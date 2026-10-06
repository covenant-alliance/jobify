package com.mcverse.jobify.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The OpenAPI JSON is what the front end generates clients from, so it must be public and complete. */
@SpringBootTest
class OpenApiSpecIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void specIsPublicAndListsEveryAreaOfTheApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths", hasKey("/auth/login")))
                .andExpect(jsonPath("$.paths", hasKey("/jobs")))
                .andExpect(jsonPath("$.paths", hasKey("/jobs/mine")))
                .andExpect(jsonPath("$.paths", hasKey("/jobs/{id}/apply")))
                .andExpect(jsonPath("$.paths", hasKey("/jobs/{id}/save")))
                .andExpect(jsonPath("$.paths", hasKey("/jobs/saved")))
                .andExpect(jsonPath("$.paths", hasKey("/applications/me")))
                .andExpect(jsonPath("$.paths", hasKey("/applications/{id}/status")))
                .andExpect(jsonPath("$.paths", hasKey("/companies/{id}")))
                .andExpect(jsonPath("$.paths", hasKey("/account/password")))
                .andExpect(jsonPath("$.paths", hasKey("/admin/deletion-requests")))
                .andExpect(jsonPath("$.paths", hasKey("/admin/content")))
                .andExpect(jsonPath("$.paths", hasKey("/users/seekers/me/skills")));
    }
}
