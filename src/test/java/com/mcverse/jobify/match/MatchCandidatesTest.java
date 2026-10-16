package com.mcverse.jobify.match;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mcverse.jobify.user.model.Company;
import com.mcverse.jobify.user.model.CompanyRole;
import com.mcverse.jobify.user.repository.CompanyRepository;
import com.mcverse.jobify.user.repository.EmployerRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/** GET /jobs/{id}/candidates with the fake engine: it scores a candidate 10 per skill on their profile. */
@SpringBootTest
@Import(MatchEndpointTest.FakeEngineConfig.class)
class MatchCandidatesTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private MatchEndpointTest.FakeEngine engine;
    @Autowired
    private CompanyRepository companyRepo;
    @Autowired
    private EmployerRepository employerRepo;
    @Autowired
    private TransactionTemplate tx;

    private MockMvc mvc;
    private String owner;
    private String ownerName;
    private String companyId;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        engine.down = false;
        engine.sentCandidates = null;
        ownerName = "cand_owner_" + UUID.randomUUID().toString().substring(0, 8);
        owner = registerEmployer(ownerName);
        String company = mvc.perform(post("/users/companies").header(AUTHORIZATION, owner)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Cand Co " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        companyId = JsonPath.read(company, "$.id");
    }

    private String registerEmployer(String username) throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"s3cur3Pass\",\"firstName\":\"E\","
                                + "\"lastName\":\"M\",\"role\":\"EMPLOYER\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private int createJob() throws Exception {
        String body = mvc.perform(post("/jobs").header(AUTHORIZATION, owner).contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Candidates job\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,"
                                + "\"rateType\":\"HOURLY\",\"location\":\"Berlin\",\"workMode\":\"REMOTE\","
                                + "\"employmentType\":\"FULL_TIME\",\"requiredSkills\":[\"CdSkillA\",\"CdSkillB\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.postId");
    }

    /** A seeker with this many skills on the profile (the fake scores 10 per skill). */
    private String seekerWithSkills(String username, int skills) throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"s3cur3Pass\",\"role\":\"SEEKER\","
                                + "\"firstName\":\"Cand\",\"lastName\":\"" + username + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + JsonPath.read(body, "$.token");
        for (int i = 0; i < skills; i++) {
            mvc.perform(post("/users/seekers/me/skills").header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                            .content("{\"skillName\":\"Cd" + username + i + "\",\"proficiencyLevel\":\"ADVANCED\"}"))
                    .andExpect(status().isCreated());
        }
        return token;
    }

    private String apply(String seeker, int jobId) throws Exception {
        String body = mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, seeker))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    // ---- the ranking ----

    @Test
    void theOwnerSeesTheApplicantsBestFirstWithTheirDetailsAndScores() throws Exception {
        int jobId = createJob();
        String weak = seekerWithSkills("cdweak" + System.nanoTime() % 100000, 1);
        String strong = seekerWithSkills("cdstrong" + System.nanoTime() % 100000, 3);
        String middle = seekerWithSkills("cdmid" + System.nanoTime() % 100000, 2);
        String weakApp = apply(weak, jobId);
        apply(strong, jobId);
        apply(middle, jobId);

        mvc.perform(get("/jobs/" + jobId + "/candidates").header(AUTHORIZATION, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].score").value(30))
                .andExpect(jsonPath("$.content[1].score").value(20))
                .andExpect(jsonPath("$.content[2].score").value(10))
                .andExpect(jsonPath("$.content[2].applicationId").value(weakApp))
                .andExpect(jsonPath("$.content[2].status").value("APPLIED"))
                .andExpect(jsonPath("$.content[0].applicant.name").value("Cand"))
                .andExpect(jsonPath("$.content[0].applicant.hasCv").value(false))
                .andExpect(jsonPath("$.content[0].missingSkills[0]").exists())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void whatTheMatchingServiceIsToldHasNoNamesOnlyIdsSkillsAndPreferences() throws Exception {
        int jobId = createJob();
        apply(seekerWithSkills("cdprivacy" + System.nanoTime() % 100000, 1), jobId);

        mvc.perform(get("/jobs/" + jobId + "/candidates").header(AUTHORIZATION, owner)).andExpect(status().isOk());

        var sent = engine.sentCandidates.get(0);
        assertEquals(1, sent.skills().size());
        assertNull(sent.preferences());
        // SeekerFacts has no field for a name, an email or a CV: the type itself is the guarantee
        assertEquals(3, MatchFacts.SeekerFacts.class.getRecordComponents().length);
    }

    @Test
    void withdrawnApplicationsAreNotRanked() throws Exception {
        int jobId = createJob();
        String stays = seekerWithSkills("cdstays" + System.nanoTime() % 100000, 1);
        String leaves = seekerWithSkills("cdleaves" + System.nanoTime() % 100000, 2);
        apply(stays, jobId);
        String leavingApp = apply(leaves, jobId);
        mvc.perform(delete("/applications/" + leavingApp).header(AUTHORIZATION, leaves)).andExpect(status().isNoContent());

        mvc.perform(get("/jobs/" + jobId + "/candidates").header(AUTHORIZATION, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].score").value(10));
    }

    @Test
    void pagingSlicesTheRankedList() throws Exception {
        int jobId = createJob();
        for (int i = 1; i <= 3; i++) {
            apply(seekerWithSkills("cdpage" + i + System.nanoTime() % 100000, i), jobId);
        }

        mvc.perform(get("/jobs/" + jobId + "/candidates?page=0&size=2").header(AUTHORIZATION, owner))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false));
        mvc.perform(get("/jobs/" + jobId + "/candidates?page=1&size=2").header(AUTHORIZATION, owner))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].score").value(10))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void aJobWithNoApplicantsIsAnEmptyPageAndTheEngineIsNotCalled() throws Exception {
        int jobId = createJob();

        mvc.perform(get("/jobs/" + jobId + "/candidates").header(AUTHORIZATION, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
        assertNull(engine.sentCandidates);
    }

    // ---- who may ask ----

    @Test
    void aColleagueInTheSameCompanyMayAskAnotherCompanyMayNot() throws Exception {
        int jobId = createJob();
        apply(seekerWithSkills("cdteam" + System.nanoTime() % 100000, 1), jobId);

        String colleagueName = "cand_mgr_" + UUID.randomUUID().toString().substring(0, 8);
        String colleague = registerEmployer(colleagueName);
        tx.executeWithoutResult(s -> {
            Company company = companyRepo.findById(companyId).orElseThrow();
            var employer = employerRepo.findByUsername(colleagueName).orElseThrow();
            employer.setCompany(company);
            employer.setCompanyRole(CompanyRole.MANAGER);
            employerRepo.save(employer);
        });

        mvc.perform(get("/jobs/" + jobId + "/candidates").header(AUTHORIZATION, colleague))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/jobs/" + jobId + "/candidates").header(AUTHORIZATION, bearer(mvc, "startupxyz")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("Only the company that posted this job")));
    }

    @Test
    void seekersAndAdminsAreForbiddenAndAnonymousIsUnauthorised() throws Exception {
        int jobId = createJob();
        mvc.perform(get("/jobs/" + jobId + "/candidates").header(AUTHORIZATION, bearer(mvc, "alice_s")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/jobs/" + jobId + "/candidates").header(AUTHORIZATION, bearer(mvc, "admin")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/jobs/" + jobId + "/candidates")).andExpect(status().isUnauthorized());
        assertNull(engine.sentCandidates);
    }

    @Test
    void anUnknownJobIs404AndBadPagingIs400() throws Exception {
        mvc.perform(get("/jobs/99999999/candidates").header(AUTHORIZATION, owner)).andExpect(status().isNotFound());
        int jobId = createJob();
        for (String query : new String[] {"?page=-1", "?size=0", "?size=51"}) {
            mvc.perform(get("/jobs/" + jobId + "/candidates" + query).header(AUTHORIZATION, owner))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void whenTheEngineIsDownTheAnswerIs503() throws Exception {
        int jobId = createJob();
        apply(seekerWithSkills("cddown" + System.nanoTime() % 100000, 1), jobId);
        engine.down = true;

        mvc.perform(get("/jobs/" + jobId + "/candidates").header(AUTHORIZATION, owner))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Job matching is temporarily unavailable. Please try again in a moment."));
        assertTrue(engine.down);
    }
}
