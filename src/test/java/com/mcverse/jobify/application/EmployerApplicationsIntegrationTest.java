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

import java.util.List;
import java.util.Map;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P1.3 (employer lists applicants) and P1.4 (employer moves an application through the pipeline). */
@SpringBootTest
class EmployerApplicationsIntegrationTest {

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
                                + "\"rateType\":\"HOURLY\",\"location\":\"Berlin\",\"workMode\":\"REMOTE\",\"employmentType\":\"FULL_TIME\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.postId");
    }

    private String apply(String seekerUsername, int jobId, String note) throws Exception {
        String body = mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, bearer(mvc, seekerUsername))
                        .contentType(APPLICATION_JSON)
                        .content(note == null ? "{}" : "{\"coverNote\":\"" + note + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void force(String applicationId, String status) {
        jdbc.update("update applications set status = ? where id = ?", status, applicationId);
    }

    private String statusOf(String applicationId) {
        return jdbc.queryForObject("select status from applications where id = ?", String.class, applicationId);
    }

    private String move(String employer, String applicationId, String target, int expectedStatus) throws Exception {
        return mvc.perform(put("/applications/" + applicationId + "/status").header(AUTHORIZATION, bearer(mvc, employer))
                        .contentType(APPLICATION_JSON).content("{\"status\":\"" + target + "\"}"))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    // ── P1.3: list applicants ─────────────────────────────────────────────────

    @Test
    void ownerSeesApplicantsNewestFirstWithAnApplicantSummary() throws Exception {
        int jobId = createJob("techcorp", "Two applicants");
        String first = apply("alice_s", jobId, "First");
        Thread.sleep(5);
        String second = apply("bob_s", jobId, null);

        mvc.perform(get("/jobs/" + jobId + "/applications").header(AUTHORIZATION, bearer(mvc, "techcorp")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[1].id").value(first))
                .andExpect(jsonPath("$[1].status").value("APPLIED"))
                .andExpect(jsonPath("$[1].coverNote").value("First"))
                .andExpect(jsonPath("$[1].applicant.username").value("alice_s"))
                .andExpect(jsonPath("$[1].applicant.name").value("Alice"))
                .andExpect(jsonPath("$[1].applicant.lastName").value("Johnson"))
                .andExpect(jsonPath("$[1].applicant.seekerId").isNotEmpty())
                .andExpect(jsonPath("$[1].applicant.hasCv").value(false));
    }

    @Test
    void jobWithoutApplicantsReturnsAnEmptyList() throws Exception {
        int jobId = createJob("techcorp", "Nobody yet");
        mvc.perform(get("/jobs/" + jobId + "/applications").header(AUTHORIZATION, bearer(mvc, "techcorp")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void withdrawnApplicationsAreListedWithTheirStatus() throws Exception {
        int jobId = createJob("techcorp", "Withdrawn listed");
        String id = apply("alice_s", jobId, null);
        mvc.perform(delete("/applications/" + id).header(AUTHORIZATION, bearer(mvc, "alice_s")))
                .andExpect(status().isNoContent());

        mvc.perform(get("/jobs/" + jobId + "/applications").header(AUTHORIZATION, bearer(mvc, "techcorp")))
                .andExpect(jsonPath("$[0].status").value("WITHDRAWN"));
    }

    @Test
    void statusFilterNarrowsTheList() throws Exception {
        int jobId = createJob("techcorp", "Filtered");
        String reviewed = apply("alice_s", jobId, null);
        apply("bob_s", jobId, null);
        force(reviewed, "IN_REVIEW");

        mvc.perform(get("/jobs/" + jobId + "/applications?status=IN_REVIEW")
                        .header(AUTHORIZATION, bearer(mvc, "techcorp")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(reviewed));
        mvc.perform(get("/jobs/" + jobId + "/applications?status=OFFER")
                        .header(AUTHORIZATION, bearer(mvc, "techcorp")))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unknownStatusFilterIs400WithTheAllowedValues() throws Exception {
        int jobId = createJob("techcorp", "Bad filter");
        mvc.perform(get("/jobs/" + jobId + "/applications?status=MAYBE")
                        .header(AUTHORIZATION, bearer(mvc, "techcorp")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("status has an invalid value")))
                .andExpect(jsonPath("$.message", containsString("INTERVIEW")));
    }

    @Test
    void onlyTheOwningEmployerMayListApplicants() throws Exception {
        int jobId = createJob("techcorp", "Private list");
        apply("alice_s", jobId, null);
        mvc.perform(get("/jobs/" + jobId + "/applications").header(AUTHORIZATION, bearer(mvc, "startupxyz")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only view applications for your own jobs."));
        mvc.perform(get("/jobs/" + jobId + "/applications").header(AUTHORIZATION, bearer(mvc, "alice_s")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/jobs/" + jobId + "/applications")).andExpect(status().isUnauthorized());
    }

    @Test
    void listingApplicantsOfAnUnknownJobIs404() throws Exception {
        mvc.perform(get("/jobs/987654/applications").header(AUTHORIZATION, bearer(mvc, "techcorp")))
                .andExpect(status().isNotFound());
    }

    // ── P1.4: stage changes ───────────────────────────────────────────────────

    @Test
    void ownerMovesAnApplicationThroughTheWholePipeline() throws Exception {
        int jobId = createJob("techcorp", "Full pipeline");
        String id = apply("alice_s", jobId, null);

        for (String stage : List.of("IN_REVIEW", "INTERVIEW", "OFFER", "REJECTED")) {
            String body = move("techcorp", id, stage, 200);
            assertEquals(stage, JsonPath.read(body, "$.status"));
            assertEquals("alice_s", JsonPath.read(body, "$.applicant.username"));
            assertEquals(stage, statusOf(id));
        }
    }

    @Test
    void theSeekerSeesTheNewStageInTheirList() throws Exception {
        int jobId = createJob("techcorp", "Seeker sees stage");
        String id = apply("carol_s", jobId, null);
        move("techcorp", id, "IN_REVIEW", 200);

        mvc.perform(get("/applications/me").header(AUTHORIZATION, bearer(mvc, "carol_s")))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].status").value("IN_REVIEW"));
    }

    @Test
    void updatedAtChangesOnEveryTransition() throws Exception {
        int jobId = createJob("techcorp", "Timestamps");
        String id = apply("alice_s", jobId, null);
        String before = jdbc.queryForObject("select updated_at from applications where id = ?", String.class, id);
        Thread.sleep(5);
        move("techcorp", id, "IN_REVIEW", 200);
        String after = jdbc.queryForObject("select updated_at from applications where id = ?", String.class, id);
        assertNotEquals(before, after);
    }

    @Test
    void everyDisallowedMoveIs422WithAReadableMessage() throws Exception {
        Map<String, List<String>> disallowed = Map.of(
                "APPLIED", List.of("INTERVIEW", "OFFER", "APPLIED"),
                "IN_REVIEW", List.of("APPLIED", "OFFER", "IN_REVIEW"),
                "INTERVIEW", List.of("APPLIED", "IN_REVIEW", "INTERVIEW"),
                "OFFER", List.of("APPLIED", "IN_REVIEW", "INTERVIEW", "OFFER"));
        int jobId = createJob("techcorp", "Disallowed moves");
        String id = apply("alice_s", jobId, null);

        for (var entry : disallowed.entrySet()) {
            force(id, entry.getKey());
            for (String target : entry.getValue()) {
                String body = move("techcorp", id, target, 422);
                assertEquals("An application in " + entry.getKey() + " cannot move to " + target + ".",
                        JsonPath.read(body, "$.message"));
                assertEquals(entry.getKey(), statusOf(id));
            }
        }
    }

    @Test
    void rejectedAndWithdrawnApplicationsAreFinal() throws Exception {
        int jobId = createJob("techcorp", "Final states");
        String id = apply("alice_s", jobId, null);
        for (String finalState : List.of("REJECTED", "WITHDRAWN")) {
            force(id, finalState);
            String body = move("techcorp", id, "IN_REVIEW", 422);
            assertEquals("This application is already " + finalState + " and can no longer change.",
                    JsonPath.read(body, "$.message"));
        }
    }

    @Test
    void anEmployerCannotWithdrawOnTheApplicantsBehalf() throws Exception {
        int jobId = createJob("techcorp", "No employer withdraw");
        String id = apply("alice_s", jobId, null);
        String body = move("techcorp", id, "WITHDRAWN", 422);
        assertEquals("Only the applicant can withdraw an application.", JsonPath.read(body, "$.message"));
        assertEquals("APPLIED", statusOf(id));
    }

    @Test
    void onlyTheOwningEmployerMayChangeTheStage() throws Exception {
        int jobId = createJob("techcorp", "Owner only");
        String id = apply("alice_s", jobId, null);

        String body = move("startupxyz", id, "IN_REVIEW", 403);
        assertEquals("You can only manage applications for your own jobs.", JsonPath.read(body, "$.message"));
        move("alice_s", id, "IN_REVIEW", 403); // the applicant cannot promote themselves
        mvc.perform(put("/applications/" + id + "/status").contentType(APPLICATION_JSON)
                        .content("{\"status\":\"IN_REVIEW\"}"))
                .andExpect(status().isUnauthorized());
        assertEquals("APPLIED", statusOf(id));
    }

    @Test
    void unknownApplicationIs404() throws Exception {
        move("techcorp", "does-not-exist", "IN_REVIEW", 404);
    }

    @Test
    void missingOrUnknownStatusIs400() throws Exception {
        int jobId = createJob("techcorp", "Bad body");
        String id = apply("alice_s", jobId, null);
        String employer = bearer(mvc, "techcorp");

        mvc.perform(put("/applications/" + id + "/status").header(AUTHORIZATION, employer)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("status is required"));
        mvc.perform(put("/applications/" + id + "/status").header(AUTHORIZATION, employer)
                        .contentType(APPLICATION_JSON).content("{\"status\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest());
    }
}
