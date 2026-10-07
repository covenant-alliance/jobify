package com.mcverse.jobify.user;

import com.jayway.jsonpath.JsonPath;
import com.mcverse.jobify.user.model.Company;
import com.mcverse.jobify.user.model.CompanyRole;
import com.mcverse.jobify.user.repository.CompanyRepository;
import com.mcverse.jobify.user.repository.EmployerRepository;
import com.mcverse.jobify.user.service.CompanyRoleBackfill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Company teams (#71): several employer accounts in one company manage its jobs, applications and statistics. There is
 * no invitation route yet (#72), so the tests put the second person in the company directly.
 */
@SpringBootTest
class CompanyTeamIntegrationTest {

    private static final String PASSWORD = "Team-Pass-4321";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    @Autowired private WebApplicationContext context;
    @Autowired private EmployerRepository employerRepo;
    @Autowired private CompanyRepository companyRepo;
    @Autowired private TransactionTemplate tx;
    @Autowired private CompanyRoleBackfill backfill;

    private MockMvc mvc;
    private String ownerName, managerName, outsiderName;
    private String owner, manager, outsider, companyId;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        ownerName = name("tm_owner");
        managerName = name("tm_mgr");
        outsiderName = name("tm_out");
        owner = register(ownerName);
        manager = register(managerName);
        outsider = register(outsiderName);
        String company = mvc.perform(post("/users/companies").header(AUTHORIZATION, owner)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Team Co " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        companyId = JsonPath.read(company, "$.id");
        mvc.perform(post("/users/companies").header(AUTHORIZATION, outsider).contentType(APPLICATION_JSON)
                .content("{\"name\":\"Other Co " + UUID.randomUUID() + "\"}")).andExpect(status().isCreated());
        join(managerName, CompanyRole.MANAGER);
    }

    private static String name(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String register(String username) throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(
                        "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"firstName\":\"T\","
                                + "\"lastName\":\"M\",\"role\":\"EMPLOYER\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    /** Connects an employer to the company directly (the invitation flow is #72). */
    private void join(String username, CompanyRole role) {
        tx.executeWithoutResult(s -> {
            Company company = companyRepo.findById(companyId).orElseThrow();
            var employer = employerRepo.findByUsername(username).orElseThrow();
            employer.setCompany(company);
            employer.setCompanyRole(role);
            employerRepo.save(employer);
        });
    }

    private int createJob(String token, String title) throws Exception {
        String body = mvc.perform(post("/jobs").header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                        .content(jobJson(title))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.postId");
    }

    private static String jobJson(String title) {
        return "{\"jobTitle\":\"" + title + "\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,\"rateType\":\"HOURLY\","
                + "\"location\":\"Berlin\",\"workMode\":\"REMOTE\",\"employmentType\":\"FULL_TIME\"}";
    }

    private String seeker() throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(
                        "{\"username\":\"" + name("tm_sk") + "\",\"password\":\"" + PASSWORD
                                + "\",\"firstName\":\"S\",\"lastName\":\"K\",\"role\":\"SEEKER\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private String apply(String seeker, int jobId) throws Exception {
        String body = mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, seeker))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    // ------------------------------------------------------------------------------------------- roles

    @Test
    void everyoneSeesTheirRoleInTheirCompany() throws Exception {
        mvc.perform(get("/users/employers/me").header(AUTHORIZATION, owner))
                .andExpect(jsonPath("$.companyRole").value("OWNER"));
        mvc.perform(get("/users/employers/me").header(AUTHORIZATION, manager))
                .andExpect(jsonPath("$.companyRole").value("MANAGER"))
                .andExpect(jsonPath("$.company.id").value(companyId));
        // an employer without a company has no role
        String lone = register(name("tm_lone"));
        mvc.perform(get("/users/employers/me").header(AUTHORIZATION, lone))
                .andExpect(jsonPath("$.companyRole").doesNotExist());
        // seeded employers (set up before roles existed) are owners of their companies
        mvc.perform(get("/users/employers/me").header(AUTHORIZATION, bearer(mvc, "techcorp")))
                .andExpect(jsonPath("$.companyRole").value("OWNER"));
    }

    // -------------------------------------------------------------------------------------------- jobs

    @Test
    void teammatesSeeEachOthersJobsAndOutsidersSeeNeither() throws Exception {
        int byOwner = createJob(owner, "Owner's job");
        int byManager = createJob(manager, "Manager's job");
        for (String who : new String[] {owner, manager}) {
            mvc.perform(get("/jobs/mine").header(AUTHORIZATION, who)).andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].postId", hasItem(byOwner)))
                    .andExpect(jsonPath("$[*].postId", hasItem(byManager)));
        }
        mvc.perform(get("/jobs/mine").header(AUTHORIZATION, outsider))
                .andExpect(jsonPath("$[*].postId", not(hasItem(byOwner))))
                .andExpect(jsonPath("$[*].postId", not(hasItem(byManager))));
    }

    @Test
    void aManagerEditsClosesAndAddsPicturesToTheOwnersJobButAnOutsiderCannot() throws Exception {
        int id = createJob(owner, "Shared job");
        mvc.perform(put("/jobs/" + id).header(AUTHORIZATION, manager).contentType(APPLICATION_JSON)
                        .content(jobJson("Edited by manager")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jobTitle").value("Edited by manager"))
                .andExpect(jsonPath("$.employerUsername").value(ownerName)); // still posted by the owner
        mvc.perform(patch("/jobs/" + id + "/available").header(AUTHORIZATION, manager)
                .contentType(APPLICATION_JSON).content("{\"available\":false}")).andExpect(status().isOk());
        mvc.perform(multipart("/jobs/" + id + "/images")
                .file(new MockMultipartFile("file", "a.png", "image/png", PNG)).header(AUTHORIZATION, manager))
                .andExpect(status().isCreated());

        mvc.perform(put("/jobs/" + id).header(AUTHORIZATION, outsider).contentType(APPLICATION_JSON)
                        .content(jobJson("Hijack"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only change job postings that you created."));
        mvc.perform(patch("/jobs/" + id + "/available").header(AUTHORIZATION, outsider)
                .contentType(APPLICATION_JSON).content("{\"available\":true}")).andExpect(status().isForbidden());
        mvc.perform(multipart("/jobs/" + id + "/images")
                .file(new MockMultipartFile("file", "a.png", "image/png", PNG)).header(AUTHORIZATION, outsider))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------------------------ applications

    @Test
    void aManagerReviewsApplicantsToTheOwnersJobAndAnOutsiderCannot() throws Exception {
        int id = createJob(owner, "Reviewed together");
        String seeker = seeker();
        String applicationId = apply(seeker, id);

        mvc.perform(get("/jobs/" + id + "/applications").header(AUTHORIZATION, manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(applicationId));
        mvc.perform(put("/applications/" + applicationId + "/status").header(AUTHORIZATION, manager)
                        .contentType(APPLICATION_JSON).content("{\"status\":\"IN_REVIEW\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_REVIEW"));

        mvc.perform(get("/jobs/" + id + "/applications").header(AUTHORIZATION, outsider))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only view applications for your own jobs."));
        mvc.perform(put("/applications/" + applicationId + "/status").header(AUTHORIZATION, outsider)
                        .contentType(APPLICATION_JSON).content("{\"status\":\"INTERVIEW\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void statisticsCoverEveryJobOfTheCompany() throws Exception {
        int byOwner = createJob(owner, "Stats owner");
        int byManager = createJob(manager, "Stats manager");
        apply(seeker(), byOwner);
        apply(seeker(), byManager);
        for (String who : new String[] {owner, manager}) {
            mvc.perform(get("/employers/me/stats").header(AUTHORIZATION, who)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobs.total").value(2))
                    .andExpect(jsonPath("$.perJob.length()").value(2))
                    .andExpect(jsonPath("$.applications.total").value(2));
        }
        mvc.perform(get("/employers/me/stats").header(AUTHORIZATION, outsider))
                .andExpect(jsonPath("$.jobs.total").value(0));
    }

    // ----------------------------------------------------------------------------- company and logo

    @Test
    void onlyTheOwnerChangesTheCompanyAndItsLogo() throws Exception {
        mvc.perform(put("/users/companies/" + companyId).header(AUTHORIZATION, manager)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Renamed by manager\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only the company owner can do that."));
        mvc.perform(multipart("/users/companies/" + companyId + "/logo")
                .file(new MockMultipartFile("file", "l.png", "image/png", PNG)).header(AUTHORIZATION, manager))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only the company owner can do that."));
        mvc.perform(delete("/users/companies/" + companyId + "/logo").header(AUTHORIZATION, manager))
                .andExpect(status().isForbidden());

        mvc.perform(put("/users/companies/" + companyId).header(AUTHORIZATION, owner)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Renamed by owner\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed by owner"));
        mvc.perform(multipart("/users/companies/" + companyId + "/logo")
                .file(new MockMultipartFile("file", "l.png", "image/png", PNG)).header(AUTHORIZATION, owner))
                .andExpect(status().isOk());
        // an outsider is refused as before
        mvc.perform(put("/users/companies/" + companyId).header(AUTHORIZATION, outsider)
                .contentType(APPLICATION_JSON).content("{\"name\":\"x\"}")).andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------------------------- leaving

    private void deleteAccount(String token, String username) throws Exception {
        String req = mvc.perform(post("/account/deletion-request").header(AUTHORIZATION, token)
                        .contentType(APPLICATION_JSON).content("{\"reason\":\"leaving\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/admin/deletion-requests/" + (String) JsonPath.read(req, "$.id") + "/approve")
                .header(AUTHORIZATION, bearer(mvc, "admin"))).andExpect(status().isOk());
        assertEquals(java.util.Optional.empty(), employerRepo.findByUsername(username));
    }

    @Test
    void whenAManagerLeavesTheirJobsAndApplicantsStayWithTheCompany() throws Exception {
        int id = createJob(manager, "Posted by the leaver");
        String applicationId = apply(seeker(), id);

        deleteAccount(manager, managerName);

        mvc.perform(get("/jobs/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.employerUsername").value(ownerName));
        mvc.perform(get("/jobs/" + id + "/applications").header(AUTHORIZATION, owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(applicationId));
    }

    @Test
    void whenTheOnlyOwnerLeavesTheLongestServingManagerBecomesOwner() throws Exception {
        int id = createJob(owner, "Posted by the owner");
        deleteAccount(owner, ownerName);

        mvc.perform(get("/jobs/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.employerUsername").value(managerName));
        mvc.perform(get("/users/employers/me").header(AUTHORIZATION, manager))
                .andExpect(jsonPath("$.companyRole").value("OWNER"));
        mvc.perform(put("/users/companies/" + companyId).header(AUTHORIZATION, manager)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Now run by the former manager\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void aPersonOnTheirOwnTakesTheirJobsWithThemAsBefore() throws Exception {
        String lone = register(name("tm_solo"));
        String loneName = JsonPath.read(mvc.perform(get("/users/employers/me").header(AUTHORIZATION, lone))
                .andReturn().getResponse().getContentAsString(), "$.username");
        int id = createJob(lone, "Goes with its poster");
        deleteAccount(lone, loneName);
        mvc.perform(get("/jobs/" + id)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------------------------- legacy

    @Test
    void anEmployerWhoseRoleWasNeverWrittenDownCountsAsOwnerAndTheBackfillWritesIt() throws Exception {
        tx.executeWithoutResult(s -> {
            var employer = employerRepo.findByUsername(ownerName).orElseThrow();
            employer.setCompanyRole(null); // as before company roles existed
            employerRepo.save(employer);
        });
        mvc.perform(put("/users/companies/" + companyId).header(AUTHORIZATION, owner)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Legacy owner edit\"}"))
                .andExpect(status().isOk());

        backfill.run(null);

        tx.executeWithoutResult(s -> assertEquals(CompanyRole.OWNER,
                employerRepo.findByUsername(ownerName).orElseThrow().getCompanyRole()));
        assertEquals(0, employerRepo.findAllByCompanyIsNotNullAndCompanyRoleIsNull().size());
        backfill.run(null); // again, harmless
    }
}
