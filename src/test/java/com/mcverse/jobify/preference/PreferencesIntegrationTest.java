package com.mcverse.jobify.preference;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET/PUT /users/me/preferences (#25), and the effect of a switched-off notification type. */
@SpringBootTest
class PreferencesIntegrationTest {

    private static final String PASSWORD = "Prefs-Pass-4321";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String username(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String register(String username, String role) throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"role\":\""
                                + role + "\",\"firstName\":\"P\",\"lastName\":\"T\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private ResultActions save(String token, String json) throws Exception {
        return mvc.perform(put("/users/me/preferences").header(AUTHORIZATION, token)
                .contentType(APPLICATION_JSON).content(json));
    }

    private int createJob(String employer) throws Exception {
        String body = mvc.perform(post("/jobs").header(AUTHORIZATION, employer).contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Prefs job\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,"
                                + "\"rateType\":\"HOURLY\",\"location\":\"Berlin\",\"workMode\":\"REMOTE\","
                                + "\"employmentType\":\"FULL_TIME\"}"))
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

    private int notificationCount(String token) throws Exception {
        String body = mvc.perform(get("/notifications").header(AUTHORIZATION, token)).andReturn().getResponse()
                .getContentAsString();
        return JsonPath.<java.util.List<?>>read(body, "$").size();
    }

    // -------------------------------------------------------------------------------------- read and write

    @Test
    void everyoneStartsWithEveryNotificationOnAndNoColour() throws Exception {
        for (String role : new String[] {"SEEKER", "EMPLOYER"}) {
            String token = register(username("pf_default"), role);
            mvc.perform(get("/users/me/preferences").header(AUTHORIZATION, token)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.notifications.INTERVIEW").value(true))
                    .andExpect(jsonPath("$.notifications.APPLICATION").value(true))
                    .andExpect(jsonPath("$.notifications.SYSTEM").value(true))
                    .andExpect(jsonPath("$.notifications.HIRING").value(true))
                    .andExpect(jsonPath("$.notifications.ACCOUNT").value(true))
                    .andExpect(jsonPath("$.accentColor").doesNotExist());
        }
        mvc.perform(get("/users/me/preferences").header(AUTHORIZATION, bearer(mvc, "admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notifications.SYSTEM").value(true));
    }

    @Test
    void onlyWhatIsSentChangesAndItIsRemembered() throws Exception {
        String token = register(username("pf_merge"), "SEEKER");
        save(token, "{\"notifications\":{\"APPLICATION\":false}}").andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.APPLICATION").value(false))
                .andExpect(jsonPath("$.notifications.INTERVIEW").value(true));
        save(token, "{\"accentColor\":\"#1A73E8\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.accentColor").value("#1a73e8"))                    // stored lower case
                .andExpect(jsonPath("$.notifications.APPLICATION").value(false));         // kept
        save(token, "{\"notifications\":{\"interview\":false,\"APPLICATION\":true}}").andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.INTERVIEW").value(false))            // names are case-insensitive
                .andExpect(jsonPath("$.notifications.APPLICATION").value(true))
                .andExpect(jsonPath("$.accentColor").value("#1a73e8"));
        mvc.perform(get("/users/me/preferences").header(AUTHORIZATION, token))
                .andExpect(jsonPath("$.notifications.INTERVIEW").value(false))
                .andExpect(jsonPath("$.accentColor").value("#1a73e8"));
        save(token, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.accentColor").value("#1a73e8"));
    }

    @Test
    void anEmptyColourClearsItAndSettingsBelongToTheirOwner() throws Exception {
        String a = register(username("pf_a"), "SEEKER");
        String b = register(username("pf_b"), "EMPLOYER");
        save(a, "{\"accentColor\":\"#00ff00\",\"notifications\":{\"SYSTEM\":false}}").andExpect(status().isOk());
        mvc.perform(get("/users/me/preferences").header(AUTHORIZATION, b))
                .andExpect(jsonPath("$.accentColor").doesNotExist())
                .andExpect(jsonPath("$.notifications.SYSTEM").value(true));
        save(a, "{\"accentColor\":\"\"}").andExpect(status().isOk()).andExpect(jsonPath("$.accentColor").doesNotExist())
                .andExpect(jsonPath("$.notifications.SYSTEM").value(false));
    }

    @Test
    void badInputIs400WithAReadableMessageAndChangesNothing() throws Exception {
        String token = register(username("pf_bad"), "SEEKER");
        save(token, "{\"notifications\":{\"SYSTEM\":false,\"WEATHER\":true}}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Unknown notification type 'WEATHER'. Use one of: INTERVIEW")));
        save(token, "{\"notifications\":{\"ACCOUNT\":false}}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("ACCOUNT notifications cannot be turned off")));
        save(token, "{\"notifications\":{\"SYSTEM\":null}}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("notifications.SYSTEM must be true or false."));
        for (String colour : new String[] {"red", "#12345", "#1234567", "#gggggg", "1a73e8", " "}) {
            save(token, "{\"notifications\":{\"SYSTEM\":false},\"accentColor\":\"" + colour + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("accentColor must look like #1a73e8, or be empty to clear it."));
        }
        save(token, "{\"notifications\":{\"SYSTEM\":\"yes\"}}").andExpect(status().isBadRequest());
        mvc.perform(put("/users/me/preferences").header(AUTHORIZATION, token)).andExpect(status().isBadRequest());
        // all of that left the settings alone
        mvc.perform(get("/users/me/preferences").header(AUTHORIZATION, token))
                .andExpect(jsonPath("$.notifications.SYSTEM").value(true));
        // ACCOUNT true is harmless
        save(token, "{\"notifications\":{\"ACCOUNT\":true}}").andExpect(status().isOk());
    }

    @Test
    void anonymousAndStaleTokensGet401() throws Exception {
        mvc.perform(get("/users/me/preferences")).andExpect(status().isUnauthorized());
        mvc.perform(put("/users/me/preferences").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/users/me/preferences").header(AUTHORIZATION, "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------------------------------- effect

    @Test
    void aSwitchedOffTypeIsNotDeliveredAndOthersStillAre() throws Exception {
        String employer = register(username("pf_emp"), "EMPLOYER");
        String seeker = register(username("pf_sk"), "SEEKER");
        save(employer, "{\"notifications\":{\"APPLICATION\":false}}").andExpect(status().isOk());
        save(seeker, "{\"notifications\":{\"INTERVIEW\":false}}").andExpect(status().isOk());

        String applicationId = apply(seeker, createJob(employer));
        assertEquals(0, notificationCount(employer), "the employer switched off APPLICATION");
        mvc.perform(get("/notifications/unread-count").header(AUTHORIZATION, employer))
                .andExpect(jsonPath("$.count").value(0));

        move(employer, applicationId, "IN_REVIEW");   // APPLICATION: seeker still gets it
        move(employer, applicationId, "INTERVIEW");   // INTERVIEW: seeker switched it off
        move(employer, applicationId, "OFFER");       // HIRING: seeker still gets it
        mvc.perform(get("/notifications").header(AUTHORIZATION, seeker)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("HIRING"))
                .andExpect(jsonPath("$[1].type").value("APPLICATION"));
        // the move itself was not affected
        mvc.perform(get("/applications/me").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$[0].status").value("OFFER"));
    }

    @Test
    void switchingATypeBackOnDeliversAgainButNotWhatWasDroppedBefore() throws Exception {
        String employer = register(username("pf_back_e"), "EMPLOYER");
        String seeker = register(username("pf_back_s"), "SEEKER");
        int job = createJob(employer);
        save(employer, "{\"notifications\":{\"APPLICATION\":false}}").andExpect(status().isOk());
        apply(seeker, job);
        save(employer, "{\"notifications\":{\"APPLICATION\":true}}").andExpect(status().isOk());
        assertEquals(0, notificationCount(employer));
        apply(register(username("pf_back_s2"), "SEEKER"), job);
        assertEquals(1, notificationCount(employer));
    }

    @Test
    void accountNotificationsAreAlwaysDelivered() throws Exception {
        String name = username("pf_acct");
        String token = register(name, "SEEKER");
        save(token, "{\"notifications\":{\"SYSTEM\":false,\"APPLICATION\":false,\"INTERVIEW\":false,\"HIRING\":false}}")
                .andExpect(status().isOk());
        mvc.perform(put("/account/password").header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Another-Pass-9876\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/notifications").header(AUTHORIZATION, token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("ACCOUNT"));
    }

    @Test
    void deletingAnAccountRemovesItsSettings() throws Exception {
        String name = username("pf_del");
        String token = register(name, "SEEKER");
        save(token, "{\"accentColor\":\"#123456\",\"notifications\":{\"SYSTEM\":false}}").andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("select count(*) from user_preferences where username = ?", Integer.class, name));
        String req = mvc.perform(post("/account/deletion-request").header(AUTHORIZATION, token)
                        .contentType(APPLICATION_JSON).content("{\"reason\":\"bye\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/admin/deletion-requests/" + (String) JsonPath.read(req, "$.id") + "/approve")
                .header(AUTHORIZATION, bearer(mvc, "admin"))).andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("select count(*) from user_preferences where username = ?", Integer.class, name));
        assertEquals(0, jdbc.queryForObject("select count(*) from user_disabled_notifications where username = ?",
                Integer.class, name));
    }
}
