package com.mcverse.jobify.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /auth/refresh is rate limited like login and register: guessing refresh tokens must not be free. */
@SpringBootTest(properties = {"app.security.auth-rate-limit.max-requests=3", "app.security.auth-rate-limit.window-seconds=60"})
class RefreshRateLimitIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void theFourthRefreshFromOneAddressIsThrottled() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON).content("{\"refreshToken\":\"guess" + i + "\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON).content("{\"refreshToken\":\"guess4\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.success").value(false));
    }
}
