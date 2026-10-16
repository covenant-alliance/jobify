package com.mcverse.jobify.match;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Alerts on, but with a small cap: only that many seekers are ever scored for one new job. */
@SpringBootTest(properties = {"matching.alerts.enabled=true", "matching.alerts.max-seekers=2"})
@Import(MatchEndpointTest.FakeEngineConfig.class)
class MatchNotifierSettingsTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private MatchEndpointTest.FakeEngine engine;
    @Autowired
    private MatchNotifier notifier;

    @Test
    void atMostTheConfiguredNumberOfSeekersIsScored() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String tag = UUID.randomUUID().toString().substring(0, 6);
        String skill = "Cap" + tag;
        for (int i = 0; i < 4; i++) {
            String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                            .content("{\"username\":\"cap_" + i + tag + "\",\"password\":\"s3cur3Pass\",\"role\":\"SEEKER\","
                                    + "\"firstName\":\"C\",\"lastName\":\"P\"}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            mvc.perform(post("/users/seekers/me/skills").header(AUTHORIZATION, "Bearer " + JsonPath.read(body, "$.token"))
                    .contentType(APPLICATION_JSON)
                    .content("{\"skillName\":\"" + skill + "\",\"proficiencyLevel\":\"ADVANCED\"}")).andExpect(status().isCreated());
        }
        String job = mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp")).contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Cap " + tag + "\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,"
                                + "\"rateType\":\"HOURLY\",\"location\":\"Berlin\",\"workMode\":\"REMOTE\","
                                + "\"employmentType\":\"FULL_TIME\",\"requiredSkills\":[\"" + skill + "\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        engine.sentCandidates = null;

        notifier.notifyFor(JsonPath.read(job, "$.postId"));

        assertEquals(2, engine.sentCandidates.size());
    }
}
