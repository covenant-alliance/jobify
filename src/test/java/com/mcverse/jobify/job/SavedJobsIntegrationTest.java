package com.mcverse.jobify.job;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** P2: saved jobs. Each test makes its own jobs and uses its own seeker where order matters. */
@SpringBootTest
class SavedJobsIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private int createJob(String employerUsername, String title) throws Exception {
        String body = mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, employerUsername))
                        .contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"" + title + "\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,"
                                + "\"rateType\":\"HOURLY\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.postId");
    }

    private String newSeeker(String username) throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"s3cur3Pass\",\"role\":\"SEEKER\","
                                + "\"firstName\":\"S\",\"lastName\":\"K\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    @Test
    void savingAddsTheJobToTheListWithTheFullJobShape() throws Exception {
        int jobId = createJob("techcorp", "Save me");
        String seeker = newSeeker("saver_one");

        mvc.perform(put("/jobs/" + jobId + "/save").header(AUTHORIZATION, seeker)).andExpect(status().isNoContent());

        mvc.perform(get("/jobs/saved").header(AUTHORIZATION, seeker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].postId").value(jobId))
                .andExpect(jsonPath("$[0].jobTitle").value("Save me"))
                .andExpect(jsonPath("$[0].employerUsername").value("techcorp"))
                .andExpect(jsonPath("$[0].companyName").value("TechCorp Ltd"))
                .andExpect(jsonPath("$[0].rate").value(50.0))
                .andExpect(jsonPath("$[0].available").value(true));
    }

    @Test
    void savingTwiceKeepsASingleEntry() throws Exception {
        int jobId = createJob("techcorp", "Idempotent save");
        String seeker = newSeeker("saver_two");
        mvc.perform(put("/jobs/" + jobId + "/save").header(AUTHORIZATION, seeker)).andExpect(status().isNoContent());
        mvc.perform(put("/jobs/" + jobId + "/save").header(AUTHORIZATION, seeker)).andExpect(status().isNoContent());

        mvc.perform(get("/jobs/saved").header(AUTHORIZATION, seeker)).andExpect(jsonPath("$.length()").value(1));
        assertEquals(1, jdbc.queryForObject("select count(*) from saved_jobs where job_post_id = ?",
                Integer.class, jobId));
    }

    @Test
    void mostRecentlySavedComesFirst() throws Exception {
        int first = createJob("techcorp", "Saved first");
        int second = createJob("startupxyz", "Saved second");
        String seeker = newSeeker("saver_three");
        mvc.perform(put("/jobs/" + first + "/save").header(AUTHORIZATION, seeker)).andExpect(status().isNoContent());
        Thread.sleep(5);
        mvc.perform(put("/jobs/" + second + "/save").header(AUTHORIZATION, seeker)).andExpect(status().isNoContent());

        mvc.perform(get("/jobs/saved").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$[0].postId").value(second))
                .andExpect(jsonPath("$[1].postId").value(first));
    }

    @Test
    void removingTakesItOutAndRemovingAgainIsFine() throws Exception {
        int jobId = createJob("techcorp", "Remove me");
        String seeker = newSeeker("saver_four");
        mvc.perform(put("/jobs/" + jobId + "/save").header(AUTHORIZATION, seeker)).andExpect(status().isNoContent());

        mvc.perform(delete("/jobs/" + jobId + "/save").header(AUTHORIZATION, seeker)).andExpect(status().isNoContent());
        mvc.perform(delete("/jobs/" + jobId + "/save").header(AUTHORIZATION, seeker)).andExpect(status().isNoContent());

        mvc.perform(get("/jobs/saved").header(AUTHORIZATION, seeker)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void closedJobsStayInTheListFlaggedUnavailable() throws Exception {
        int jobId = createJob("techcorp", "Closes later");
        String seeker = newSeeker("saver_five");
        mvc.perform(put("/jobs/" + jobId + "/save").header(AUTHORIZATION, seeker)).andExpect(status().isNoContent());
        mvc.perform(patch("/jobs/" + jobId + "/available").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                .contentType(APPLICATION_JSON).content("{\"available\":false}")).andExpect(status().isOk());

        mvc.perform(get("/jobs/saved").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$[0].postId").value(jobId))
                .andExpect(jsonPath("$[0].available").value(false));
    }

    @Test
    void savedListsArePerSeeker() throws Exception {
        int jobId = createJob("techcorp", "Private bookmark");
        mvc.perform(put("/jobs/" + jobId + "/save").header(AUTHORIZATION, bearer(mvc, "alice_s")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/jobs/saved").header(AUTHORIZATION, bearer(mvc, "alice_s")))
                .andExpect(jsonPath("$[*].postId", hasItem(jobId)));
        mvc.perform(get("/jobs/saved").header(AUTHORIZATION, bearer(mvc, "bob_s")))
                .andExpect(jsonPath("$[*].postId", not(hasItem(jobId))));
    }

    @Test
    void unknownJobIs404ForSaveAndRemove() throws Exception {
        String seeker = bearer(mvc, "alice_s");
        mvc.perform(put("/jobs/987654/save").header(AUTHORIZATION, seeker)).andExpect(status().isNotFound());
        mvc.perform(delete("/jobs/987654/save").header(AUTHORIZATION, seeker)).andExpect(status().isNotFound());
    }

    @Test
    void employersAndAnonymousUsersCannotUseSavedJobs() throws Exception {
        int jobId = createJob("techcorp", "Seekers only");
        String employer = bearer(mvc, "startupxyz");
        mvc.perform(put("/jobs/" + jobId + "/save").header(AUTHORIZATION, employer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only job seekers can save jobs."));
        mvc.perform(delete("/jobs/" + jobId + "/save").header(AUTHORIZATION, employer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/jobs/saved").header(AUTHORIZATION, employer)).andExpect(status().isForbidden());

        mvc.perform(put("/jobs/" + jobId + "/save")).andExpect(status().isForbidden());
        mvc.perform(get("/jobs/saved")).andExpect(status().isForbidden());
    }

    @Test
    void theSavedRouteDoesNotShadowOrLeakThePublicJobRoutes() throws Exception {
        mvc.perform(get("/jobs")).andExpect(status().isOk());
        mvc.perform(get("/jobs/saved")).andExpect(status().isForbidden()); // not the public GET /jobs/{id}
    }

    // ── account deletion must not be blocked by bookmarks ─────────────────────

    private void approveDeletion(String userToken) throws Exception {
        String body = mvc.perform(post("/account/deletion-request").header(AUTHORIZATION, userToken))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        mvc.perform(post("/admin/deletion-requests/" + id + "/approve").header(AUTHORIZATION, bearer(mvc, "admin")))
                .andExpect(status().isOk());
    }

    @Test
    void deletingASeekerRemovesTheirSavedJobs() throws Exception {
        int jobId = createJob("techcorp", "Bookmarked by a deleted seeker");
        String seeker = newSeeker("saver_deleted");
        mvc.perform(put("/jobs/" + jobId + "/save").header(AUTHORIZATION, seeker)).andExpect(status().isNoContent());

        approveDeletion(seeker);

        assertEquals(0, jdbc.queryForObject("select count(*) from saved_jobs where job_post_id = ?",
                Integer.class, jobId));
        mvc.perform(get("/jobs/" + jobId)).andExpect(status().isOk());
    }

    @Test
    void deletingAnEmployerRemovesBookmarksOfTheirJobs() throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"saved_employer_gone\",\"password\":\"s3cur3Pass\","
                                + "\"role\":\"EMPLOYER\",\"firstName\":\"E\",\"lastName\":\"G\"}"))
                .andReturn().getResponse().getContentAsString();
        String employer = "Bearer " + JsonPath.read(body, "$.token");
        String created = mvc.perform(post("/jobs").header(AUTHORIZATION, employer).contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Will vanish\",\"jobDescription\":\"<p>x</p>\",\"rate\":20,"
                                + "\"rateType\":\"HOURLY\"}"))
                .andReturn().getResponse().getContentAsString();
        int jobId = JsonPath.read(created, "$.postId");
        String alice = bearer(mvc, "alice_s");
        mvc.perform(put("/jobs/" + jobId + "/save").header(AUTHORIZATION, alice)).andExpect(status().isNoContent());

        approveDeletion(employer);

        mvc.perform(get("/jobs/saved").header(AUTHORIZATION, alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].postId", not(hasItem(jobId))));
    }
}
