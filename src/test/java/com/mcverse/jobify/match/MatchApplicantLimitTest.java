package com.mcverse.jobify.match;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** More applicants than matching.max-applicants: only the newest applications are ranked. */
@SpringBootTest(properties = "matching.max-applicants=2")
@Import(MatchEndpointTest.FakeEngineConfig.class)
class MatchApplicantLimitTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private MatchEndpointTest.FakeEngine engine;

    @Test
    void onlyTheNewestApplicationsUpToTheLimitAreRanked() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String employer = bearer(mvc, "financegroup");
        String job = mvc.perform(post("/jobs").header(AUTHORIZATION, employer).contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Limit applicants\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,"
                                + "\"rateType\":\"HOURLY\",\"location\":\"Berlin\",\"workMode\":\"REMOTE\","
                                + "\"employmentType\":\"FULL_TIME\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int jobId = JsonPath.read(job, "$.postId");
        String[] ids = new String[3];
        for (int i = 0; i < 3; i++) {
            String name = "applim" + i + System.nanoTime() % 100000;
            String registered = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                            .content("{\"username\":\"" + name + "\",\"password\":\"s3cur3Pass\",\"role\":\"SEEKER\","
                                    + "\"firstName\":\"A\",\"lastName\":\"L\"}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            String token = "Bearer " + JsonPath.read(registered, "$.token");
            ids[i] = JsonPath.read(mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, token))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
            Thread.sleep(5);
        }

        mvc.perform(get("/jobs/" + jobId + "/candidates").header(AUTHORIZATION, employer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        assertEquals(2, engine.sentCandidates.size(), "never more than the limit");
    }
}
