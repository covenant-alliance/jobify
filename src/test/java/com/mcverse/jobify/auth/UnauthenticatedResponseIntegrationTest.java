package com.mcverse.jobify.auth;

import com.mcverse.jobify.config.JwtConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Base64;
import java.util.Date;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A request without a valid session is answered 401 in the standard error envelope, because the front end logs the
 * user out on 401. A signed-in user who is not allowed gets 403 with a readable message instead (issue #50).
 */
@SpringBootTest
class UnauthenticatedResponseIntegrationTest {

    private static final String MESSAGE = "Your session has expired or is not valid. Please sign in again.";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtConfig jwtConfig;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String token(String username, long issuedMillisAgo, long lifetimeMillis) {
        long issued = System.currentTimeMillis() - issuedMillisAgo;
        return "Bearer " + Jwts.builder().subject(username)
                .issuedAt(new Date(issued)).expiration(new Date(issued + lifetimeMillis))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtConfig.getSecret()))).compact();
    }

    private void assertSessionEnded(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/json")))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(MESSAGE))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void noTokenIs401WithTheStandardEnvelope() throws Exception {
        assertSessionEnded(mvc.perform(get("/users/seekers/me")));
    }

    @Test
    void aMalformedTokenIs401() throws Exception {
        assertSessionEnded(mvc.perform(get("/users/seekers/me").header("Authorization", "Bearer not.a.jwt")));
        assertSessionEnded(mvc.perform(get("/users/seekers/me").header("Authorization", "Bearer ")));
    }

    @Test
    void anExpiredTokenIs401() throws Exception {
        String expired = token("alice_s", 2 * 24 * 3600_000L, 24 * 3600_000L);
        assertSessionEnded(mvc.perform(get("/users/seekers/me").header("Authorization", expired)));
    }

    @Test
    void aTokenSignedWithAnotherKeyIs401() throws Exception {
        String forged = "Bearer " + Jwts.builder().subject("alice_s")
                .expiration(new Date(System.currentTimeMillis() + 3600_000L))
                .signWith(Keys.hmacShaKeyFor(new byte[32])).compact();
        assertSessionEnded(mvc.perform(get("/users/seekers/me").header("Authorization", forged)));
    }

    @Test
    void aValidTokenForAnAccountThatNoLongerExistsIs401NotAServerError() throws Exception {
        String ghost = token("deleted_account_" + System.nanoTime(), 0, 3600_000L);
        assertSessionEnded(mvc.perform(get("/users/seekers/me").header("Authorization", ghost)));
    }

    @Test
    void aValidTokenStillWorks() throws Exception {
        mvc.perform(get("/users/seekers/me").header("Authorization", bearer(mvc, "alice_s")))
                .andExpect(status().isOk());
    }

    @Test
    void theWrongRoleIs403WithAReadableMessageNotAnEmptyBody() throws Exception {
        mvc.perform(get("/admin/stats").header("Authorization", bearer(mvc, "alice_s")))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/json")))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("You do not have permission to do that."))
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/jobs/mine").header("Authorization", bearer(mvc, "alice_s")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void publicPagesIgnoreAStaleTokenInsteadOfBreaking() throws Exception {
        String expired = token("alice_s", 2 * 24 * 3600_000L, 24 * 3600_000L);
        mvc.perform(get("/jobs").header("Authorization", expired)).andExpect(status().isOk());
        mvc.perform(get("/content").header("Authorization", "Bearer garbage")).andExpect(status().isOk());
    }

    @Test
    void the401CarriesCorsHeadersSoTheBrowserLetsTheFrontEndSeeIt() throws Exception {
        mvc.perform(get("/users/seekers/me").header("Origin", "http://localhost:3000"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(get("/admin/stats").header("Origin", "http://localhost:3000")
                        .header("Authorization", bearer(mvc, "alice_s")))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void aWrongPasswordAtLoginIsStill401WithItsOwnMessage() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/login")
                        .contentType("application/json").content("{\"username\":\"alice_s\",\"password\":\"wrong-pass-1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }
}
