package com.mcverse.jobify.application;

import com.jayway.jsonpath.JsonPath;
import com.mcverse.jobify.application.model.Application;
import com.mcverse.jobify.application.model.ApplicationStatus;
import com.mcverse.jobify.application.model.ApplicationStatusChange;
import com.mcverse.jobify.application.repository.ApplicationRepository;
import com.mcverse.jobify.application.repository.ApplicationStatusChangeRepository;
import com.mcverse.jobify.application.service.ApplicationHistoryBackfill;
import com.mcverse.jobify.job.repository.JobRepo;
import com.mcverse.jobify.user.repository.SeekerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every change of an application's status is recorded, by whom and when, and nothing else adds rows. */
@SpringBootTest
class ApplicationHistoryIntegrationTest {

    private static final String PASSWORD = "a-decent-passphrase";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ApplicationStatusChangeRepository historyRepo;
    @Autowired
    private ApplicationRepository applicationRepo;
    @Autowired
    private SeekerRepository seekerRepo;
    @Autowired
    private JobRepo jobRepo;
    @Autowired
    private ApplicationHistoryBackfill backfill;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String[] register(String role) throws Exception {
        String username = role.toLowerCase().charAt(0) + "h_" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD
                                + "\",\"role\":\"" + role + "\",\"firstName\":\"H\",\"lastName\":\"S\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new String[] {username, "Bearer " + JsonPath.read(body, "$.token")};
    }

    private int createJob(String employerToken) throws Exception {
        String body = mvc.perform(post("/jobs").header(AUTHORIZATION, employerToken).contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"History job\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,"
                                + "\"rateType\":\"HOURLY\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.postId");
    }

    private String apply(String seekerToken, int jobId) throws Exception {
        String body = mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, seekerToken))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void move(String employerToken, String applicationId, String status, int expected) throws Exception {
        mvc.perform(put("/applications/" + applicationId + "/status").header(AUTHORIZATION, employerToken)
                .contentType(APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().is(expected));
    }

    private List<ApplicationStatusChange> history(String applicationId) {
        return historyRepo.findAllByApplicationIdOrderByChangedAtAscIdAsc(applicationId);
    }

    @Test
    void applyingMovingAndWithdrawingAreRecordedWithWhoDidIt() throws Exception {
        String[] employer = register("EMPLOYER");
        String[] seeker = register("SEEKER");
        int job = createJob(employer[1]);
        String id = apply(seeker[1], job);

        List<ApplicationStatusChange> afterApply = history(id);
        assertEquals(1, afterApply.size());
        assertNull(afterApply.get(0).getFromStatus());
        assertEquals(ApplicationStatus.APPLIED, afterApply.get(0).getToStatus());
        assertEquals(seeker[0], afterApply.get(0).getChangedBy());

        move(employer[1], id, "IN_REVIEW", 200);
        move(employer[1], id, "INTERVIEW", 200);
        mvc.perform(delete("/applications/" + id).header(AUTHORIZATION, seeker[1])).andExpect(status().isNoContent());

        List<ApplicationStatusChange> all = history(id);
        assertEquals(4, all.size());
        assertEquals(ApplicationStatus.IN_REVIEW, all.get(1).getToStatus());
        assertEquals(ApplicationStatus.APPLIED, all.get(1).getFromStatus());
        assertEquals(employer[0], all.get(1).getChangedBy());
        assertEquals(ApplicationStatus.INTERVIEW, all.get(2).getToStatus());
        assertEquals(ApplicationStatus.WITHDRAWN, all.get(3).getToStatus());
        assertEquals(ApplicationStatus.INTERVIEW, all.get(3).getFromStatus());
        assertEquals(seeker[0], all.get(3).getChangedBy(), "a withdrawal is the applicant's own action");
    }

    @Test
    void theLastRowCarriesTheSameTimestampAsTheApplication() throws Exception {
        String[] employer = register("EMPLOYER");
        String[] seeker = register("SEEKER");
        String id = apply(seeker[1], createJob(employer[1]));
        move(employer[1], id, "IN_REVIEW", 200);
        List<ApplicationStatusChange> rows = history(id);
        Application application = applicationRepo.findById(id).orElseThrow();
        assertEquals(application.getUpdatedAt(), rows.get(rows.size() - 1).getChangedAt());
        assertEquals(application.getCreatedAt(), rows.get(0).getChangedAt());
    }

    @Test
    void reapplyingAfterAWithdrawalIsRecorded() throws Exception {
        String[] employer = register("EMPLOYER");
        String[] seeker = register("SEEKER");
        int job = createJob(employer[1]);
        String id = apply(seeker[1], job);
        mvc.perform(delete("/applications/" + id).header(AUTHORIZATION, seeker[1])).andExpect(status().isNoContent());
        assertEquals(id, apply(seeker[1], job), "the same application row is reused");

        List<ApplicationStatusChange> rows = history(id);
        assertEquals(3, rows.size());
        assertEquals(ApplicationStatus.WITHDRAWN, rows.get(2).getFromStatus());
        assertEquals(ApplicationStatus.APPLIED, rows.get(2).getToStatus());
    }

    @Test
    void refusedChangesAddNoRows() throws Exception {
        String[] employer = register("EMPLOYER");
        String[] other = register("EMPLOYER");
        String[] seeker = register("SEEKER");
        String id = apply(seeker[1], createJob(employer[1]));

        move(employer[1], id, "OFFER", 422);          // not an allowed move from APPLIED
        move(employer[1], id, "WITHDRAWN", 422);      // only the applicant withdraws
        move(other[1], id, "IN_REVIEW", 403);         // not the owner
        mvc.perform(delete("/applications/" + id).header(AUTHORIZATION, other[1]));   // not the applicant
        assertEquals(1, historyRepo.countByApplicationId(id), "only the creation row");
    }

    @Test
    void aRejectedApplicationIsFinalAndGainsNoFurtherRows() throws Exception {
        String[] employer = register("EMPLOYER");
        String[] seeker = register("SEEKER");
        String id = apply(seeker[1], createJob(employer[1]));
        move(employer[1], id, "REJECTED", 200);
        move(employer[1], id, "IN_REVIEW", 422);
        assertEquals(2, historyRepo.countByApplicationId(id));
    }

    @Test
    void applicationsFromBeforeHistoryGetAMinimalHistoryOnceAndOnlyOnce() {
        Application old = applicationRepo.save(newOldApplication(ApplicationStatus.INTERVIEW));
        assertEquals(0, historyRepo.countByApplicationId(old.getId()));

        backfill.run(null);
        List<ApplicationStatusChange> rows = history(old.getId());
        assertEquals(2, rows.size());
        assertNull(rows.get(0).getFromStatus());
        assertEquals(ApplicationStatus.APPLIED, rows.get(0).getToStatus());
        assertEquals(applicationRepo.findById(old.getId()).orElseThrow().getCreatedAt(), rows.get(0).getChangedAt());
        assertEquals(ApplicationStatus.APPLIED, rows.get(1).getFromStatus());
        assertEquals(ApplicationStatus.INTERVIEW, rows.get(1).getToStatus());
        assertEquals("history-backfill", rows.get(1).getChangedBy());

        backfill.run(null);
        assertEquals(2, historyRepo.countByApplicationId(old.getId()), "running it again changes nothing");
    }

    @Test
    void anOldApplicationStillAtAppliedGetsJustTheCreationRow() {
        Application old = applicationRepo.save(newOldApplication(ApplicationStatus.APPLIED));
        backfill.run(null);
        assertEquals(1, historyRepo.countByApplicationId(old.getId()));
    }

    @Test
    void approvingAnAccountDeletionRemovesTheApplicantsApplicationsAndTheirHistory() throws Exception {
        String[] employer = register("EMPLOYER");
        String[] seeker = register("SEEKER");
        String id = apply(seeker[1], createJob(employer[1]));
        move(employer[1], id, "IN_REVIEW", 200);
        assertEquals(2, historyRepo.countByApplicationId(id));

        String requestBody = mvc.perform(post("/account/deletion-request").header(AUTHORIZATION, seeker[1])
                        .contentType(APPLICATION_JSON).content("{\"reason\":\"bye\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(requestBody, "$.id");
        mvc.perform(post("/admin/deletion-requests/" + requestId + "/approve")
                        .header(AUTHORIZATION, bearer(mvc, "admin")).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        assertTrue(applicationRepo.findById(id).isEmpty());
        assertEquals(0, historyRepo.countByApplicationId(id), "the history goes with the application");
    }

    @Test
    void approvingAnEmployersDeletionRemovesTheirJobsApplicationsAndHistory() throws Exception {
        String[] employer = register("EMPLOYER");
        String[] seeker = register("SEEKER");
        String id = apply(seeker[1], createJob(employer[1]));
        move(employer[1], id, "REJECTED", 200);

        String requestBody = mvc.perform(post("/account/deletion-request").header(AUTHORIZATION, employer[1])
                        .contentType(APPLICATION_JSON).content("{\"reason\":\"closing\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/admin/deletion-requests/" + JsonPath.read(requestBody, "$.id") + "/approve")
                        .header(AUTHORIZATION, bearer(mvc, "admin")).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        assertFalse(applicationRepo.findById(id).isPresent());
        assertEquals(0, historyRepo.countByApplicationId(id));
    }

    /** An application as an older build would have left it: no history rows. */
    private Application newOldApplication(ApplicationStatus status) {
        var seeker = seekerRepo.findByUsername("carol_s").orElseThrow();
        // a job this seeker has not applied to yet: any seeded job, made unique by scanning for a free pair
        var job = jobRepo.findAll().stream()
                .filter(j -> applicationRepo.findBySeekerUsernameAndJobPostId("carol_s", j.getPostId()).isEmpty())
                .findFirst().orElseThrow();
        Application application = new Application(seeker, job, null);
        if (status != ApplicationStatus.APPLIED) {
            application.changeStatus(status);
        }
        return application;
    }
}
