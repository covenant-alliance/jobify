package com.mcverse.jobify.auth;

import com.jayway.jsonpath.JsonPath;
import com.mcverse.jobify.auth.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Refresh tokens (#95): rotation, reuse detection, sign-out, and every reason a session must end. */
@SpringBootTest
class RefreshTokenIntegrationTest {

    private static final String PASSWORD = "Refresh-Pass-4321";
    private static final String EXPIRED = "Your session has expired or is not valid. Please sign in again.";

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RefreshTokenService refreshTokens;

    private MockMvc mvc;
    private String username;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        username = "rt_" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ---------------------------------------------------------------------------------------- helpers

    private String registerAndGetBody(String name, String role) throws Exception {
        return mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(
                        "{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\",\"firstName\":\"R\","
                                + "\"lastName\":\"T\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private String login(String name) throws Exception {
        return mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + token + "\"}"));
    }

    private ResultActions logout(String token) throws Exception {
        return mvc.perform(post("/auth/logout").contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + token + "\"}"));
    }

    private static String refreshOf(String body) {
        return JsonPath.read(body, "$.refreshToken");
    }

    private static String accessOf(String body) {
        return JsonPath.read(body, "$.token");
    }

    private void expectSessionEnded(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(EXPIRED)).andExpect(jsonPath("$.status").value(401));
    }

    /** Makes the token look as if it was first used a minute ago, so presenting it again is no longer a double click. */
    private void ageUse(String rawToken) {
        jdbc.update("update refresh_tokens set used_at = ? where token_hash = ?", LocalDateTime.now().minusMinutes(1),
                sha256(rawToken));
    }

    private static String sha256(String raw) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------------------------- issue and refresh

    @Test
    void registerAndLoginHandOutARefreshTokenAndNothingElseChanged() throws Exception {
        String registered = registerAndGetBody(username, "SEEKER");
        assertTrue(refreshOf(registered).matches("[A-Za-z0-9_-]{43}"), "256 random bits, URL-safe");
        long refreshMs = ((Number) JsonPath.read(registered, "$.refreshExpiresIn")).longValue();
        assertTrue(Math.abs(refreshMs - 30L * 24 * 3600 * 1000) < 5000, "30 days");
        assertEquals("Bearer", JsonPath.read(registered, "$.type"));
        assertTrue(((Number) JsonPath.read(registered, "$.expiresIn")).longValue() > 0);

        String loggedIn = login(username);
        assertNotEquals(refreshOf(registered), refreshOf(loggedIn), "every sign-in has its own refresh token");
        mvc.perform(get("/applications/me").header(AUTHORIZATION, "Bearer " + accessOf(loggedIn))).andExpect(status().isOk());
    }

    @Test
    void refreshingGivesAWorkingAccessTokenAndANewRefreshToken() throws Exception {
        String first = registerAndGetBody(username, "SEEKER");
        String body = refresh(refreshOf(first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username)).andExpect(jsonPath("$.role").value("SEEKER"))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.refreshToken", matchesPattern("[A-Za-z0-9_-]{43}")))
                .andReturn().getResponse().getContentAsString();
        assertNotEquals(refreshOf(first), refreshOf(body));
        mvc.perform(get("/applications/me").header(AUTHORIZATION, "Bearer " + accessOf(body))).andExpect(status().isOk());
        // and the chain goes on
        refresh(refreshOf(body)).andExpect(status().isOk());
    }

    @Test
    void onlyTheHashIsStored() throws Exception {
        String body = registerAndGetBody(username, "SEEKER");
        String raw = refreshOf(body);
        assertEquals(0, jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class, raw));
        assertEquals(1, jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ? and username = ?",
                Integer.class, sha256(raw), username));
    }

    // ------------------------------------------------------------------------- reuse and the grace window

    @Test
    void aSecondUseRightAfterTheFirstIsADoubleClickNotTheft() throws Exception {
        String first = registerAndGetBody(username, "SEEKER");
        String a = refresh(refreshOf(first)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String b = refresh(refreshOf(first)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertNotEquals(refreshOf(a), refreshOf(b));
        refresh(refreshOf(a)).andExpect(status().isOk());
        refresh(refreshOf(b)).andExpect(status().isOk());
    }

    @Test
    void usingAnOldTokenAgainLaterEndsTheWholeSignIn() throws Exception {
        String first = registerAndGetBody(username, "SEEKER");
        String second = refresh(refreshOf(first)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        ageUse(refreshOf(first));

        expectSessionEnded(refresh(refreshOf(first)));          // the stolen copy, replayed
        expectSessionEnded(refresh(refreshOf(second)));         // the rightful owner is signed out too
        // another sign-in of the same person is a different family and is not touched
        String other = login(username);
        refresh(refreshOf(other)).andExpect(status().isOk());
    }

    @Test
    void anExpiredRefreshTokenIsRefused() throws Exception {
        String body = registerAndGetBody(username, "SEEKER");
        jdbc.update("update refresh_tokens set expires_at = ? where token_hash = ?", LocalDateTime.now().minusSeconds(5),
                sha256(refreshOf(body)));
        expectSessionEnded(refresh(refreshOf(body)));
    }

    @Test
    void unknownAndMalformedTokensAreRefusedPlainly() throws Exception {
        expectSessionEnded(refresh("not-a-real-token"));
        expectSessionEnded(refresh(UUID.randomUUID().toString()));
        mvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON).content("{\"refreshToken\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + "x".repeat(201) + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/auth/refresh")).andExpect(status().isBadRequest());
        // an access token is not a refresh token
        expectSessionEnded(refresh(accessOf(registerAndGetBody(username, "SEEKER"))));
    }

    // ------------------------------------------------------------------------------------------ sign out

    @Test
    void signingOutEndsThatSignInOnlyAndIsHarmlessToRepeat() throws Exception {
        registerAndGetBody(username, "EMPLOYER");
        String phone = login(username);
        String laptop = login(username);

        logout(refreshOf(phone)).andExpect(status().isNoContent());
        expectSessionEnded(refresh(refreshOf(phone)));
        refresh(refreshOf(laptop)).andExpect(status().isOk());

        logout(refreshOf(phone)).andExpect(status().isNoContent());       // again
        logout("never-existed").andExpect(status().isNoContent());        // unknown: same answer
        mvc.perform(post("/auth/logout").contentType(APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void signingOutEverywhereEndsEverySignIn() throws Exception {
        String first = registerAndGetBody(username, "SEEKER");
        String second = login(username);
        mvc.perform(post("/auth/logout-all")).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/logout-all").header(AUTHORIZATION, "Bearer " + accessOf(second)))
                .andExpect(status().isNoContent());
        expectSessionEnded(refresh(refreshOf(first)));
        expectSessionEnded(refresh(refreshOf(second)));
        login(username); // signing in again works as ever
    }

    // ------------------------------------------------------------------- reasons a session must end

    @Test
    void changingThePasswordEndsEveryRefreshToken() throws Exception {
        String body = registerAndGetBody(username, "SEEKER");
        String other = login(username);
        mvc.perform(put("/account/password").header(AUTHORIZATION, "Bearer " + accessOf(body))
                        .contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Brand-New-Pass-7777\"}"))
                .andExpect(status().isOk());
        expectSessionEnded(refresh(refreshOf(body)));
        expectSessionEnded(refresh(refreshOf(other)));
    }

    @Test
    void disablingAnAccountEndsItsRefreshTokensForGood() throws Exception {
        String body = registerAndGetBody(username, "SEEKER");
        String admin = bearer(mvc, "admin");
        mvc.perform(post("/admin/users/" + username + "/disable").header(AUTHORIZATION, admin)).andExpect(status().isOk());
        expectSessionEnded(refresh(refreshOf(body)));
        mvc.perform(post("/admin/users/" + username + "/enable").header(AUTHORIZATION, admin)).andExpect(status().isOk());
        expectSessionEnded(refresh(refreshOf(body)));    // enabling does not bring old sessions back
        refresh(refreshOf(login(username))).andExpect(status().isOk());
    }

    @Test
    void aDisabledAccountCannotUseARefreshTokenThatWasIssuedJustBeforeItWasDisabled() throws Exception {
        String body = registerAndGetBody(username, "SEEKER");
        jdbc.update("update app_users set enabled = false where username = ?", username); // disabled behind the app's back
        expectSessionEnded(refresh(refreshOf(body)));
        assertEquals(0, jdbc.queryForObject("select count(*) from refresh_tokens where username = ? and revoked_at is null",
                Integer.class, username), "the refusal ended the account's sessions");
    }

    @Test
    void deletingAnAccountRemovesItsRefreshTokens() throws Exception {
        String body = registerAndGetBody(username, "SEEKER");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/admin/users/" + username).param("reason", "test").header(AUTHORIZATION, bearer(mvc, "admin")))
                .andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("select count(*) from refresh_tokens where username = ?", Integer.class, username));
        expectSessionEnded(refresh(refreshOf(body)));
    }

    // ------------------------------------------------------------------------------------- housekeeping

    @Test
    void theNightlyCleanUpRemovesOnlyLongExpiredTokens() throws Exception {
        String old = refreshOf(registerAndGetBody(username, "SEEKER"));
        String fresh = refreshOf(login(username));
        jdbc.update("update refresh_tokens set expires_at = ? where token_hash = ?", LocalDateTime.now().minusDays(3), sha256(old));
        refreshTokens.purgeExpired();
        assertEquals(0, jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class, sha256(old)));
        assertEquals(1, jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class, sha256(fresh)));
    }

}
