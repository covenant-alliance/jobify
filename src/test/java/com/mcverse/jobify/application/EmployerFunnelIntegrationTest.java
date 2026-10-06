package com.mcverse.jobify.application;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The funnel in GET /employers/me/stats, built from real applications moved through the real API. */
@SpringBootTest
class EmployerFunnelIntegrationTest {

    private static final String PASSWORD = "a-decent-passphrase";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String register(String role) throws Exception {
        String username = role.toLowerCase().charAt(0) + "f_" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD
                                + "\",\"role\":\"" + role + "\",\"firstName\":\"F\",\"lastName\":\"U\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private int createJob(String employer, String title) throws Exception {
        String body = mvc.perform(post("/jobs").header(AUTHORIZATION, employer).contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"" + title + "\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,"
                                + "\"rateType\":\"HOURLY\",\"location\":\"Berlin\",\"workMode\":\"REMOTE\",\"employmentType\":\"FULL_TIME\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.postId");
    }

    /** A new seeker applies; returns {application id, seeker token}. */
    private String[] applicant(int jobId) throws Exception {
        String seeker = register("SEEKER");
        String body = mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, seeker))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new String[] {JsonPath.read(body, "$.id"), seeker};
    }

    private void move(String employer, String applicationId, String... stages) throws Exception {
        for (String stage : stages) {
            mvc.perform(put("/applications/" + applicationId + "/status").header(AUTHORIZATION, employer)
                    .contentType(APPLICATION_JSON).content("{\"status\":\"" + stage + "\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void anEmployerWithNoApplicationsGetsAZeroFunnelAndNoMedians() throws Exception {
        String employer = register("EMPLOYER");
        createJob(employer, "Nobody yet");
        mvc.perform(get("/employers/me/stats").header(AUTHORIZATION, employer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applications.funnel.reached.APPLIED").value(0))
                .andExpect(jsonPath("$.applications.funnel.reached.IN_REVIEW").value(0))
                .andExpect(jsonPath("$.applications.funnel.reached.INTERVIEW").value(0))
                .andExpect(jsonPath("$.applications.funnel.reached.OFFER").value(0))
                .andExpect(jsonPath("$.applications.funnel.medianDaysInStage.APPLIED").doesNotExist())
                .andExpect(jsonPath("$.perJob[0].funnel.reached.APPLIED").value(0));
    }

    @Test
    void theFunnelShowsHowFarApplicantsReallyGot() throws Exception {
        String employer = register("EMPLOYER");
        int job = createJob(employer, "Funnel job");

        String[] hired = applicant(job);       // APPLIED -> IN_REVIEW -> INTERVIEW -> OFFER
        move(employer, hired[0], "IN_REVIEW", "INTERVIEW", "OFFER");
        String[] interviewedThenRejected = applicant(job);   // ... -> INTERVIEW -> REJECTED
        move(employer, interviewedThenRejected[0], "IN_REVIEW", "INTERVIEW", "REJECTED");
        String[] screenedOut = applicant(job); // APPLIED -> IN_REVIEW -> REJECTED
        move(employer, screenedOut[0], "IN_REVIEW", "REJECTED");
        String[] withdrew = applicant(job);    // APPLIED -> WITHDRAWN
        mvc.perform(delete("/applications/" + withdrew[0]).header(AUTHORIZATION, withdrew[1]))
                .andExpect(status().isNoContent());
        applicant(job);                        // still APPLIED

        mvc.perform(get("/employers/me/stats").header(AUTHORIZATION, employer))
                .andExpect(status().isOk())
                // the snapshot by current status is unchanged
                .andExpect(jsonPath("$.applications.total").value(5))
                .andExpect(jsonPath("$.applications.byStatus.REJECTED").value(2))
                .andExpect(jsonPath("$.applications.byStatus.OFFER").value(1))
                // the funnel is cumulative
                .andExpect(jsonPath("$.applications.funnel.reached.APPLIED").value(5))
                .andExpect(jsonPath("$.applications.funnel.reached.IN_REVIEW").value(3))
                .andExpect(jsonPath("$.applications.funnel.reached.INTERVIEW").value(2))
                .andExpect(jsonPath("$.applications.funnel.reached.OFFER").value(1))
                // stays that ended exist for the first three stages, not for OFFER (still there)
                .andExpect(jsonPath("$.applications.funnel.medianDaysInStage.APPLIED").isNumber())
                .andExpect(jsonPath("$.applications.funnel.medianDaysInStage.IN_REVIEW").isNumber())
                .andExpect(jsonPath("$.applications.funnel.medianDaysInStage.INTERVIEW").isNumber())
                .andExpect(jsonPath("$.applications.funnel.medianDaysInStage.OFFER").doesNotExist())
                // one job, so the per-job funnel equals the overall one
                .andExpect(jsonPath("$.perJob[0].funnel.reached.APPLIED").value(5))
                .andExpect(jsonPath("$.perJob[0].funnel.reached.INTERVIEW").value(2));
    }

    @Test
    void eachJobHasItsOwnFunnelAndTheOverallOneAddsThemUp() throws Exception {
        String employer = register("EMPLOYER");
        int busy = createJob(employer, "Busy");
        int quiet = createJob(employer, "Quiet");
        move(employer, applicant(busy)[0], "IN_REVIEW", "INTERVIEW");
        move(employer, applicant(busy)[0], "IN_REVIEW");
        applicant(quiet);

        String body = mvc.perform(get("/employers/me/stats").header(AUTHORIZATION, employer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applications.funnel.reached.APPLIED").value(3))
                .andExpect(jsonPath("$.applications.funnel.reached.IN_REVIEW").value(2))
                .andExpect(jsonPath("$.applications.funnel.reached.INTERVIEW").value(1))
                .andReturn().getResponse().getContentAsString();
        // perJob is newest job first: the quiet one, then the busy one
        org.junit.jupiter.api.Assertions.assertEquals(quiet, (int) JsonPath.read(body, "$.perJob[0].postId"));
        org.junit.jupiter.api.Assertions.assertEquals(1, (int) JsonPath.read(body, "$.perJob[0].funnel.reached.APPLIED"));
        org.junit.jupiter.api.Assertions.assertEquals(0, (int) JsonPath.read(body, "$.perJob[0].funnel.reached.IN_REVIEW"));
        org.junit.jupiter.api.Assertions.assertEquals(2, (int) JsonPath.read(body, "$.perJob[1].funnel.reached.APPLIED"));
        org.junit.jupiter.api.Assertions.assertEquals(1, (int) JsonPath.read(body, "$.perJob[1].funnel.reached.INTERVIEW"));
    }

    @Test
    void anEmployerNeverSeesAnotherEmployersApplicationsInTheFunnel() throws Exception {
        String mine = register("EMPLOYER");
        String other = register("EMPLOYER");
        createJob(mine, "Mine");
        int theirs = createJob(other, "Theirs");
        move(other, applicant(theirs)[0], "IN_REVIEW", "INTERVIEW", "OFFER");

        mvc.perform(get("/employers/me/stats").header(AUTHORIZATION, mine))
                .andExpect(jsonPath("$.applications.total").value(0))
                .andExpect(jsonPath("$.applications.funnel.reached.APPLIED").value(0))
                .andExpect(jsonPath("$.applications.funnel.reached.OFFER").value(0));
        mvc.perform(get("/employers/me/stats").header(AUTHORIZATION, other))
                .andExpect(jsonPath("$.applications.funnel.reached.OFFER").value(1));
    }

    @Test
    void aReapplicationIsCountedOnceByItsLatestAttempt() throws Exception {
        String employer = register("EMPLOYER");
        int job = createJob(employer, "Second chance");
        String[] applicant = applicant(job);
        move(employer, applicant[0], "IN_REVIEW", "INTERVIEW");
        mvc.perform(delete("/applications/" + applicant[0]).header(AUTHORIZATION, applicant[1]))
                .andExpect(status().isNoContent());
        mvc.perform(post("/jobs/" + job + "/apply").header(AUTHORIZATION, applicant[1])).andExpect(status().isCreated());

        mvc.perform(get("/employers/me/stats").header(AUTHORIZATION, employer))
                .andExpect(jsonPath("$.applications.total").value(1))
                .andExpect(jsonPath("$.applications.funnel.reached.APPLIED").value(1))
                .andExpect(jsonPath("$.applications.funnel.reached.INTERVIEW").value(0));
    }

    @Test
    void theStatsStillRefuseSeekersAndAnonymousCallers() throws Exception {
        mvc.perform(get("/employers/me/stats").header(AUTHORIZATION, register("SEEKER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/employers/me/stats")).andExpect(status().isUnauthorized());
    }
}
