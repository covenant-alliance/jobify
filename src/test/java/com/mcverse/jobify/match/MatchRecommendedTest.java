package com.mcverse.jobify.match;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * GET /jobs/recommended with the same fake engine as MatchEndpointTest. The fake scores a job by its title
 * ("Rank 80 ..." scores 80, anything else 0), so each test controls the order of its own jobs. The database is shared
 * between tests, so these assert the order of their own jobs, never "the first job overall".
 */
@SpringBootTest
@Import(MatchEndpointTest.FakeEngineConfig.class)
class MatchRecommendedTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private MatchEndpointTest.FakeEngine engine;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        engine.down = false;
        engine.sent = null;
        engine.sentCandidates = null;
    }

    private int createJob(String title, String location, String workMode) throws Exception {
        String json = mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"" + title + "\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,"
                                + "\"rateType\":\"HOURLY\",\"location\":\"" + location + "\",\"workMode\":\"" + workMode
                                + "\",\"employmentType\":\"FULL_TIME\",\"requiredSkills\":[\"RcSkill\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.postId");
    }

    private String newSeeker(String username) throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"s3cur3Pass\",\"role\":\"SEEKER\","
                                + "\"firstName\":\"R\",\"lastName\":\"C\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private List<Integer> recommendedIds(String seeker, String query) throws Exception {
        String body = mvc.perform(get("/jobs/recommended" + query).header(AUTHORIZATION, seeker))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.content[*].job.postId");
    }

    @Test
    void bestScoreFirstWithTheScoreAndTheSkillNames() throws Exception {
        int low = createJob("Rank 50 low", "Berlin, Germany", "HYBRID");
        int top = createJob("Rank 95 top", "Berlin, Germany", "HYBRID");
        int mid = createJob("Rank 80 mid", "Berlin, Germany", "HYBRID");
        String seeker = newSeeker("rec_order");

        List<Integer> ids = recommendedIds(seeker, "?size=50");
        assertTrue(ids.indexOf(top) < ids.indexOf(mid) && ids.indexOf(mid) < ids.indexOf(low), "ids were " + ids);

        mvc.perform(get("/jobs/recommended?size=50").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$.content[?(@.job.postId == " + top + ")].score").value(95))
                .andExpect(jsonPath("$.content[?(@.job.postId == " + top + ")].job.jobTitle").value("Rank 95 top"))
                .andExpect(jsonPath("$.content[?(@.job.postId == " + top + ")].job.companyName").value("TechCorp Ltd"));
    }

    @Test
    void pagingSlicesTheRankedList() throws Exception {
        createJob("Rank 71 a", "Berlin, Germany", "HYBRID");
        createJob("Rank 72 b", "Berlin, Germany", "HYBRID");
        String seeker = newSeeker("rec_paging");

        String first = mvc.perform(get("/jobs/recommended?page=0&size=1").header(AUTHORIZATION, seeker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.last").value(false))
                .andReturn().getResponse().getContentAsString();
        long total = ((Number) JsonPath.read(first, "$.totalElements")).longValue();
        assertTrue(total >= 2);
        assertEquals((int) total, (int) JsonPath.read(first, "$.totalPages"));

        String second = mvc.perform(get("/jobs/recommended?page=1&size=1").header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$.content.length()").value(1)).andReturn().getResponse().getContentAsString();
        assertFalse(((Number) JsonPath.read(first, "$.content[0].job.postId")).intValue()
                == ((Number) JsonPath.read(second, "$.content[0].job.postId")).intValue());

        mvc.perform(get("/jobs/recommended?page=100000&size=50").header(AUTHORIZATION, seeker))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void jobsAlreadyAppliedToAndClosedJobsAreLeftOut() throws Exception {
        int applied = createJob("Rank 60 applied", "Berlin, Germany", "HYBRID");
        int closed = createJob("Rank 61 closed", "Berlin, Germany", "HYBRID");
        int open = createJob("Rank 62 open", "Berlin, Germany", "HYBRID");
        mvc.perform(patch("/jobs/" + closed + "/available").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                .contentType(APPLICATION_JSON).content("{\"available\":false}")).andExpect(status().isOk());
        String seeker = newSeeker("rec_applied");
        mvc.perform(post("/jobs/" + applied + "/apply").header(AUTHORIZATION, seeker)).andExpect(status().isCreated());

        List<Integer> ids = recommendedIds(seeker, "?size=50");

        assertTrue(ids.contains(open));
        assertFalse(ids.contains(applied), "already applied to");
        assertFalse(ids.contains(closed), "closed");
        // another seeker still sees the job she applied to
        assertTrue(recommendedIds(newSeeker("rec_applied_other"), "?size=50").contains(applied));
    }

    @Test
    void savedCountriesNarrowTheListButRemoteJobsStay() throws Exception {
        int berlin = createJob("Rank 63 berlin", "Berlin, Germany", "ONSITE");
        int paris = createJob("Rank 64 paris", "Paris, France", "ONSITE");
        int parisRemote = createJob("Rank 65 paris remote", "Paris, France", "REMOTE");
        String seeker = newSeeker("rec_country");
        mvc.perform(put("/users/seekers/me/preferences").header(AUTHORIZATION, seeker).contentType(APPLICATION_JSON)
                .content("{\"countryCodes\":[\"DE\"]}")).andExpect(status().isOk());

        List<Integer> ids = recommendedIds(seeker, "?size=50");

        assertTrue(ids.contains(berlin));
        assertTrue(ids.contains(parisRemote), "remote jobs are not tied to a country");
        assertFalse(ids.contains(paris), "an on-site job in another country is not recommended");
    }

    @Test
    void aSeekerWithNothingSavedStillGetsAnAnswer() throws Exception {
        int job = createJob("Rank 66 anyone", "Paris, France", "ONSITE");

        List<Integer> ids = recommendedIds(newSeeker("rec_nothing"), "?size=50");

        assertTrue(ids.contains(job), "without saved countries every country is allowed");
        assertEquals(0, engine.seeker.skills().size());
        assertEquals(null, engine.seeker.preferences());
    }

    @Test
    void thePageParametersAreChecked() throws Exception {
        String seeker = newSeeker("rec_params");
        for (String query : new String[] {"?page=-1", "?size=0", "?size=51"}) {
            mvc.perform(get("/jobs/recommended" + query).header(AUTHORIZATION, seeker))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString(query.contains("page") ? "page" : "size")));
        }
    }

    @Test
    void employersAndAdminsAreForbiddenAndAnonymousIsUnauthorised() throws Exception {
        for (String user : new String[] {"techcorp", "admin"}) {
            mvc.perform(get("/jobs/recommended").header(AUTHORIZATION, bearer(mvc, user)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/jobs/recommended")).andExpect(status().isUnauthorized());
    }

    @Test
    void whenTheEngineIsDownTheAnswerIs503AndNoFallbackListIsShown() throws Exception {
        createJob("Rank 67 down", "Berlin, Germany", "HYBRID");
        engine.down = true;

        mvc.perform(get("/jobs/recommended").header(AUTHORIZATION, newSeeker("rec_down")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Job matching is temporarily unavailable. Please try again in a moment."));
    }
}
