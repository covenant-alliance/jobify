package com.mcverse.jobify.application;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P8: GET /employers/me/stats. Each test registers its own employer so every number is exact. */
@SpringBootTest
class EmployerStatsIntegrationTest {

    private static final String PASSWORD = "a-decent-passphrase";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String newEmployer(String username) throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD
                                + "\",\"role\":\"EMPLOYER\",\"firstName\":\"E\",\"lastName\":\"S\"}"))
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

    private String apply(String seekerUsername, int jobId) throws Exception {
        String body = mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, bearer(mvc, seekerUsername)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void move(String employer, String applicationId, String status) throws Exception {
        mvc.perform(put("/applications/" + applicationId + "/status").header(AUTHORIZATION, employer)
                .contentType(APPLICATION_JSON).content("{\"status\":\"" + status + "\"}")).andExpect(status().isOk());
    }

    @Test
    void anEmployerWithNoJobsGetsZerosAndAFullEmptySeries() throws Exception {
        mvc.perform(get("/employers/me/stats").header(AUTHORIZATION, newEmployer("es_empty")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs.total").value(0))
                .andExpect(jsonPath("$.jobs.open").value(0))
                .andExpect(jsonPath("$.jobs.closed").value(0))
                .andExpect(jsonPath("$.applications.total").value(0))
                .andExpect(jsonPath("$.applications.active").value(0))
                .andExpect(jsonPath("$.applications.byStatus.APPLIED").value(0))
                .andExpect(jsonPath("$.applications.byStatus.WITHDRAWN").value(0))
                .andExpect(jsonPath("$.applications.weekly.length()").value(12))
                .andExpect(jsonPath("$.perJob.length()").value(0));
    }

    @Test
    void jobsWithoutApplicantsStillAppearWithZeroCounts() throws Exception {
        String employer = newEmployer("es_quiet");
        int open = createJob(employer, "Quiet open");
        int closed = createJob(employer, "Quiet closed");
        mvc.perform(patch("/jobs/" + closed + "/available").header(AUTHORIZATION, employer)
                .contentType(APPLICATION_JSON).content("{\"available\":false}")).andExpect(status().isOk());

        mvc.perform(get("/employers/me/stats").header(AUTHORIZATION, employer))
                .andExpect(jsonPath("$.jobs.total").value(2))
                .andExpect(jsonPath("$.jobs.open").value(1))
                .andExpect(jsonPath("$.jobs.closed").value(1))
                .andExpect(jsonPath("$.applications.total").value(0))
                .andExpect(jsonPath("$.perJob.length()").value(2))
                .andExpect(jsonPath("$.perJob[0].postId").value(closed)) // newest job first
                .andExpect(jsonPath("$.perJob[0].available").value(false))
                .andExpect(jsonPath("$.perJob[0].total").value(0))
                .andExpect(jsonPath("$.perJob[1].postId").value(open))
                .andExpect(jsonPath("$.perJob[1].jobTitle").value("Quiet open"));
    }

    @Test
    void countsPerStatusAndPerJobFollowTheApplications() throws Exception {
        String employer = newEmployer("es_busy");
        int first = createJob(employer, "Busy one");
        int second = createJob(employer, "Busy two");

        String aliceFirst = apply("alice_s", first);
        String bobFirst = apply("bob_s", first);
        apply("carol_s", first);
        String aliceSecond = apply("alice_s", second);
        move(employer, aliceFirst, "IN_REVIEW");
        move(employer, aliceFirst, "INTERVIEW");
        move(employer, bobFirst, "REJECTED");
        mvc.perform(delete("/applications/" + aliceSecond).header(AUTHORIZATION, bearer(mvc, "alice_s")))
                .andExpect(status().isNoContent());

        mvc.perform(get("/employers/me/stats").header(AUTHORIZATION, employer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applications.total").value(4))
                .andExpect(jsonPath("$.applications.active").value(2)) // carol applied, alice interview
                .andExpect(jsonPath("$.applications.byStatus.APPLIED").value(1))
                .andExpect(jsonPath("$.applications.byStatus.INTERVIEW").value(1))
                .andExpect(jsonPath("$.applications.byStatus.REJECTED").value(1))
                .andExpect(jsonPath("$.applications.byStatus.WITHDRAWN").value(1))
                .andExpect(jsonPath("$.applications.byStatus.IN_REVIEW").value(0))
                .andExpect(jsonPath("$.perJob[?(@.postId == " + first + ")].total").value(3))
                .andExpect(jsonPath("$.perJob[?(@.postId == " + first + ")].active").value(2))
                .andExpect(jsonPath("$.perJob[?(@.postId == " + first + ")].byStatus.REJECTED").value(1))
                .andExpect(jsonPath("$.perJob[?(@.postId == " + second + ")].total").value(1))
                .andExpect(jsonPath("$.perJob[?(@.postId == " + second + ")].active").value(0))
                .andExpect(jsonPath("$.perJob[?(@.postId == " + second + ")].byStatus.WITHDRAWN").value(1));
    }

    @Test
    void thisWeeksApplicationsLandInTheLastEntryOfTheSeries() throws Exception {
        String employer = newEmployer("es_weekly");
        int job = createJob(employer, "Weekly employer");
        apply("alice_s", job);
        apply("bob_s", job);
        String thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();

        mvc.perform(get("/employers/me/stats").header(AUTHORIZATION, employer))
                .andExpect(jsonPath("$.applications.weekly.length()").value(12))
                .andExpect(jsonPath("$.applications.weekly[11].weekStart").value(thisMonday))
                .andExpect(jsonPath("$.applications.weekly[11].count").value(2))
                .andExpect(jsonPath("$.applications.weekly[10].count").value(0));
    }

    @Test
    void anEmployerNeverSeesAnotherEmployersNumbers() throws Exception {
        String mine = newEmployer("es_mine");
        String theirs = newEmployer("es_theirs");
        int theirJob = createJob(theirs, "Their private job");
        apply("alice_s", theirJob);
        createJob(mine, "My only job");

        mvc.perform(get("/employers/me/stats").header(AUTHORIZATION, mine))
                .andExpect(jsonPath("$.jobs.total").value(1))
                .andExpect(jsonPath("$.applications.total").value(0))
                .andExpect(jsonPath("$.perJob.length()").value(1))
                .andExpect(jsonPath("$.perJob[0].jobTitle").value("My only job"));
        mvc.perform(get("/employers/me/stats").header(AUTHORIZATION, theirs))
                .andExpect(jsonPath("$.applications.total").value(1));
    }

    @Test
    void seekersAdminsAndAnonymousUsersAreRefused() throws Exception {
        mvc.perform(get("/employers/me/stats").header(AUTHORIZATION, bearer(mvc, "alice_s")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only employers have job statistics."));
        mvc.perform(get("/employers/me/stats").header(AUTHORIZATION, bearer(mvc, "admin")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/employers/me/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void theSeededEmployerSeesTheirSeededJobs() throws Exception {
        mvc.perform(get("/employers/me/stats").header(AUTHORIZATION, bearer(mvc, "financegroup")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs.total").isNumber())
                .andExpect(jsonPath("$.perJob[?(@.jobTitle == 'Senior Data Scientist')]").isNotEmpty());
    }
}
