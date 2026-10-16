package com.mcverse.jobify.match;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * "A new job fits you" notifications. The fake engine scores a candidate 10 per skill on their profile, so a seeker
 * with six skills scores 60 and one with one skill scores 10; the threshold here is 50.
 */
@SpringBootTest(properties = {"matching.alerts.enabled=true", "matching.alerts.threshold=50"})
@Import(MatchEndpointTest.FakeEngineConfig.class)
class MatchNotifierTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private MatchEndpointTest.FakeEngine engine;
    @Autowired
    private MatchNotifier notifier;

    private MockMvc mvc;
    private String employer;
    private String tag;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        engine.down = false;
        engine.sentCandidates = null;
        employer = bearer(mvc, "techcorp");
        tag = UUID.randomUUID().toString().substring(0, 6);
    }

    private int postJob(String location, String workMode, String skillsJson) throws Exception {
        String body = mvc.perform(post("/jobs").header(AUTHORIZATION, employer).contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Alert " + tag + "\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,"
                                + "\"rateType\":\"HOURLY\",\"location\":\"" + location + "\",\"workMode\":\"" + workMode
                                + "\",\"employmentType\":\"FULL_TIME\",\"requiredSkills\":" + skillsJson + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.postId");
    }

    /** Registers a seeker with the given skill names. */
    private String seeker(String name, String... skills) throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"password\":\"s3cur3Pass\",\"role\":\"SEEKER\","
                                + "\"firstName\":\"N\",\"lastName\":\"T\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + JsonPath.read(body, "$.token");
        for (String skill : skills) {
            mvc.perform(post("/users/seekers/me/skills").header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                            .content("{\"skillName\":\"" + skill + "\",\"proficiencyLevel\":\"ADVANCED\"}"))
                    .andExpect(status().isCreated());
        }
        return token;
    }

    private int matchNotifications(String token) throws Exception {
        String body = mvc.perform(get("/notifications").header(AUTHORIZATION, token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> types = JsonPath.read(body, "$[?(@.type == 'MATCH')].title");
        return types.stream().filter(t -> t.contains(tag)).toList().size();
    }

    private String[] skills(String shared, int extra) {
        String[] all = new String[1 + extra];
        all[0] = shared;
        for (int i = 1; i <= extra; i++) {
            all[i] = "Ex" + tag + i;
        }
        return all;
    }

    // ---- who is told ----

    @Test
    void onlyASeekerAtOrAboveTheThresholdIsTold() throws Exception {
        String a = "NtA" + tag;
        String strong = seeker("al_strong_" + tag, skills(a, 5));   // 6 skills -> 60
        String weak = seeker("al_weak_" + tag, a);                  // 1 skill -> 10
        String unrelated = seeker("al_other_" + tag, skills("Unrelated" + tag, 8)); // shares no skill: never scored
        int jobId = postJob("Berlin, Germany", "HYBRID", "[\"" + a + "\"]");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(1, matchNotifications(strong)));

        assertEquals(0, matchNotifications(weak), "below the threshold");
        assertEquals(0, matchNotifications(unrelated), "shares no skill with the job");
        assertEquals(2, engine.sentCandidates.size(), "only seekers who share a skill are sent to the engine");
        String body = mvc.perform(get("/notifications").header(AUTHORIZATION, strong)).andReturn().getResponse().getContentAsString();
        assertEquals(jobId, ((Number) ((List<?>) JsonPath.read(body, "$[?(@.type == 'MATCH')].jobId")).get(0)).intValue());
    }

    @Test
    void aSeekerWhoSwitchedMatchAlertsOffIsNotToldButOthersAre() throws Exception {
        String a = "NtB" + tag;
        String off = seeker("al_off_" + tag, skills(a, 5));
        String on = seeker("al_on_" + tag, skills(a, 5));
        mvc.perform(put("/users/me/preferences").header(AUTHORIZATION, off).contentType(APPLICATION_JSON)
                .content("{\"notifications\":{\"MATCH\":false}}")).andExpect(status().isOk());

        postJob("Berlin, Germany", "HYBRID", "[\"" + a + "\"]");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(1, matchNotifications(on)));
        assertEquals(0, matchNotifications(off));
    }

    @Test
    void aSeekerWhoAlreadyAppliedIsNotToldAndCountryPreferencesAreRespected() throws Exception {
        String a = "NtC" + tag;
        String french = seeker("al_fr_" + tag, skills(a, 5));
        String german = seeker("al_de_" + tag, skills(a, 5));
        String anywhere = seeker("al_any_" + tag, skills(a, 5));
        mvc.perform(put("/users/seekers/me/preferences").header(AUTHORIZATION, french).contentType(APPLICATION_JSON)
                .content("{\"countryCodes\":[\"FR\"]}")).andExpect(status().isOk());
        mvc.perform(put("/users/seekers/me/preferences").header(AUTHORIZATION, german).contentType(APPLICATION_JSON)
                .content("{\"countryCodes\":[\"DE\"]}")).andExpect(status().isOk());

        postJob("Berlin, Germany", "ONSITE", "[\"" + a + "\"]");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(1, matchNotifications(german)));
        assertEquals(1, matchNotifications(anywhere), "no country saved means any country");
        assertEquals(0, matchNotifications(french), "wants France, the job is on-site in Germany");
    }

    @Test
    void aRemoteJobReachesSeekersWhateverCountryTheyChose() throws Exception {
        String a = "NtD" + tag;
        String french = seeker("al_frr_" + tag, skills(a, 5));
        mvc.perform(put("/users/seekers/me/preferences").header(AUTHORIZATION, french).contentType(APPLICATION_JSON)
                .content("{\"countryCodes\":[\"FR\"]}")).andExpect(status().isOk());

        postJob("Berlin, Germany", "REMOTE", "[\"" + a + "\"]");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(1, matchNotifications(french)));
    }

    @Test
    void aJobWithoutRequiredSkillsNotifiesNobody() throws Exception {
        seeker("al_noskill_" + tag, skills("NtE" + tag, 5));
        int jobId = postJob("Berlin, Germany", "HYBRID", "[]");

        assertEquals(0, notifier.notifyFor(jobId));
        assertNull(engine.sentCandidates);
    }

    // ---- failure ----

    @Test
    void whenMatchingIsDownThePostSucceedsAndNobodyIsTold() throws Exception {
        String a = "NtF" + tag;
        String seeker = seeker("al_down_" + tag, skills(a, 5));
        engine.down = true;

        int jobId = postJob("Berlin, Germany", "HYBRID", "[\"" + a + "\"]"); // 201 is asserted inside

        await().atMost(Duration.ofSeconds(10)).until(() -> engine.sentCandidates != null);
        assertEquals(0, matchNotifications(seeker));
        assertEquals(0, notifier.notifyFor(jobId));
    }

    @Test
    void aClosedOrMissingJobNotifiesNobody() {
        assertEquals(0, notifier.notifyFor(99999999));
    }
}
