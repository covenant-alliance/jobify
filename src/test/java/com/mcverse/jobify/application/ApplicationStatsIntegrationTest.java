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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P16: GET /applications/me/stats. Each test registers its own seeker so counts are exact. */
@SpringBootTest
class ApplicationStatsIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String newSeeker(String username) throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"a-decent-passphrase\","
                                + "\"role\":\"SEEKER\",\"firstName\":\"S\",\"lastName\":\"T\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    /** Creates a job as techcorp, applies as the seeker, returns the application id. */
    private String applyToNewJob(String seeker, String title) throws Exception {
        String job = mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"" + title + "\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,"
                                + "\"rateType\":\"HOURLY\"}"))
                .andReturn().getResponse().getContentAsString();
        int jobId = JsonPath.read(job, "$.postId");
        String application = mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, seeker))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(application, "$.id");
    }

    @Test
    void aNewSeekerGetsAllZerosWithEveryKeyPresent() throws Exception {
        mvc.perform(get("/applications/me/stats").header(AUTHORIZATION, newSeeker("stats_empty")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.active").value(0))
                .andExpect(jsonPath("$.byStatus.APPLIED").value(0))
                .andExpect(jsonPath("$.byStatus.IN_REVIEW").value(0))
                .andExpect(jsonPath("$.byStatus.INTERVIEW").value(0))
                .andExpect(jsonPath("$.byStatus.OFFER").value(0))
                .andExpect(jsonPath("$.byStatus.REJECTED").value(0))
                .andExpect(jsonPath("$.byStatus.WITHDRAWN").value(0))
                .andExpect(jsonPath("$.weekly.length()").value(12))
                .andExpect(jsonPath("$.weekly[11].count").value(0));
    }

    @Test
    void countsFollowTheApplicationsThroughTheirLifecycle() throws Exception {
        String seeker = newSeeker("stats_lifecycle");
        String first = applyToNewJob(seeker, "Stats job one");
        String second = applyToNewJob(seeker, "Stats job two");
        applyToNewJob(seeker, "Stats job three");

        mvc.perform(put("/applications/" + first + "/status").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON).content("{\"status\":\"IN_REVIEW\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/applications/" + second).header(AUTHORIZATION, seeker))
                .andExpect(status().isNoContent());

        mvc.perform(get("/applications/me/stats").header(AUTHORIZATION, seeker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.active").value(2))
                .andExpect(jsonPath("$.byStatus.APPLIED").value(1))
                .andExpect(jsonPath("$.byStatus.IN_REVIEW").value(1))
                .andExpect(jsonPath("$.byStatus.WITHDRAWN").value(1));
    }

    @Test
    void thisWeeksApplicationsLandInTheLastEntryOfTheSeries() throws Exception {
        String seeker = newSeeker("stats_weekly");
        applyToNewJob(seeker, "Weekly one");
        applyToNewJob(seeker, "Weekly two");
        String thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();

        mvc.perform(get("/applications/me/stats").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$.weekly.length()").value(12))
                .andExpect(jsonPath("$.weekly[11].weekStart").value(thisMonday))
                .andExpect(jsonPath("$.weekly[11].count").value(2))
                .andExpect(jsonPath("$.weekly[10].count").value(0));
    }

    @Test
    void seekersOnlySeeTheirOwnNumbers() throws Exception {
        String busy = newSeeker("stats_busy");
        applyToNewJob(busy, "Busy one");
        String other = newSeeker("stats_other");

        mvc.perform(get("/applications/me/stats").header(AUTHORIZATION, other))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void employersAndAnonymousUsersAreRefused() throws Exception {
        mvc.perform(get("/applications/me/stats").header(AUTHORIZATION, bearer(mvc, "techcorp")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only job seekers have applications."));
        mvc.perform(get("/applications/me/stats")).andExpect(status().isForbidden());
    }

    @Test
    void theStatsRouteDoesNotShadowTheApplicationList() throws Exception {
        String seeker = newSeeker("stats_routes");
        applyToNewJob(seeker, "Routes job");
        mvc.perform(get("/applications/me").header(AUTHORIZATION, seeker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
