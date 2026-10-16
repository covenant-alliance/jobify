package com.mcverse.jobify.match;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mcverse.jobify.match.MatchFacts.JobFacts;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** The SQL narrowing cap: only the newest matching.max-candidates jobs are ever sent to the engine. */
@SpringBootTest(properties = "matching.max-candidates=3")
@Import(MatchEndpointTest.FakeEngineConfig.class)
class MatchCandidateLimitTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private MatchEndpointTest.FakeEngine engine;

    @Test
    void onlyTheNewestJobsUpToTheLimitAreSentToTheEngine() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        List<Integer> created = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String json = mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                            .contentType(APPLICATION_JSON)
                            .content("{\"jobTitle\":\"Limit " + i + "\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,"
                                    + "\"rateType\":\"HOURLY\",\"location\":\"Berlin, Germany\",\"workMode\":\"HYBRID\","
                                    + "\"employmentType\":\"FULL_TIME\"}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            created.add(JsonPath.read(json, "$.postId"));
        }
        String registered = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"rec_limit\",\"password\":\"s3cur3Pass\",\"role\":\"SEEKER\","
                                + "\"firstName\":\"L\",\"lastName\":\"M\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        mvc.perform(get("/jobs/recommended?size=50").header(AUTHORIZATION, "Bearer " + JsonPath.read(registered, "$.token")))
                .andExpect(status().isOk());

        List<Integer> sent = engine.sent.stream().map(JobFacts::jobId).sorted().toList();
        assertEquals(3, sent.size(), "never more than the limit");
        assertEquals(created.subList(2, 5), sent, "the three newest jobs");
        assertTrue(engine.sent.size() <= 3);
    }
}
