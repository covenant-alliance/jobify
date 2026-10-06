package com.mcverse.jobify.notification;

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
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P6: notifications and the events that create them. Each test registers its own users so counts are exact. */
@SpringBootTest
class NotificationsIntegrationTest {

    private static final String PASSWORD = "a-decent-passphrase";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String register(String username, String role) throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"role\":\""
                                + role + "\",\"firstName\":\"" + (role.equals("SEEKER") ? "Nina" : "Eric")
                                + "\",\"lastName\":\"Test\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private int createJob(String employer, String title) throws Exception {
        String body = mvc.perform(post("/jobs").header(AUTHORIZATION, employer).contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"" + title + "\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,"
                                + "\"rateType\":\"HOURLY\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.postId");
    }

    private String apply(String seeker, int jobId) throws Exception {
        String body = mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, seeker))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void move(String employer, String applicationId, String status) throws Exception {
        mvc.perform(put("/applications/" + applicationId + "/status").header(AUTHORIZATION, employer)
                        .contentType(APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    private String listJson(String token, String query) throws Exception {
        return mvc.perform(get("/notifications" + query).header(AUTHORIZATION, token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    // ── events ────────────────────────────────────────────────────────────────

    @Test
    void anApplicationNotifiesTheEmployerAndNotTheSeeker() throws Exception {
        String employer = register("nt_emp_apply", "EMPLOYER");
        String seeker = register("nt_sk_apply", "SEEKER");
        int jobId = createJob(employer, "Notify on apply");
        String applicationId = apply(seeker, jobId);

        mvc.perform(get("/notifications").header(AUTHORIZATION, employer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("APPLICATION"))
                .andExpect(jsonPath("$[0].title").value("New application"))
                .andExpect(jsonPath("$[0].body").value("Nina Test applied for Notify on apply."))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].jobId").value(jobId))
                .andExpect(jsonPath("$[0].applicationId").value(applicationId))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
        mvc.perform(get("/notifications").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void everyStageChangeNotifiesTheSeekerWithTheMatchingType() throws Exception {
        String employer = register("nt_emp_stage", "EMPLOYER");
        String seeker = register("nt_sk_stage", "SEEKER");
        String applicationId = apply(seeker, createJob(employer, "Stage notices"));

        move(employer, applicationId, "IN_REVIEW");
        Thread.sleep(3);
        move(employer, applicationId, "INTERVIEW");
        Thread.sleep(3);
        move(employer, applicationId, "OFFER");
        Thread.sleep(3);
        move(employer, applicationId, "REJECTED");

        // newest first
        mvc.perform(get("/notifications").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].type").value("APPLICATION"))
                .andExpect(jsonPath("$[0].title").value("Application update"))
                .andExpect(jsonPath("$[0].body", containsString("was not taken forward")))
                .andExpect(jsonPath("$[1].type").value("HIRING"))
                .andExpect(jsonPath("$[1].title").value("You have an offer"))
                .andExpect(jsonPath("$[2].type").value("INTERVIEW"))
                .andExpect(jsonPath("$[3].type").value("APPLICATION"))
                .andExpect(jsonPath("$[3].title").value("Your application is being reviewed"))
                .andExpect(jsonPath("$[3].applicationId").value(applicationId));
    }

    @Test
    void messagesNameTheCompanyWhenTheEmployerHasOne() throws Exception {
        String employer = bearer(mvc, "techcorp");
        String seeker = register("nt_sk_company", "SEEKER");
        String applicationId = apply(seeker, createJob(employer, "Company named notice"));
        move(employer, applicationId, "IN_REVIEW");

        mvc.perform(get("/notifications").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$[0].body")
                        .value("TechCorp Ltd is now reviewing your application for Company named notice."));
    }

    @Test
    void withdrawingNotifiesTheEmployer() throws Exception {
        String employer = register("nt_emp_withdraw", "EMPLOYER");
        String seeker = register("nt_sk_withdraw", "SEEKER");
        String applicationId = apply(seeker, createJob(employer, "Withdraw notice"));
        mvc.perform(delete("/applications/" + applicationId).header(AUTHORIZATION, seeker))
                .andExpect(status().isNoContent());

        mvc.perform(get("/notifications").header(AUTHORIZATION, employer))
                .andExpect(jsonPath("$.length()").value(2)) // the application, then the withdrawal
                .andExpect(jsonPath("$[?(@.title == 'Application withdrawn')].body")
                        .value("Nina Test withdrew their application for Withdraw notice."));
    }

    @Test
    void applyingAgainAfterAWithdrawalNotifiesTheEmployerAgain() throws Exception {
        String employer = register("nt_emp_reapply", "EMPLOYER");
        String seeker = register("nt_sk_reapply", "SEEKER");
        int jobId = createJob(employer, "Reapply notice");
        String applicationId = apply(seeker, jobId);
        mvc.perform(delete("/applications/" + applicationId).header(AUTHORIZATION, seeker));
        apply(seeker, jobId);

        mvc.perform(get("/notifications").header(AUTHORIZATION, employer))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void aRefusedChangeSendsNothing() throws Exception {
        String employer = register("nt_emp_refused", "EMPLOYER");
        String seeker = register("nt_sk_refused", "SEEKER");
        String applicationId = apply(seeker, createJob(employer, "Refused move"));
        mvc.perform(put("/applications/" + applicationId + "/status").header(AUTHORIZATION, employer)
                        .contentType(APPLICATION_JSON).content("{\"status\":\"OFFER\"}"))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/notifications").header(AUTHORIZATION, seeker)).andExpect(jsonPath("$.length()").value(0));
    }

    // ── reading ───────────────────────────────────────────────────────────────

    @Test
    void unreadCountDropsAsNotificationsAreRead() throws Exception {
        String employer = register("nt_emp_count", "EMPLOYER");
        String seeker = register("nt_sk_count", "SEEKER");
        String applicationId = apply(seeker, createJob(employer, "Count me"));
        move(employer, applicationId, "IN_REVIEW");
        move(employer, applicationId, "INTERVIEW");

        mvc.perform(get("/notifications/unread-count").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$.count").value(2));

        String id = JsonPath.read(listJson(seeker, ""), "$[0].id");
        mvc.perform(post("/notifications/" + id + "/read").header(AUTHORIZATION, seeker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        mvc.perform(post("/notifications/" + id + "/read").header(AUTHORIZATION, seeker)) // idempotent
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        mvc.perform(get("/notifications/unread-count").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$.count").value(1));
        mvc.perform(get("/notifications?unreadOnly=true").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void readAllMarksEverythingAndReportsHowManyChanged() throws Exception {
        String employer = register("nt_emp_all", "EMPLOYER");
        String seeker = register("nt_sk_all", "SEEKER");
        String applicationId = apply(seeker, createJob(employer, "Read all"));
        move(employer, applicationId, "IN_REVIEW");
        move(employer, applicationId, "INTERVIEW");
        move(employer, applicationId, "OFFER");

        mvc.perform(post("/notifications/read-all").header(AUTHORIZATION, seeker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(3));
        mvc.perform(post("/notifications/read-all").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$.updated").value(0));
        mvc.perform(get("/notifications/unread-count").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$.count").value(0));
        mvc.perform(get("/notifications").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$.length()").value(3)); // read ones are kept
    }

    @Test
    void readAllOnlyTouchesTheCallersNotifications() throws Exception {
        String employer = register("nt_emp_own", "EMPLOYER");
        String seeker = register("nt_sk_own", "SEEKER");
        apply(seeker, createJob(employer, "Only mine"));

        mvc.perform(post("/notifications/read-all").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$.updated").value(0));
        mvc.perform(get("/notifications/unread-count").header(AUTHORIZATION, employer))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void someoneElsesNotificationIsNotFoundAndStaysUnread() throws Exception {
        String employer = register("nt_emp_priv", "EMPLOYER");
        String seeker = register("nt_sk_priv", "SEEKER");
        apply(seeker, createJob(employer, "Private notice"));
        String employerNotificationId = JsonPath.read(listJson(employer, ""), "$[0].id");

        mvc.perform(post("/notifications/" + employerNotificationId + "/read").header(AUTHORIZATION, seeker))
                .andExpect(status().isNotFound());
        mvc.perform(post("/notifications/does-not-exist/read").header(AUTHORIZATION, seeker))
                .andExpect(status().isNotFound());
        mvc.perform(get("/notifications/unread-count").header(AUTHORIZATION, employer))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void limitControlsTheListSizeAndIsValidated() throws Exception {
        String employer = register("nt_emp_limit", "EMPLOYER");
        String seeker = register("nt_sk_limit", "SEEKER");
        String applicationId = apply(seeker, createJob(employer, "Limit notices"));
        move(employer, applicationId, "IN_REVIEW");
        move(employer, applicationId, "INTERVIEW");

        mvc.perform(get("/notifications?limit=1").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/notifications?limit=0").header(AUTHORIZATION, seeker))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("limit must be between 1 and 100."));
        mvc.perform(get("/notifications?limit=101").header(AUTHORIZATION, seeker))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/notifications?limit=abc").header(AUTHORIZATION, seeker))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousUsersAreRefusedEverywhere() throws Exception {
        mvc.perform(get("/notifications")).andExpect(status().isForbidden());
        mvc.perform(get("/notifications/unread-count")).andExpect(status().isForbidden());
        mvc.perform(post("/notifications/read-all")).andExpect(status().isForbidden());
        mvc.perform(post("/notifications/x/read")).andExpect(status().isForbidden());
    }

    // ── account events ────────────────────────────────────────────────────────

    @Test
    void changingAPasswordLeavesAnAccountNotice() throws Exception {
        String seeker = register("nt_sk_password", "SEEKER");
        mvc.perform(put("/account/password").header(AUTHORIZATION, seeker).contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"another-passphrase\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/notifications").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("ACCOUNT"))
                .andExpect(jsonPath("$[0].title").value("Your password was changed"));
    }

    @Test
    void aDeclinedDeletionRequestTellsTheUserWhy() throws Exception {
        String seeker = register("nt_sk_declined", "SEEKER");
        String body = mvc.perform(post("/account/deletion-request").header(AUTHORIZATION, seeker))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(body, "$.id");
        mvc.perform(post("/admin/deletion-requests/" + requestId + "/reject")
                        .header(AUTHORIZATION, bearer(mvc, "admin")).contentType(APPLICATION_JSON)
                        .content("{\"note\":\"Open dispute\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/notifications").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$[0].type").value("ACCOUNT"))
                .andExpect(jsonPath("$[0].title").value("Account deletion request declined"))
                .andExpect(jsonPath("$[0].body", containsString("Open dispute")));
    }

    @Test
    void deletingAnAccountRemovesItsNotifications() throws Exception {
        String employer = register("nt_emp_gone", "EMPLOYER");
        String seeker = register("nt_sk_gone", "SEEKER");
        apply(seeker, createJob(employer, "Gone notices"));
        assertEquals(1, jdbc.queryForObject("select count(*) from notifications where recipient_username = ?",
                Integer.class, "nt_emp_gone"));

        String body = mvc.perform(post("/account/deletion-request").header(AUTHORIZATION, employer))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(body, "$.id");
        mvc.perform(post("/admin/deletion-requests/" + requestId + "/approve")
                        .header(AUTHORIZATION, bearer(mvc, "admin")))
                .andExpect(status().isOk());

        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where recipient_username = ?",
                Integer.class, "nt_emp_gone"));
    }
}
