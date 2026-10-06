package com.mcverse.jobify.account;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Password change and the deletion-request workflow. Each test registers its own throwaway user. */
@SpringBootTest
class AccountEndpointsIntegrationTest {

    private static final String PASSWORD = "s3cur3Pass";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** Registers a user and returns their Authorization header value. */
    private String registerUser(String username, String role) throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD
                                + "\",\"role\":\"" + role + "\",\"firstName\":\"T\",\"lastName\":\"U\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private String requestDeletion(String token, String reason) throws Exception {
        String body = mvc.perform(post("/account/deletion-request").header(AUTHORIZATION, token)
                        .contentType(APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private int loginStatus(String username, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    // ── password ──────────────────────────────────────────────────────────────

    @Test
    void changingPasswordSwitchesWhichPasswordLogsIn() throws Exception {
        String token = registerUser("pw_user", "SEEKER");
        mvc.perform(put("/account/password").header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"brand-new-pass\"}"))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(200, loginStatus("pw_user", "brand-new-pass"));
        org.junit.jupiter.api.Assertions.assertEquals(401, loginStatus("pw_user", PASSWORD));
    }

    @Test
    void wrongCurrentPasswordIs422AndChangesNothing() throws Exception {
        String token = registerUser("pw_wrong", "SEEKER");
        mvc.perform(put("/account/password").header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"not-it\",\"newPassword\":\"brand-new-pass\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Current password is incorrect."));
        org.junit.jupiter.api.Assertions.assertEquals(200, loginStatus("pw_wrong", PASSWORD));
    }

    @Test
    void shortNewPasswordIs400() throws Exception {
        String token = registerUser("pw_short", "SEEKER");
        mvc.perform(put("/account/password").header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("must be 8 to 72 characters")));
    }

    @Test
    void anonymousCannotChangePassword() throws Exception {
        mvc.perform(put("/account/password").contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"a\",\"newPassword\":\"bbbbbbbbb\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── deletion request: user side ───────────────────────────────────────────

    @Test
    void getReturnsEmpty200WhenThereIsNoRequest() throws Exception {
        String token = registerUser("del_none", "SEEKER");
        mvc.perform(get("/account/deletion-request").header(AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void userCanRequestViewAndCancelDeletion() throws Exception {
        String token = registerUser("del_cancel", "SEEKER");
        requestDeletion(token, "leaving");

        mvc.perform(get("/account/deletion-request").header(AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.username").value("del_cancel"))
                .andExpect(jsonPath("$.requesterRole").value("SEEKER"))
                .andExpect(jsonPath("$.reason").value("leaving"));

        mvc.perform(delete("/account/deletion-request").header(AUTHORIZATION, token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/account/deletion-request").header(AUTHORIZATION, token))
                .andExpect(content().string(""));
    }

    @Test
    void secondPendingRequestIs422() throws Exception {
        String token = registerUser("del_twice", "SEEKER");
        requestDeletion(token, "one");
        mvc.perform(post("/account/deletion-request").header(AUTHORIZATION, token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("You already have a pending deletion request."));
    }

    @Test
    void requestWithoutBodyIsAllowed() throws Exception {
        String token = registerUser("del_nobody", "EMPLOYER");
        mvc.perform(post("/account/deletion-request").header(AUTHORIZATION, token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requesterRole").value("EMPLOYER"));
    }

    @Test
    void cancellingWhenNothingIsPendingIs404() throws Exception {
        String token = registerUser("del_cancel_none", "SEEKER");
        mvc.perform(delete("/account/deletion-request").header(AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    // ── deletion request: admin side ──────────────────────────────────────────

    @Test
    void pendingRequestsAreListedForAdminOnly() throws Exception {
        String token = registerUser("del_listed", "SEEKER");
        requestDeletion(token, "x");
        String admin = bearer(mvc, "admin");

        mvc.perform(get("/admin/deletion-requests").header(AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", hasItem("del_listed")));
        mvc.perform(get("/admin/deletion-requests").header(AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void approvingDeletesTheSeekerAccountAndProfile() throws Exception {
        String token = registerUser("del_approved", "SEEKER");
        String id = requestDeletion(token, "bye");
        String admin = bearer(mvc, "admin");

        mvc.perform(post("/admin/deletion-requests/" + id + "/approve").header(AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());

        org.junit.jupiter.api.Assertions.assertEquals(401, loginStatus("del_approved", PASSWORD));
        mvc.perform(get("/admin/deletion-requests").header(AUTHORIZATION, admin))
                .andExpect(jsonPath("$[*].username", not(hasItem("del_approved"))));
    }

    @Test
    void approvingDeletesAnEmployerWithoutJobs() throws Exception {
        String token = registerUser("del_employer", "EMPLOYER");
        String id = requestDeletion(token, "closing");
        mvc.perform(post("/admin/deletion-requests/" + id + "/approve")
                        .header(AUTHORIZATION, bearer(mvc, "admin")))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(401, loginStatus("del_employer", PASSWORD));
    }

    /** Documents current behaviour: deleting an employer also deletes their job posts (JPA cascade). */
    @Test
    void approvingAnEmployerWhoOwnsJobsAlsoRemovesTheirJobs() throws Exception {
        String token = registerUser("del_employer_jobs", "EMPLOYER");
        String created = mvc.perform(post("/jobs").header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Temp role\",\"jobDescription\":\"<p>x</p>\",\"rate\":20,\"rateType\":\"HOURLY\",\"location\":\"Berlin\",\"workMode\":\"REMOTE\",\"employmentType\":\"FULL_TIME\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int jobId = JsonPath.read(created, "$.postId");
        String id = requestDeletion(token, "closing");

        mvc.perform(post("/admin/deletion-requests/" + id + "/approve")
                        .header(AUTHORIZATION, bearer(mvc, "admin")))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(401, loginStatus("del_employer_jobs", PASSWORD));
        mvc.perform(get("/jobs/" + jobId)).andExpect(status().isNotFound());
    }

    @Test
    void rejectingKeepsTheAccountAndRecordsTheNote() throws Exception {
        String token = registerUser("del_rejected", "SEEKER");
        String id = requestDeletion(token, "bye");
        String admin = bearer(mvc, "admin");

        mvc.perform(post("/admin/deletion-requests/" + id + "/reject").header(AUTHORIZATION, admin)
                        .contentType(APPLICATION_JSON).content("{\"note\":\"Open dispute\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.resolutionNote").value("Open dispute"));

        org.junit.jupiter.api.Assertions.assertEquals(200, loginStatus("del_rejected", PASSWORD));
        // a resolved request no longer counts as pending, so the user may ask again
        requestDeletion(token, "again");
    }

    @Test
    void resolvingTwiceIs422() throws Exception {
        String token = registerUser("del_resolved_twice", "SEEKER");
        String id = requestDeletion(token, "bye");
        String admin = bearer(mvc, "admin");
        mvc.perform(post("/admin/deletion-requests/" + id + "/reject").header(AUTHORIZATION, admin))
                .andExpect(status().isOk());
        mvc.perform(post("/admin/deletion-requests/" + id + "/approve").header(AUTHORIZATION, admin))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("This request has already been resolved."));
    }

    @Test
    void resolvingAnUnknownRequestIs404() throws Exception {
        String admin = bearer(mvc, "admin");
        mvc.perform(post("/admin/deletion-requests/does-not-exist/approve").header(AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        mvc.perform(post("/admin/deletion-requests/does-not-exist/reject").header(AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminsCannotResolve() throws Exception {
        String token = registerUser("del_forbidden", "SEEKER");
        String id = requestDeletion(token, "bye");
        mvc.perform(post("/admin/deletion-requests/" + id + "/approve").header(AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/deletion-requests/" + id + "/reject").header(AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }
}
