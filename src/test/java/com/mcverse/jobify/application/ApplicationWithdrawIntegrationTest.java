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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P1.2: a seeker withdraws their own application. */
@SpringBootTest
class ApplicationWithdrawIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** Creates a job as techcorp and has the given seeker apply; returns the application id. */
    private String applyToNewJob(String seekerToken, String title) throws Exception {
        String job = mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"" + title + "\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,"
                                + "\"rateType\":\"HOURLY\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int jobId = JsonPath.read(job, "$.postId");
        String application = mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, seekerToken))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(application, "$.id");
    }

    private void forceStatus(String applicationId, String status) {
        jdbc.update("update applications set status = ? where id = ?", status, applicationId);
    }

    @Test
    void applicantWithdrawsAndTheRecordIsKeptAsWithdrawn() throws Exception {
        String alice = bearer(mvc, "alice_s");
        String id = applyToNewJob(alice, "Withdraw me");

        mvc.perform(delete("/applications/" + id).header(AUTHORIZATION, alice))
                .andExpect(status().isNoContent());

        mvc.perform(get("/applications/me").header(AUTHORIZATION, alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].status").value("WITHDRAWN"));
    }

    @Test
    void withdrawingChangesUpdatedAt() throws Exception {
        String alice = bearer(mvc, "alice_s");
        String id = applyToNewJob(alice, "Timestamp check");
        String before = jdbc.queryForObject("select updated_at from applications where id = ?", String.class, id);
        Thread.sleep(5);

        mvc.perform(delete("/applications/" + id).header(AUTHORIZATION, alice)).andExpect(status().isNoContent());

        String after = jdbc.queryForObject("select updated_at from applications where id = ?", String.class, id);
        assertNotEquals(before, after);
    }

    @Test
    void withdrawingIsAllowedFromEveryOpenStage() throws Exception {
        String alice = bearer(mvc, "alice_s");
        for (String stage : new String[] {"IN_REVIEW", "INTERVIEW", "OFFER"}) {
            String id = applyToNewJob(alice, "Stage " + stage);
            forceStatus(id, stage);
            mvc.perform(delete("/applications/" + id).header(AUTHORIZATION, alice))
                    .andExpect(status().isNoContent());
            assertEquals("WITHDRAWN",
                    jdbc.queryForObject("select status from applications where id = ?", String.class, id));
        }
    }

    @Test
    void withdrawingTwiceIs422() throws Exception {
        String alice = bearer(mvc, "alice_s");
        String id = applyToNewJob(alice, "Twice");
        mvc.perform(delete("/applications/" + id).header(AUTHORIZATION, alice)).andExpect(status().isNoContent());
        mvc.perform(delete("/applications/" + id).header(AUTHORIZATION, alice))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("This application can no longer be withdrawn."));
    }

    @Test
    void aRejectedApplicationCannotBeWithdrawn() throws Exception {
        String alice = bearer(mvc, "alice_s");
        String id = applyToNewJob(alice, "Rejected");
        forceStatus(id, "REJECTED");
        mvc.perform(delete("/applications/" + id).header(AUTHORIZATION, alice))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("This application can no longer be withdrawn."));
        assertEquals("REJECTED",
                jdbc.queryForObject("select status from applications where id = ?", String.class, id));
    }

    @Test
    void anotherSeekerCannotWithdrawItAndNothingChanges() throws Exception {
        String id = applyToNewJob(bearer(mvc, "alice_s"), "Not yours");
        mvc.perform(delete("/applications/" + id).header(AUTHORIZATION, bearer(mvc, "bob_s")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only withdraw your own applications."));
        assertEquals("APPLIED",
                jdbc.queryForObject("select status from applications where id = ?", String.class, id));
    }

    @Test
    void employersAndAnonymousUsersCannotWithdraw() throws Exception {
        String id = applyToNewJob(bearer(mvc, "alice_s"), "Employer attempt");
        mvc.perform(delete("/applications/" + id).header(AUTHORIZATION, bearer(mvc, "techcorp")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/applications/" + id)).andExpect(status().isUnauthorized());
        assertEquals("APPLIED",
                jdbc.queryForObject("select status from applications where id = ?", String.class, id));
    }

    @Test
    void unknownApplicationIs404() throws Exception {
        mvc.perform(delete("/applications/does-not-exist").header(AUTHORIZATION, bearer(mvc, "alice_s")))
                .andExpect(status().isNotFound());
    }

    @Test
    void afterWithdrawingTheSeekerCanApplyAgain() throws Exception {
        String alice = bearer(mvc, "alice_s");
        String job = mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Again\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,"
                                + "\"rateType\":\"HOURLY\"}"))
                .andReturn().getResponse().getContentAsString();
        int jobId = JsonPath.read(job, "$.postId");
        String first = mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, alice))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(first, "$.id");
        mvc.perform(delete("/applications/" + id).header(AUTHORIZATION, alice)).andExpect(status().isNoContent());

        mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, alice))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("APPLIED"));
    }
}
