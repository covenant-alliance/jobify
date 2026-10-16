package com.mcverse.jobify.match;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** The default configuration (matching.enabled=false): the real wiring answers 503, and nothing else is affected. */
@SpringBootTest
class MatchDisabledEndpointTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void theMatchEndpointAnswers503WithTheFriendlyMessageAndTheRestWorks() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String created = mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Disabled match\",\"jobDescription\":\"<p>x</p>\",\"rate\":1,"
                                + "\"rateType\":\"YEARLY\",\"location\":\"Berlin\",\"workMode\":\"REMOTE\","
                                + "\"employmentType\":\"FULL_TIME\"}"))
                .andReturn().getResponse().getContentAsString();
        int jobId = JsonPath.read(created, "$.postId");

        mvc.perform(get("/jobs/" + jobId + "/match").header(AUTHORIZATION, bearer(mvc, "alice_s")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Job matching is temporarily unavailable. Please try again in a moment."));

        mvc.perform(get("/jobs/" + jobId)).andExpect(status().isOk());
    }
}
