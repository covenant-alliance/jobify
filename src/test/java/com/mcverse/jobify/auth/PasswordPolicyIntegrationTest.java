package com.mcverse.jobify.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The password policy as seen through register and change-password. */
@SpringBootTest
class PasswordPolicyIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private org.springframework.test.web.servlet.ResultActions register(String username, String password)
            throws Exception {
        return mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password
                        + "\",\"role\":\"SEEKER\",\"firstName\":\"P\",\"lastName\":\"P\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions change(String token, String current, String next)
            throws Exception {
        return mvc.perform(put("/account/password").header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    private String registerAndToken(String username, String password) throws Exception {
        String body = register(username, password).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void shortPasswordIsRejectedOnRegisterWithTheLengthRule() throws Exception {
        register("pp_short", "short1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password must be 8 to 72 characters"));
    }

    @Test
    void passwordEqualToTheUsernameIsRejectedOnRegister() throws Exception {
        register("pp_same_name", "PP_same_name")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password must not be the same as your username."));
    }

    @Test
    void commonPasswordIsRejectedOnRegisterAndNoAccountIsCreated() throws Exception {
        register("pp_common", "password123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("That password is too common. Please choose a less predictable one."));
        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"pp_common\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void goodPasswordRegistersAndLogsIn() throws Exception {
        register("pp_good", "a-decent-passphrase")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("pp_good"));
    }

    // ── change password ───────────────────────────────────────────────────────

    @Test
    void changeRejectsPasswordEqualToUsername() throws Exception {
        String token = registerAndToken("pp_change_name", "a-decent-passphrase");
        change(token, "a-decent-passphrase", "pp_change_name")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password must not be the same as your username."));
    }

    @Test
    void changeRejectsCommonPasswords() throws Exception {
        String token = registerAndToken("pp_change_common", "a-decent-passphrase");
        change(token, "a-decent-passphrase", "qwertyuiop")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("That password is too common. Please choose a less predictable one."));
    }

    @Test
    void changeRejectsTheSamePasswordAgain() throws Exception {
        String token = registerAndToken("pp_change_same", "a-decent-passphrase");
        change(token, "a-decent-passphrase", "a-decent-passphrase")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("The new password must be different from the current one."));
    }

    @Test
    void wrongCurrentPasswordIsStillReportedFirst() throws Exception {
        String token = registerAndToken("pp_change_wrong", "a-decent-passphrase");
        change(token, "not-the-password", "a-fresh-passphrase")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Current password is incorrect."));
    }

    @Test
    void aValidChangeWorks() throws Exception {
        String token = registerAndToken("pp_change_ok", "a-decent-passphrase");
        change(token, "a-decent-passphrase", "a-fresh-passphrase").andExpect(status().isOk());
    }
}
