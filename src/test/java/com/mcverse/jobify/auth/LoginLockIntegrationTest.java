package com.mcverse.jobify.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lock after repeated failed logins. Own context and database, locking after 3 failures. */
@SpringBootTest(properties = {
        "app.security.login-lock.max-failures=3",
        "app.security.login-lock.window-minutes=10",
        "app.security.auth-rate-limit.max-requests=100000",
        "spring.datasource.url=jdbc:h2:mem:login-lock-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        // own in-memory H2 even when the suite is pointed at PostgreSQL (see docs/DATABASE_MIGRATION.md)
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
class LoginLockIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void thirdFailureLocksTheAccountEvenForTheCorrectPassword() throws Exception {
        login("alice_s", "wrong-1").andExpect(status().isUnauthorized());
        login("alice_s", "wrong-2").andExpect(status().isUnauthorized());
        login("alice_s", "wrong-3").andExpect(status().isUnauthorized());

        login("alice_s", "password")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "600"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Too many failed login attempts. Try again in 10 minutes."))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void otherAccountsAreNotLocked() throws Exception {
        for (int i = 1; i <= 3; i++) {
            login("bob_s", "wrong-" + i).andExpect(status().isUnauthorized());
        }
        login("bob_s", "password").andExpect(status().isTooManyRequests());
        login("carol_s", "password").andExpect(status().isOk());
    }

    @Test
    void aSuccessfulLoginResetsTheCount() throws Exception {
        login("techcorp", "wrong-1").andExpect(status().isUnauthorized());
        login("techcorp", "wrong-2").andExpect(status().isUnauthorized());
        login("techcorp", "password").andExpect(status().isOk());
        login("techcorp", "wrong-3").andExpect(status().isUnauthorized());
        login("techcorp", "wrong-4").andExpect(status().isUnauthorized());
        login("techcorp", "password").andExpect(status().isOk());
    }

    @Test
    void unknownUsernamesLockTooSoTheLockRevealsNothing() throws Exception {
        for (int i = 1; i <= 3; i++) {
            login("no_such_user", "whatever-" + i).andExpect(status().isUnauthorized());
        }
        login("no_such_user", "whatever-4")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many failed login attempts. Try again in 10 minutes."));
    }

    @Test
    void usernameMatchingIgnoresCase() throws Exception {
        for (int i = 1; i <= 3; i++) {
            login("STARTUPXYZ", "wrong-" + i).andExpect(status().isUnauthorized());
        }
        login("startupxyz", "password").andExpect(status().isTooManyRequests());
    }
}
