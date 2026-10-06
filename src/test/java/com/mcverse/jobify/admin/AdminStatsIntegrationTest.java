package com.mcverse.jobify.admin;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** P7: GET /admin/stats. Test classes share data, so these check what changes, not absolute numbers. */
@SpringBootTest
class AdminStatsIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin = bearer(mvc, "admin");
    }

    private String stats() throws Exception {
        return mvc.perform(get("/admin/stats").header(AUTHORIZATION, admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static long number(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    @Test
    void theShapeIsCompleteAndTotalsAddUp() throws Exception {
        String s = stats();
        assertEquals(number(s, "$.users.byRole.SEEKER") + number(s, "$.users.byRole.EMPLOYER")
                + number(s, "$.users.byRole.ADMIN"), number(s, "$.users.total"));
        assertEquals(number(s, "$.jobs.open") + number(s, "$.jobs.closed"), number(s, "$.jobs.total"));
        long sum = 0;
        for (String status : new String[] {"APPLIED", "IN_REVIEW", "INTERVIEW", "OFFER", "REJECTED", "WITHDRAWN"}) {
            sum += number(s, "$.applications.byStatus." + status);
        }
        assertEquals(sum, number(s, "$.applications.total"));
        assertTrue(number(s, "$.users.byRole.ADMIN") >= 1);
        assertTrue(number(s, "$.users.byRole.SEEKER") >= 3);
        assertTrue(number(s, "$.jobs.closed") >= 1, "the seed data has closed positions");
        assertTrue(number(s, "$.pendingDeletionRequests") >= 0);
        assertTrue(number(s, "$.users.newLast30Days") >= number(s, "$.users.newLast7Days"));
    }

    @Test
    void numbersMoveWhenThingsHappen() throws Exception {
        String before = stats();

        // a new seeker and a new employer
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                .content("{\"username\":\"stats_adm_seeker\",\"password\":\"a-decent-passphrase\",\"role\":\"SEEKER\","
                        + "\"firstName\":\"S\",\"lastName\":\"S\"}")).andExpect(status().isCreated());
        String registered = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"stats_adm_employer\",\"password\":\"a-decent-passphrase\","
                                + "\"role\":\"EMPLOYER\",\"firstName\":\"E\",\"lastName\":\"E\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String employer = "Bearer " + JsonPath.read(registered, "$.token");

        // a job, an application to it, then a stage change and a deletion request
        String job = mvc.perform(post("/jobs").header(AUTHORIZATION, employer).contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Stats job\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,"
                                + "\"rateType\":\"HOURLY\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int jobId = JsonPath.read(job, "$.postId");
        String application = mvc.perform(post("/jobs/" + jobId + "/apply").header(AUTHORIZATION, bearer(mvc, "bob_s")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String applicationId = JsonPath.read(application, "$.id");
        mvc.perform(put("/applications/" + applicationId + "/status").header(AUTHORIZATION, employer)
                .contentType(APPLICATION_JSON).content("{\"status\":\"IN_REVIEW\"}")).andExpect(status().isOk());
        mvc.perform(post("/account/deletion-request").header(AUTHORIZATION, bearer(mvc, "carol_s")))
                .andExpect(status().is2xxSuccessful());

        String after = stats();
        assertEquals(number(before, "$.users.total") + 2, number(after, "$.users.total"));
        assertEquals(number(before, "$.users.byRole.SEEKER") + 1, number(after, "$.users.byRole.SEEKER"));
        assertEquals(number(before, "$.users.byRole.EMPLOYER") + 1, number(after, "$.users.byRole.EMPLOYER"));
        assertTrue(number(after, "$.users.newLast7Days") >= number(before, "$.users.newLast7Days") + 2);
        assertEquals(number(before, "$.jobs.open") + 1, number(after, "$.jobs.open"));
        assertEquals(number(before, "$.jobs.total") + 1, number(after, "$.jobs.total"));
        assertTrue(number(after, "$.jobs.postedLast7Days") >= number(before, "$.jobs.postedLast7Days") + 1);
        assertEquals(number(before, "$.applications.total") + 1, number(after, "$.applications.total"));
        assertEquals(number(before, "$.applications.byStatus.IN_REVIEW") + 1,
                number(after, "$.applications.byStatus.IN_REVIEW"));
        assertTrue(number(after, "$.applications.submittedLast7Days")
                >= number(before, "$.applications.submittedLast7Days") + 1);
        assertTrue(number(after, "$.pendingDeletionRequests") >= number(before, "$.pendingDeletionRequests"));

        // closing the job moves it from open to closed
        mvc.perform(patch("/jobs/" + jobId + "/available").header(AUTHORIZATION, employer)
                .contentType(APPLICATION_JSON).content("{\"available\":false}")).andExpect(status().isOk());
        String closed = stats();
        assertEquals(number(after, "$.jobs.open") - 1, number(closed, "$.jobs.open"));
        assertEquals(number(after, "$.jobs.closed") + 1, number(closed, "$.jobs.closed"));
    }

    @Test
    void onlyAdminsMaySeeTheStats() throws Exception {
        mvc.perform(get("/admin/stats").header(AUTHORIZATION, bearer(mvc, "alice_s"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/stats").header(AUTHORIZATION, bearer(mvc, "techcorp"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/stats")).andExpect(status().isUnauthorized());
    }
}
