package com.mcverse.jobify.application;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P1.1: apply to a job and list my applications. Each test creates its own job so tests stay independent. */
@SpringBootTest
class ApplicationEndpointsIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private int createJob(String employerToken, String title) throws Exception {
        String body = mvc.perform(post("/jobs").header(AUTHORIZATION, employerToken).contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"" + title + "\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,"
                                + "\"rateType\":\"HOURLY\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.postId");
    }

    private String registerAndLogin(String username, String role) throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"s3cur3Pass\",\"role\":\"" + role
                                + "\",\"firstName\":\"T\",\"lastName\":\"U\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    // ── apply ─────────────────────────────────────────────────────────────────

    @Test
    void seekerAppliesAndGetsAnAppliedApplicationWithJobSummary() throws Exception {
        int jobId = createJob(bearer(mvc, "techcorp"), "Apply target");
        mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, bearer(mvc, "alice_s"))
                        .contentType(APPLICATION_JSON).content("{\"coverNote\":\"  I am a great fit.  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.coverNote").value("I am a great fit."))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.job.postId").value(jobId))
                .andExpect(jsonPath("$.job.jobTitle").value("Apply target"))
                .andExpect(jsonPath("$.job.employerUsername").value("techcorp"))
                .andExpect(jsonPath("$.job.companyName").value("TechCorp Ltd"))
                .andExpect(jsonPath("$.job.available").value(true));
    }

    @Test
    void applyingWithoutABodyOrWithABlankNoteStoresNoNote() throws Exception {
        String employer = bearer(mvc, "techcorp");
        int first = createJob(employer, "No body");
        int second = createJob(employer, "Blank note");
        mvc.perform(post("/jobs/" + first + "/apply").header(AUTHORIZATION, bearer(mvc, "alice_s")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coverNote").doesNotExist());
        mvc.perform(post("/jobs/" + second + "/apply").header(AUTHORIZATION, bearer(mvc, "alice_s"))
                        .contentType(APPLICATION_JSON).content("{\"coverNote\":\"   \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coverNote").doesNotExist());
    }

    @Test
    void tooLongCoverNoteIs400() throws Exception {
        int jobId = createJob(bearer(mvc, "techcorp"), "Long note");
        mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, bearer(mvc, "alice_s"))
                        .contentType(APPLICATION_JSON).content("{\"coverNote\":\"" + "a".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("coverNote must be at most 2000 characters"));
    }

    @Test
    void applyingTwiceIs422() throws Exception {
        int jobId = createJob(bearer(mvc, "techcorp"), "Twice");
        String seeker = bearer(mvc, "alice_s");
        mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, seeker)).andExpect(status().isCreated());
        mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, seeker))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("You have already applied to this job."));
    }

    @Test
    void twoSeekersCanApplyToTheSameJob() throws Exception {
        int jobId = createJob(bearer(mvc, "techcorp"), "Popular");
        mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, bearer(mvc, "alice_s")))
                .andExpect(status().isCreated());
        mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, bearer(mvc, "bob_s")))
                .andExpect(status().isCreated());
    }

    @Test
    void closedJobRejectsApplications() throws Exception {
        String employer = bearer(mvc, "techcorp");
        int jobId = createJob(employer, "Closed");
        mvc.perform(patch("/jobs/" + jobId + "/available").header(AUTHORIZATION, employer)
                        .contentType(APPLICATION_JSON).content("{\"available\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, bearer(mvc, "alice_s")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("This job is no longer accepting applications."));
    }

    @Test
    void unknownJobIs404() throws Exception {
        mvc.perform(post("/jobs/987654/apply").header(AUTHORIZATION, bearer(mvc, "alice_s")))
                .andExpect(status().isNotFound());
    }

    @Test
    void employersAndAnonymousUsersCannotApply() throws Exception {
        int jobId = createJob(bearer(mvc, "techcorp"), "Not for employers");
        mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, bearer(mvc, "startupxyz")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only job seekers can apply for jobs."));
        mvc.perform(post("/jobs/" + jobId + "/apply")).andExpect(status().isUnauthorized());
    }

    @Test
    void withdrawnApplicationCanBeSubmittedAgainAsTheSameRecord() throws Exception {
        int jobId = createJob(bearer(mvc, "techcorp"), "Reapply");
        String seeker = bearer(mvc, "carol_s");
        String first = mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, seeker))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(first, "$.id");
        mvc.perform(delete("/applications/" + id).header(AUTHORIZATION, seeker)).andExpect(status().isNoContent());

        mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, seeker)
                        .contentType(APPLICATION_JSON).content("{\"coverNote\":\"Second try\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.coverNote").value("Second try"));
    }

    // ── list mine ─────────────────────────────────────────────────────────────

    @Test
    void myApplicationsAreNewestFirstAndPrivate() throws Exception {
        String employer = bearer(mvc, "techcorp");
        int older = createJob(employer, "Older application");
        int newer = createJob(employer, "Newer application");
        String bob = registerAndLogin("list_seeker", "SEEKER");
        mvc.perform(post("/jobs/" + older + "/apply").header(AUTHORIZATION, bob)).andExpect(status().isCreated());
        Thread.sleep(5);
        mvc.perform(post("/jobs/" + newer + "/apply").header(AUTHORIZATION, bob)).andExpect(status().isCreated());

        mvc.perform(get("/applications/me").header(AUTHORIZATION, bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].job.postId").value(newer))
                .andExpect(jsonPath("$[1].job.postId").value(older));

        mvc.perform(get("/applications/me").header(AUTHORIZATION, bearer(mvc, "bob_s")))
                .andExpect(jsonPath("$[*].job.postId", not(hasItem(older))))
                .andExpect(jsonPath("$[*].job.postId", not(hasItem(newer))));
    }

    @Test
    void newSeekerHasAnEmptyList() throws Exception {
        mvc.perform(get("/applications/me").header(AUTHORIZATION, registerAndLogin("empty_list_seeker", "SEEKER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void closedJobsStayInTheListFlaggedUnavailable() throws Exception {
        String employer = bearer(mvc, "techcorp");
        int jobId = createJob(employer, "Closes later");
        String seeker = registerAndLogin("closed_flag_seeker", "SEEKER");
        mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, seeker)).andExpect(status().isCreated());
        mvc.perform(patch("/jobs/" + jobId + "/available").header(AUTHORIZATION, employer)
                .contentType(APPLICATION_JSON).content("{\"available\":false}"));

        mvc.perform(get("/applications/me").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$[0].job.available").value(false))
                .andExpect(jsonPath("$[0].status").value("APPLIED"));
    }

    @Test
    void employersAndAnonymousUsersCannotListApplications() throws Exception {
        mvc.perform(get("/applications/me").header(AUTHORIZATION, bearer(mvc, "techcorp")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/applications/me")).andExpect(status().isUnauthorized());
    }

    // ── account deletion must not be blocked by applications ──────────────────

    private void approveDeletion(String userToken) throws Exception {
        String body = mvc.perform(post("/account/deletion-request").header(AUTHORIZATION, userToken))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        mvc.perform(post("/admin/deletion-requests/" + id + "/approve").header(AUTHORIZATION, bearer(mvc, "admin")))
                .andExpect(status().isOk());
    }

    @Test
    void deletingASeekerRemovesTheirApplications() throws Exception {
        int jobId = createJob(bearer(mvc, "techcorp"), "Seeker will be deleted");
        String seeker = registerAndLogin("deleted_seeker", "SEEKER");
        mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, seeker)).andExpect(status().isCreated());

        approveDeletion(seeker);

        assertEquals(0, jdbc.queryForObject("select count(*) from applications where job_post_id = ?",
                Integer.class, jobId));
        mvc.perform(get("/jobs/" + jobId)).andExpect(status().isOk());
    }

    @Test
    void deletingAnEmployerRemovesTheApplicationsToTheirJobs() throws Exception {
        String employer = registerAndLogin("deleted_employer", "EMPLOYER");
        int jobId = createJob(employer, "Employer will be deleted");
        String alice = bearer(mvc, "alice_s");
        mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, alice)).andExpect(status().isCreated());

        approveDeletion(employer);

        mvc.perform(get("/jobs/" + jobId)).andExpect(status().isNotFound());
        mvc.perform(get("/applications/me").header(AUTHORIZATION, alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].job.postId", not(hasItem(jobId))));
    }
}
