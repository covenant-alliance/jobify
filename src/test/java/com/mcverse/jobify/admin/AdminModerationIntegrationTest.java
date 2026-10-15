package com.mcverse.jobify.admin;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What an administrator can do directly: disable/enable and delete accounts, close/reopen and delete jobs. */
@SpringBootTest
class AdminModerationIntegrationTest {

    private static final String PASSWORD = "Moderate-Pass-4321";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mvc;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin = bearer(mvc, "admin");
    }

    private String name(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String register(String username, String role) throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(
                        "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"firstName\":\"M\","
                                + "\"lastName\":\"D\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private int createJob(String token) throws Exception {
        String body = mvc.perform(post("/jobs").header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Moderated\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,\"rateType\":\"HOURLY\","
                                + "\"location\":\"Berlin\",\"workMode\":\"REMOTE\",\"employmentType\":\"FULL_TIME\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.postId");
    }

    private int notifications(String token, String title) throws Exception {
        String body = mvc.perform(get("/notifications").header(AUTHORIZATION, token)).andReturn().getResponse()
                .getContentAsString();
        return JsonPath.<java.util.List<String>>read(body, "$[?(@.title=='" + title + "')].title").size();
    }

    private void bad(ResultActions r, int status, String message) throws Exception {
        r.andExpect(status().is(status)).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message));
    }

    // ------------------------------------------------------------------------------------------ disable

    @Test
    void aDisabledAccountCannotSignInAndItsTokenStopsWorking() throws Exception {
        String who = name("mod_dis");
        String token = register(who, "SEEKER");
        mvc.perform(get("/applications/me").header(AUTHORIZATION, token)).andExpect(status().isOk());

        mvc.perform(post("/admin/users/" + who + "/disable").header(AUTHORIZATION, admin)
                        .contentType(APPLICATION_JSON).content("{\"reason\":\"Repeated abuse reports\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(who))
                .andExpect(jsonPath("$.enabled").value(false));

        // the right password is told why; anyone else sees an ordinary failure
        bad(login(who, PASSWORD), 403, "This account has been disabled. Please contact support.");
        bad(login(who, "Wrong-Pass-1234"), 401, "Invalid username or password");
        mvc.perform(get("/applications/me").header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/users").param("q", who).header(AUTHORIZATION, admin))
                .andExpect(jsonPath("$.content[0].enabled").value(false));

        mvc.perform(post("/admin/users/" + who + "/enable").header(AUTHORIZATION, admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
        login(who, PASSWORD).andExpect(status().isOk());
        mvc.perform(get("/applications/me").header(AUTHORIZATION, token)).andExpect(status().isOk()); // old token valid again
        mvc.perform(get("/admin/users").param("q", who).header(AUTHORIZATION, admin))
                .andExpect(jsonPath("$.content[0].enabled").value(true));
    }

    @Test
    void disablingFollowsItsRules() throws Exception {
        String who = name("mod_rule");
        register(who, "EMPLOYER");
        bad(mvc.perform(post("/admin/users/admin/disable").header(AUTHORIZATION, admin)), 422,
                "You cannot disable your own account here.");
        bad(mvc.perform(post("/admin/users/" + UUID.randomUUID() + "/disable").header(AUTHORIZATION, admin)), 404,
                "We could not find that account.");
        mvc.perform(post("/admin/users/" + who + "/disable").header(AUTHORIZATION, admin)).andExpect(status().isOk());
        bad(mvc.perform(post("/admin/users/" + who + "/disable").header(AUTHORIZATION, admin)), 422,
                "That account is already disabled.");
        mvc.perform(post("/admin/users/" + who + "/enable").header(AUTHORIZATION, admin)).andExpect(status().isOk());
        bad(mvc.perform(post("/admin/users/" + who + "/enable").header(AUTHORIZATION, admin)), 422,
                "That account is already enabled.");
        mvc.perform(post("/admin/users/" + who + "/disable").header(AUTHORIZATION, admin)
                .contentType(APPLICATION_JSON).content("{\"reason\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherAdministratorCannotBeDisabledOrDeleted() throws Exception {
        // the seeded "admin" is the only administrator: ask as an employer-turned-nothing, so use the self rule
        // and the role rule through a second admin row
        jdbc.update("insert into app_users (username, password, role, enabled) select ?, password, 'ADMIN', true from app_users where username = 'admin'",
                "second_admin_" + UUID.randomUUID().toString().substring(0, 6));
        String other = jdbc.queryForObject("select username from app_users where username like 'second_admin_%' order by id desc limit 1", String.class);
        bad(mvc.perform(post("/admin/users/" + other + "/disable").header(AUTHORIZATION, admin)), 422,
                "Administrator accounts cannot be disabled here.");
        bad(mvc.perform(delete("/admin/users/" + other).param("reason", "test").header(AUTHORIZATION, admin)), 422,
                "Administrator accounts cannot be deleted here.");
        jdbc.update("delete from app_users where username = ?", other);
    }

    @Test
    void onlyAdministratorsCanModerate() throws Exception {
        String seeker = register(name("mod_sk"), "SEEKER");
        String employer = register(name("mod_em"), "EMPLOYER");
        int job = createJob(employer);
        for (String token : new String[] {seeker, employer}) {
            mvc.perform(post("/admin/users/alice_s/disable").header(AUTHORIZATION, token)).andExpect(status().isForbidden());
            mvc.perform(delete("/admin/users/alice_s").param("reason", "x").header(AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/admin/jobs/" + job + "/available").header(AUTHORIZATION, token)
                    .contentType(APPLICATION_JSON).content("{\"available\":false,\"reason\":\"x\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/admin/jobs/" + job).param("reason", "x").header(AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/admin/users/alice_s/disable")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/admin/jobs/" + job).param("reason", "x")).andExpect(status().isUnauthorized());
        mvc.perform(get("/jobs/" + job)).andExpect(status().isOk()); // nothing happened
    }

    // ----------------------------------------------------------------------------------- delete account

    @Test
    void anAdministratorDeletesAnAccountDirectlyWithAReason() throws Exception {
        String who = name("mod_del");
        String employer = register(who, "EMPLOYER");
        int job = createJob(employer);

        bad(mvc.perform(delete("/admin/users/" + who).header(AUTHORIZATION, admin)), 400, "reason is required.");
        bad(mvc.perform(delete("/admin/users/" + who).param("reason", "  ").header(AUTHORIZATION, admin)), 400,
                "reason is required.");
        bad(mvc.perform(delete("/admin/users/" + who).param("reason", "x".repeat(501)).header(AUTHORIZATION, admin)),
                400, "reason must be at most 500 characters.");
        bad(mvc.perform(delete("/admin/users/admin").param("reason", "x").header(AUTHORIZATION, admin)), 422,
                "You cannot delete your own account here.");
        mvc.perform(delete("/admin/users/" + UUID.randomUUID()).param("reason", "x").header(AUTHORIZATION, admin))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/admin/users/" + who).param("reason", "Fraud confirmed").header(AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        login(who, PASSWORD).andExpect(status().isUnauthorized());
        mvc.perform(get("/jobs/" + job)).andExpect(status().isNotFound());
        mvc.perform(get("/jobs/mine").header(AUTHORIZATION, employer)).andExpect(status().isUnauthorized());
    }

    @Test
    void deletingASeekerRemovesTheirApplications() throws Exception {
        String employer = register(name("mod_emp2"), "EMPLOYER");
        int job = createJob(employer);
        String who = name("mod_sk2");
        String seeker = register(who, "SEEKER");
        mvc.perform(post("/jobs/" + job + "/apply").header(AUTHORIZATION, seeker)).andExpect(status().isCreated());
        mvc.perform(delete("/admin/users/" + who).param("reason", "Requested by email").header(AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/jobs/" + job + "/applications").header(AUTHORIZATION, employer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    // ----------------------------------------------------------------------------------------- jobs

    @Test
    void anAdministratorClosesAndReopensAJobAndTheOwnersAreTold() throws Exception {
        String employer = register(name("mod_jo"), "EMPLOYER");
        int job = createJob(employer);

        bad(mvc.perform(patch("/admin/jobs/" + job + "/available").header(AUTHORIZATION, admin)
                .contentType(APPLICATION_JSON).content("{\"available\":false}")), 400, "reason is required.");
        mvc.perform(patch("/admin/jobs/" + job + "/available").header(AUTHORIZATION, admin)
                        .contentType(APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/admin/jobs/" + job + "/available").header(AUTHORIZATION, admin)
                        .contentType(APPLICATION_JSON).content("{\"available\":false,\"reason\":\"Breaks the posting rules\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.postId").value(job))
                .andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/jobs/" + job)).andExpect(jsonPath("$.available").value(false));
        assertEquals(1, notifications(employer, "An administrator closed your job"));
        mvc.perform(get("/notifications").header(AUTHORIZATION, employer))
                .andExpect(jsonPath("$[0].body", containsString("Reason: Breaks the posting rules")))
                .andExpect(jsonPath("$[0].jobId").value(job)).andExpect(jsonPath("$[0].type").value("SYSTEM"));
        bad(mvc.perform(patch("/admin/jobs/" + job + "/available").header(AUTHORIZATION, admin)
                .contentType(APPLICATION_JSON).content("{\"available\":false,\"reason\":\"again\"}")), 422,
                "That job is already closed.");

        mvc.perform(patch("/admin/jobs/" + job + "/available").header(AUTHORIZATION, admin)
                        .contentType(APPLICATION_JSON).content("{\"available\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        assertEquals(1, notifications(employer, "An administrator reopened your job"));
        mvc.perform(patch("/admin/jobs/987654/available").header(AUTHORIZATION, admin)
                .contentType(APPLICATION_JSON).content("{\"available\":true}")).andExpect(status().isNotFound());
    }

    @Test
    void anAdministratorDeletesAJobWithItsApplicationsSavedCopiesAndPictures() throws Exception {
        String employer = register(name("mod_jd"), "EMPLOYER");
        int job = createJob(employer);
        mvc.perform(multipart("/jobs/" + job + "/images").file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                .header(AUTHORIZATION, employer)).andExpect(status().isCreated());
        String seeker = register(name("mod_sj"), "SEEKER");
        mvc.perform(post("/jobs/" + job + "/apply").header(AUTHORIZATION, seeker)).andExpect(status().isCreated());
        mvc.perform(put("/jobs/" + job + "/save").header(AUTHORIZATION, seeker)).andExpect(status().isNoContent());

        bad(mvc.perform(delete("/admin/jobs/" + job).header(AUTHORIZATION, admin)), 400, "reason is required.");
        mvc.perform(delete("/admin/jobs/" + job).param("reason", "Scam posting").header(AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        mvc.perform(get("/jobs/" + job)).andExpect(status().isNotFound());
        mvc.perform(get("/applications/me").header(AUTHORIZATION, seeker)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/jobs/saved").header(AUTHORIZATION, seeker)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/notifications").header(AUTHORIZATION, employer))
                .andExpect(jsonPath("$[0].title").value("An administrator removed your job"))
                .andExpect(jsonPath("$[0].body", containsString("Reason: Scam posting")));
        mvc.perform(delete("/admin/jobs/" + job).param("reason", "again").header(AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
    }
}
