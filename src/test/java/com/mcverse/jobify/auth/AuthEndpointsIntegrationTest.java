package com.mcverse.jobify.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuthEndpointsIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static String register(String username, String role) {
        return "{\"username\":\"" + username + "\",\"password\":\"s3cur3Pass\",\"role\":\"" + role
                + "\",\"firstName\":\"Test\",\"lastName\":\"User\"}";
    }

    @Test
    void loginReturnsTokenAndRole() throws Exception {
        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"alice_s\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.username").value("alice_s"))
                .andExpect(jsonPath("$.role").value("SEEKER"));
    }

    @Test
    void wrongPasswordIs401WithReadableMessage() throws Exception {
        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"alice_s\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void unknownUserGetsTheSame401AsWrongPassword() throws Exception {
        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void registeredSeekerCanUseTheTokenImmediately() throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content(register("new_seeker", "SEEKER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new_seeker"))
                .andExpect(jsonPath("$.role").value("SEEKER"))
                .andReturn().getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(body, "$.token");

        mvc.perform(get("/users/seekers/me").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("new_seeker"));
    }

    @Test
    void registeredEmployerGetsEmployerRole() throws Exception {
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content(register("new_employer", "EMPLOYER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("EMPLOYER"));
    }

    @Test
    void adminCannotBeSelfRegistered() throws Exception {
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content(register("sneaky_admin", "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot self-register as ADMIN."));
        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"sneaky_admin\",\"password\":\"s3cur3Pass\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerWithMissingFieldsIs400WithFieldNames() throws Exception {
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void duplicateUsernameIsRejectedWithoutServerError() throws Exception {
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content(register("dup_user", "SEEKER"))).andExpect(status().isCreated());
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content(register("dup_user", "SEEKER")))
                .andExpect(status().is4xxClientError());
    }

    // ── route security ────────────────────────────────────────────────────────

    @Test
    void protectedRoutesRejectAnonymousUsers() throws Exception {
        mvc.perform(get("/users/seekers/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/account/deletion-request")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/deletion-requests")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mvc.perform(get("/users/seekers/me").header(AUTHORIZATION, "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicRoutesNeedNoToken() throws Exception {
        mvc.perform(get("/jobs")).andExpect(status().isOk());
        mvc.perform(get("/content")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void adminRoutesRequireTheAdminRole() throws Exception {
        String seeker = com.mcverse.jobify.support.ApiTestSupport.bearer(mvc, "alice_s");
        String employer = com.mcverse.jobify.support.ApiTestSupport.bearer(mvc, "techcorp");
        String admin = com.mcverse.jobify.support.ApiTestSupport.bearer(mvc, "admin");
        mvc.perform(get("/admin/deletion-requests").header(AUTHORIZATION, seeker)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/deletion-requests").header(AUTHORIZATION, employer)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/deletion-requests").header(AUTHORIZATION, admin)).andExpect(status().isOk());
    }
}
