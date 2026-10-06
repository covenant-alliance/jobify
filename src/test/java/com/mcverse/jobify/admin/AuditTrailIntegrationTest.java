package com.mcverse.jobify.admin;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mcverse.jobify.common.logging.CorrelationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Security events end up on the AUDIT logger, with the request id, and never with a password. */
@SpringBootTest
class AuditTrailIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private CorrelationFilter correlationFilter;

    private MockMvc mvc;
    private final Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .addFilters(correlationFilter).build();
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(appender);
    }

    private List<String> lines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private boolean logged(String fragment) {
        return lines().stream().anyMatch(l -> l.contains(fragment));
    }

    private void login(String username, String password) throws Exception {
        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void successfulLoginIsAudited() throws Exception {
        login("alice_s", "password");
        assertTrue(logged("actor='alice_s' action=LOGIN_SUCCESS"), lines().toString());
    }

    @Test
    void failedLoginIsAuditedWithoutThePassword() throws Exception {
        login("bob_s", "Not-the-Password-9");
        assertTrue(logged("actor='bob_s' action=LOGIN_FAILURE"), lines().toString());
        assertFalse(lines().toString().contains("Not-the-Password-9"));
    }

    @Test
    void unknownUsernamesAreAuditedToo() throws Exception {
        login("nobody_here", "whatever-123");
        assertTrue(logged("actor='nobody_here' action=LOGIN_FAILURE"), lines().toString());
    }

    @Test
    void lockoutAndLaterBlockedAttemptsAreAudited() throws Exception {
        for (int i = 0; i < 5; i++) login("lock_me_audit", "wrong-pass-" + i);
        assertTrue(logged("actor='lock_me_audit' action=LOGIN_FAILURE_LOCKED"), lines().toString());
        login("lock_me_audit", "wrong-again");
        assertTrue(logged("actor='lock_me_audit' action=LOGIN_BLOCKED"), lines().toString());
    }

    @Test
    void registrationIsAudited() throws Exception {
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(
                "{\"username\":\"audit_new_user\",\"password\":\"Fresh-Pass-4321\",\"firstName\":\"A\","
                        + "\"lastName\":\"U\",\"role\":\"SEEKER\"}")).andExpect(status().isCreated());
        assertTrue(logged("actor='audit_new_user' action=REGISTER role=SEEKER"), lines().toString());
        assertFalse(lines().toString().contains("Fresh-Pass-4321"));
    }

    @Test
    void passwordChangeAttemptsAreAudited() throws Exception {
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(
                "{\"username\":\"audit_pw_user\",\"password\":\"Start-Pass-4321\",\"firstName\":\"A\","
                        + "\"lastName\":\"P\",\"role\":\"SEEKER\"}")).andExpect(status().isCreated());
        String token = bearerFor("audit_pw_user", "Start-Pass-4321");
        mvc.perform(put("/account/password").header("Authorization", token).contentType(APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong-current\",\"newPassword\":\"Newer-Pass-9876\"}"))
                .andExpect(status().isUnprocessableEntity());
        assertTrue(logged("actor='audit_pw_user' action=PASSWORD_CHANGE_REFUSED"), lines().toString());
        mvc.perform(put("/account/password").header("Authorization", token).contentType(APPLICATION_JSON)
                .content("{\"currentPassword\":\"Start-Pass-4321\",\"newPassword\":\"Newer-Pass-9876\"}"))
                .andExpect(status().isOk());
        assertTrue(logged("actor='audit_pw_user' action=PASSWORD_CHANGED"), lines().toString());
        assertFalse(lines().toString().contains("Newer-Pass-9876"));
        assertFalse(lines().toString().contains("Start-Pass-4321"));
    }

    @Test
    void deletionRequestAndCancellationAreAudited() throws Exception {
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(
                "{\"username\":\"audit_del_user\",\"password\":\"Delete-Pass-4321\",\"firstName\":\"A\","
                        + "\"lastName\":\"D\",\"role\":\"SEEKER\"}")).andExpect(status().isCreated());
        String token = bearerFor("audit_del_user", "Delete-Pass-4321");
        mvc.perform(post("/account/deletion-request").header("Authorization", token)
                .contentType(APPLICATION_JSON).content("{\"reason\":\"leaving\"}")).andExpect(status().isCreated());
        assertTrue(logged("actor='audit_del_user' action=DELETION_REQUESTED role=SEEKER"), lines().toString());
        mvc.perform(delete("/account/deletion-request").header("Authorization", token))
                .andExpect(status().isNoContent());
        assertTrue(logged("actor='audit_del_user' action=DELETION_CANCELLED"), lines().toString());
    }

    @Test
    void ownershipDenialIsAudited() throws Exception {
        String other = bearer(mvc, "startupxyz");
        // job 1 belongs to another employer: closing it must be refused and audited
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/jobs/1/available")
                .header("Authorization", other).contentType(APPLICATION_JSON).content("{\"available\":false}"))
                .andExpect(status().isForbidden());
        assertTrue(logged("actor='startupxyz' action=ACCESS_DENIED"), lines().toString());
    }

    @Test
    void auditEventsCarryTheRequestIdAndClientAddress() throws Exception {
        mvc.perform(post("/auth/login").header("X-Request-Id", "trace-abc-1").contentType(APPLICATION_JSON)
                .content("{\"username\":\"alice_s\",\"password\":\"password\"}"))
                .andExpect(status().isOk());
        ILoggingEvent event = appender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("LOGIN_SUCCESS")).findFirst().orElseThrow();
        assertEquals("trace-abc-1", event.getMDCPropertyMap().get("requestId"));
        assertTrue(event.getMDCPropertyMap().containsKey("clientIp"));
    }

    @Test
    void responsesCarryARequestId() throws Exception {
        mvc.perform(get("/jobs")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().exists("X-Request-Id"));
    }

    @Test
    void browsersMayReadTheRequestIdAndRetryAfterHeaders() throws Exception {
        mvc.perform(get("/jobs").header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Expose-Headers", org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("X-Request-Id"),
                                org.hamcrest.Matchers.containsString("Retry-After"))));
    }

    private String bearerFor(String username, String password) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + com.jayway.jsonpath.JsonPath.read(body, "$.token");
    }
}
