package com.mcverse.jobify.match;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.mcverse.jobify.common.exception.ServiceUnavailableException;
import com.mcverse.jobify.match.MatchFacts.JobFacts;
import com.mcverse.jobify.match.MatchFacts.MatchResult;
import com.mcverse.jobify.match.MatchFacts.PartFact;
import com.mcverse.jobify.match.MatchFacts.RankedCandidate;
import com.mcverse.jobify.match.MatchFacts.RankedJob;
import com.mcverse.jobify.match.MatchFacts.SeekerFacts;
import com.mcverse.jobify.user.model.ProficiencyLevel;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** GET /jobs/{id}/match with a fake engine that records what it was asked and answers on demand. */
@SpringBootTest
@Import(MatchEndpointTest.FakeEngineConfig.class)
class MatchEndpointTest {

    /** Records the last call; answers a fixed result, or fails when told to. */
    static class FakeEngine implements MatchEngine {
        volatile SeekerFacts seeker;
        volatile JobFacts job;
        volatile List<JobFacts> sent;
        volatile List<SeekerFacts> sentCandidates;
        volatile boolean down;
        final AtomicReference<MatchResult> answer = new AtomicReference<>();

        @Override
        public MatchResult score(SeekerFacts seeker, JobFacts job) {
            this.seeker = seeker;
            this.job = job;
            if (down) {
                throw new ServiceUnavailableException(GrpcMatchEngine.UNAVAILABLE_MESSAGE);
            }
            return answer.get() != null ? answer.get() : new MatchResult(0, List.of(), List.of(), List.of());
        }

        @Override
        public List<RankedCandidate> rankCandidates(JobFacts job, List<SeekerFacts> candidates) {
            this.job = job;
            this.sentCandidates = candidates;
            if (down) {
                throw new ServiceUnavailableException(GrpcMatchEngine.UNAVAILABLE_MESSAGE);
            }
            // a candidate scores 10 per skill on the profile; all missing skills of the job are reported missing
            return candidates.stream()
                    .map(c -> new RankedCandidate(c.seekerId(), new MatchResult(c.skills().size() * 10, List.of(),
                            job.requiredSkills().stream().map(MatchFacts.SkillRef::id).toList(), List.of())))
                    .sorted(java.util.Comparator.comparingInt((RankedCandidate r) -> r.result().score()).reversed()
                            .thenComparing(RankedCandidate::seekerId))
                    .toList();
        }

        @Override
        public List<RankedJob> rankJobs(SeekerFacts seeker, List<JobFacts> jobs) {
            this.seeker = seeker;
            this.sent = jobs;
            if (down) {
                throw new ServiceUnavailableException(GrpcMatchEngine.UNAVAILABLE_MESSAGE);
            }
            // a test scores a job by its title ("Rank 80 ..."); everything else scores 0; best first, then newest id
            return jobs.stream()
                    .map(j -> new RankedJob(j.jobId(), new MatchResult(
                            j.title().startsWith("Rank ") ? Integer.parseInt(j.title().split(" ")[1]) : 0,
                            List.of(), j.requiredSkills().stream().map(MatchFacts.SkillRef::id).toList(), List.of())))
                    .sorted(java.util.Comparator.comparingInt((RankedJob r) -> r.result().score()).reversed()
                            .thenComparing(java.util.Comparator.comparingInt(RankedJob::jobId).reversed()))
                    .toList();
        }
    }

    @TestConfiguration
    static class FakeEngineConfig {
        @Bean
        @Primary
        FakeEngine fakeEngine() {
            return new FakeEngine();
        }
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private FakeEngine engine;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        engine.down = false;
        engine.answer.set(null);
        engine.seeker = null;
        engine.job = null;
        engine.sent = null;
        engine.sentCandidates = null;
    }

    private int createJob(String body) throws Exception {
        String json = mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.postId");
    }

    private static String job(String title, String rateType, double rate, String skillsJson) {
        return "{\"jobTitle\":\"" + title + "\",\"jobDescription\":\"<p>x</p>\",\"rate\":" + rate + ",\"rateType\":\""
                + rateType + "\",\"location\":\"Berlin, Germany\",\"workMode\":\"HYBRID\",\"employmentType\":\"FULL_TIME\","
                + "\"experienceLevel\":\"SENIOR\",\"requiredSkills\":" + skillsJson + "}";
    }

    private String newSeeker(String username) throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"s3cur3Pass\",\"role\":\"SEEKER\","
                                + "\"firstName\":\"M\",\"lastName\":\"T\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private void addSkill(String seeker, String name, String level, double years) throws Exception {
        mvc.perform(post("/users/seekers/me/skills").header(AUTHORIZATION, seeker).contentType(APPLICATION_JSON)
                        .content("{\"skillName\":\"" + name + "\",\"proficiencyLevel\":\"" + level
                                + "\",\"yearsOfExperience\":" + years + "}"))
                .andExpect(status().isCreated());
    }

    // ---- the answer ----

    @Test
    void answersWithTheScoreTheSkillsByNameAndTheParts() throws Exception {
        int jobId = createJob(job("Match one", "YEARLY", 80000, "[\"MxJava\",\"MxKafka\"]"));
        String seeker = newSeeker("match_seeker1");
        addSkill(seeker, "MxJava", "ADVANCED", 4);
        // the fake answers in terms of the ids it is given
        engine.answer.set(null);
        mvc.perform(get("/jobs/" + jobId + "/match").header(AUTHORIZATION, seeker)).andExpect(status().isOk());
        List<MatchFacts.SkillRef> required = engine.job.requiredSkills();
        String javaId = required.stream().filter(s -> s.name().equals("MxJava")).findFirst().orElseThrow().id();
        String kafkaId = required.stream().filter(s -> s.name().equals("MxKafka")).findFirst().orElseThrow().id();
        engine.answer.set(new MatchResult(76, List.of(javaId), List.of(kafkaId), List.of(
                new PartFact("skills", 60, 0.5, true), new PartFact("rate", 10, 0, false))));

        mvc.perform(get("/jobs/" + jobId + "/match").header(AUTHORIZATION, seeker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.score").value(76))
                .andExpect(jsonPath("$.matchedSkills[0].id").value(javaId))
                .andExpect(jsonPath("$.matchedSkills[0].name").value("MxJava"))
                .andExpect(jsonPath("$.missingSkills[0].name").value("MxKafka"))
                .andExpect(jsonPath("$.parts.length()").value(2))
                .andExpect(jsonPath("$.parts[0].key").value("skills"))
                .andExpect(jsonPath("$.parts[0].counted").value(true))
                .andExpect(jsonPath("$.parts[1].counted").value(false));
    }

    @Test
    void anIdTheJobDoesNotKnowIsReturnedAsItsOwnName() throws Exception {
        int jobId = createJob(job("Match unknown id", "YEARLY", 80000, "[\"MxOnly\"]"));
        engine.answer.set(new MatchResult(10, List.of("no-such-id"), List.of(), List.of()));

        mvc.perform(get("/jobs/" + jobId + "/match").header(AUTHORIZATION, newSeeker("match_seeker2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedSkills[0].name").value("no-such-id"));
    }

    // ---- what the engine is told ----

    @Test
    void theEngineGetsPlainFactsWithYearlyAmounts() throws Exception {
        int jobId = createJob(job("Match facts", "HOURLY", 50, "[\"MxFacts\"]"));
        String seeker = newSeeker("match_seeker3");
        addSkill(seeker, "MxFacts", "EXPERT", 6.5);
        mvc.perform(put("/users/seekers/me/preferences").header(AUTHORIZATION, seeker).contentType(APPLICATION_JSON)
                        .content("{\"countryCodes\":[\"de\"],\"workModes\":[\"HYBRID\"],\"employmentTypes\":[\"FULL_TIME\"],"
                                + "\"minRate\":5000,\"maxRate\":8000,\"rateType\":\"MONTHLY\",\"experienceLevel\":\"SENIOR\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/jobs/" + jobId + "/match").header(AUTHORIZATION, seeker)).andExpect(status().isOk());

        JobFacts job = engine.job;
        assertEquals(jobId, job.jobId());
        assertEquals("Match facts", job.title());
        assertEquals(50 * 2080.0, job.annualRate(), 0.001);
        assertEquals("DE", job.countryCode());
        assertEquals(com.mcverse.jobify.job.model.WorkMode.HYBRID, job.workMode());
        assertEquals(com.mcverse.jobify.job.model.ExperienceLevel.SENIOR, job.experienceLevel());
        assertTrue(job.postedAtEpochSeconds() > 0);

        SeekerFacts seekerFacts = engine.seeker;
        assertEquals(1, seekerFacts.skills().size());
        assertEquals(ProficiencyLevel.EXPERT, seekerFacts.skills().get(0).level());
        assertEquals(6.5, seekerFacts.skills().get(0).years());
        assertEquals(List.of("DE"), seekerFacts.preferences().countryCodes());
        assertEquals(60000.0, seekerFacts.preferences().minAnnualRate(), 0.001);
        assertEquals(96000.0, seekerFacts.preferences().maxAnnualRate(), 0.001);
    }

    @Test
    void aSeekerWithNoPreferencesOrSkillsIsStillScored() throws Exception {
        int jobId = createJob(job("Match nothing", "YEARLY", 1, "[]"));

        mvc.perform(get("/jobs/" + jobId + "/match").header(AUTHORIZATION, newSeeker("match_seeker4")))
                .andExpect(status().isOk());

        assertNull(engine.seeker.preferences());
        assertEquals(0, engine.seeker.skills().size());
    }

    @Test
    void aContractTotalRateIsNotSentBecauseItCannotBeCompared() throws Exception {
        int jobId = createJob(job("Match total", "CONTRACT_TOTAL", 12000, "[]"));

        mvc.perform(get("/jobs/" + jobId + "/match").header(AUTHORIZATION, newSeeker("match_seeker5")))
                .andExpect(status().isOk());

        assertNull(engine.job.annualRate());
    }

    // ---- not found, forbidden, unavailable ----

    @Test
    void unknownAndClosedJobsAre404() throws Exception {
        String seeker = newSeeker("match_seeker6");
        mvc.perform(get("/jobs/99999999/match").header(AUTHORIZATION, seeker)).andExpect(status().isNotFound());

        int jobId = createJob(job("Match closed", "YEARLY", 1, "[]"));
        mvc.perform(patch("/jobs/" + jobId + "/available").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                .contentType(APPLICATION_JSON).content("{\"available\":false}")).andExpect(status().isOk());

        mvc.perform(get("/jobs/" + jobId + "/match").header(AUTHORIZATION, seeker)).andExpect(status().isNotFound());
        assertNull(engine.job, "the engine is not asked about a job that does not exist for the seeker");
    }

    @Test
    void employersAndAdminsAreForbiddenAndAnonymousIsUnauthorised() throws Exception {
        int jobId = createJob(job("Match roles", "YEARLY", 1, "[]"));
        for (String user : new String[] {"techcorp", "admin"}) {
            mvc.perform(get("/jobs/" + jobId + "/match").header(AUTHORIZATION, bearer(mvc, user)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/jobs/" + jobId + "/match")).andExpect(status().isUnauthorized());
        assertNull(engine.job);
    }

    @Test
    void whenTheEngineIsDownTheAnswerIs503WithTheFriendlyMessage() throws Exception {
        int jobId = createJob(job("Match down", "YEARLY", 1, "[]"));
        engine.down = true;

        mvc.perform(get("/jobs/" + jobId + "/match").header(AUTHORIZATION, newSeeker("match_seeker7")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Job matching is temporarily unavailable. Please try again in a moment."));
    }

    @Test
    void therestOfTheApiKeepsWorkingWhileTheEngineIsDown() throws Exception {
        engine.down = true;
        mvc.perform(get("/jobs")).andExpect(status().isOk());
    }
}
