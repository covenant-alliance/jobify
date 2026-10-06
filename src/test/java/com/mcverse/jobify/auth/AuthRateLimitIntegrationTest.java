package com.mcverse.jobify.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Per-address limit on login and register. Own context and database, with a low limit of 5 per minute. */
@SpringBootTest(properties = {
        "app.security.auth-rate-limit.max-requests=5",
        "app.security.auth-rate-limit.window-seconds=60",
        "app.security.login-lock.max-failures=1000",
        "spring.datasource.url=jdbc:h2:mem:ratelimit-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        // own in-memory H2 even when the suite is pointed at PostgreSQL (see docs/DATABASE_MIGRATION.md)
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
class AuthRateLimitIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private ResultActions login(String address, String username, String forwardedFor) throws Exception {
        var request = post("/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"wrong-password\"}")
                .with(r -> {
                    r.setRemoteAddr(address);
                    return r;
                });
        if (forwardedFor != null) {
            request = request.header("X-Forwarded-For", forwardedFor);
        }
        return mvc.perform(request);
    }

    private ResultActions register(String address, String username) throws Exception {
        return mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"a-decent-passphrase\","
                        + "\"role\":\"SEEKER\",\"firstName\":\"R\",\"lastName\":\"L\"}")
                .with(r -> {
                    r.setRemoteAddr(address);
                    return r;
                }));
    }

    @Test
    void sixthLoginFromOneAddressInAMinuteIs429WithAReadableMessageAndRetryAfter() throws Exception {
        for (int i = 1; i <= 5; i++) {
            login("10.1.0.1", "user" + i, null).andExpect(status().isUnauthorized());
        }
        login("10.1.0.1", "user6", null)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Too many requests. Please wait 1 minute and try again."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void aRateLimitedRequestIsAudited() throws Exception {
        ch.qos.logback.classic.Logger audit = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("AUDIT");
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        audit.addAppender(appender);
        try {
            for (int i = 1; i <= 6; i++) {
                login("10.1.0.9", "audit" + i, null);
            }
            assertTrue(appender.list.stream().anyMatch(e -> e.getFormattedMessage()
                    .contains("action=RATE_LIMITED path=/auth/login")), appender.list.toString());
        } finally {
            audit.detachAppender(appender);
        }
    }

    @Test
    void anotherAddressIsNotAffected() throws Exception {
        for (int i = 1; i <= 6; i++) {
            login("10.2.0.1", "other" + i, null);
        }
        login("10.2.0.1", "other7", null).andExpect(status().isTooManyRequests());
        login("10.2.0.2", "other8", null).andExpect(status().isUnauthorized());
    }

    @Test
    void registerHasItsOwnBudgetAndIsLimitedToo() throws Exception {
        for (int i = 1; i <= 5; i++) {
            register("10.3.0.1", "rl_user_" + i).andExpect(status().isCreated());
        }
        register("10.3.0.1", "rl_user_6")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
        // login from the same address has a separate budget
        login("10.3.0.1", "rl_user_1", null).andExpect(status().isUnauthorized());
    }

    @Test
    void aSpoofedForwardedForHeaderDoesNotDodgeTheLimit() throws Exception {
        for (int i = 1; i <= 5; i++) {
            login("10.4.0.1", "spoof" + i, "203.0.113." + i).andExpect(status().isUnauthorized());
        }
        login("10.4.0.1", "spoof6", "198.51.100.77").andExpect(status().isTooManyRequests());
    }

    @Test
    void otherEndpointsAreNeverRateLimitedByThisFilter() throws Exception {
        for (int i = 1; i <= 20; i++) {
            mvc.perform(get("/jobs").with(r -> {
                r.setRemoteAddr("10.5.0.1");
                return r;
            })).andExpect(status().isOk());
        }
    }
}
