package com.mcverse.jobify.job;

import com.jayway.jsonpath.JsonPath;
import com.mcverse.jobify.notification.model.NotificationType;
import com.mcverse.jobify.notification.service.NotificationRetention;
import com.mcverse.jobify.notification.service.NotificationService;
import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The last backlog items: experience level, country facet, notification retention, friendlier 404 wording. */
@SpringBootTest
class BacklogFinishIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRetention retention;
    @Autowired private NotificationRepository notificationRepo;
    @Autowired private AuditLog auditLog;
    @Autowired private Clock clock;

    private MockMvc mvc;
    private String employer;
    private String city;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        StringBuilder letters = new StringBuilder("Q");
        for (char c : UUID.randomUUID().toString().replace("-", "").substring(0, 10).toCharArray()) {
            letters.append((char) (c >= 'a' ? c : 'a' + (c - '0')));
        }
        city = letters.toString();
        String username = "bf_" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(
                        "{\"username\":\"" + username + "\",\"password\":\"Finish-Pass-4321\",\"firstName\":\"B\","
                                + "\"lastName\":\"F\",\"role\":\"EMPLOYER\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        employer = "Bearer " + JsonPath.read(body, "$.token");
    }

    private String jobJson(String title, String location, String levelJson) {
        return "{\"jobTitle\":\"" + title + "\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,\"rateType\":\"HOURLY\","
                + "\"location\":\"" + location + "\",\"workMode\":\"REMOTE\",\"employmentType\":\"FULL_TIME\""
                + (levelJson == null ? "" : ",\"experienceLevel\":" + levelJson) + "}";
    }

    private int job(String suffix, String location, String levelJson) throws Exception {
        String body = mvc.perform(post("/jobs").header(AUTHORIZATION, employer).contentType(APPLICATION_JSON)
                        .content(jobJson(city + " " + suffix, location, levelJson))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.postId");
    }

    private ResultActions search(String more) throws Exception {
        return mvc.perform(get("/jobs/search?q=" + city + (more.isEmpty() ? "" : "&" + more) + "&size=50"));
    }

    // ------------------------------------------------------------------------------ experience level

    @Test
    void experienceLevelIsStoredReturnedKeptOnEditAndSearchable() throws Exception {
        int senior = job("a", "Berlin", "\"SENIOR\"");
        int lead = job("b", "Berlin", "\"LEAD\"");
        int junior = job("c", "Berlin", "\"JUNIOR\"");
        int unstated = job("d", "Berlin", null);

        mvc.perform(get("/jobs/" + senior)).andExpect(jsonPath("$.experienceLevel").value("SENIOR"));
        mvc.perform(get("/jobs/" + unstated)).andExpect(jsonPath("$.experienceLevel").doesNotExist());
        search("experienceLevel=SENIOR").andExpect(jsonPath("$.content[*].postId", contains(senior)));
        search("experienceLevel=SENIOR&experienceLevel=LEAD").andExpect(jsonPath("$.totalElements").value(2));
        search("experienceLevel=MID").andExpect(jsonPath("$.content", empty()));
        search("").andExpect(jsonPath("$.totalElements").value(4));
        assertEquals(1, ((List<?>) JsonPath.read(search("experienceLevel=JUNIOR").andReturn().getResponse()
                .getContentAsString(), "$.content[?(@.postId==" + junior + ")]")).size());

        // omitted on edit = unchanged; sent = replaced
        mvc.perform(put("/jobs/" + lead).header(AUTHORIZATION, employer).contentType(APPLICATION_JSON)
                        .content(jobJson(city + " b", "Berlin", null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.experienceLevel").value("LEAD"));
        mvc.perform(put("/jobs/" + lead).header(AUTHORIZATION, employer).contentType(APPLICATION_JSON)
                        .content(jobJson(city + " b", "Berlin", "\"MID\"")))
                .andExpect(jsonPath("$.experienceLevel").value("MID"));
    }

    @Test
    void anUnknownExperienceLevelIs400() throws Exception {
        mvc.perform(post("/jobs").header(AUTHORIZATION, employer).contentType(APPLICATION_JSON)
                        .content(jobJson("x", "Berlin", "\"WIZARD\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/jobs/search?experienceLevel=WIZARD")).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------------- country facet

    private Map<String, Object> country(String code, String query) throws Exception {
        String body = mvc.perform(get("/jobs/locations/countries" + query)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> all = JsonPath.read(body, "$");
        return all.stream().filter(c -> code.equals(c.get("countryCode"))).findFirst().orElse(null);
    }

    @Test
    void countriesAreListedWithCountsAndCanFilterSearch() throws Exception {
        int before = country("FR", "?limit=500") == null ? 0 : ((Number) country("FR", "?limit=500").get("jobs")).intValue();
        job("fr1", city + ", FR", null);
        job("fr2", city + "2, France", null);
        job("de1", city + ", DE", null);

        Map<String, Object> fr = country("FR", "?limit=500");
        assertEquals(before + 2, ((Number) fr.get("jobs")).intValue());
        assertEquals("France", fr.get("country"));

        search("countryCode=FR").andExpect(jsonPath("$.totalElements").value(2));
        search("countryCode=fr&countryCode=DE").andExpect(jsonPath("$.totalElements").value(3));
        search("countryCode=ES").andExpect(jsonPath("$.content", empty()));

        mvc.perform(get("/jobs/locations/countries?available=false&limit=500")).andExpect(status().isOk());
        mvc.perform(get("/jobs/locations/countries").header(AUTHORIZATION, "Bearer stale.token.x")).andExpect(status().isOk());
    }

    @Test
    void countryParametersAreChecked() throws Exception {
        mvc.perform(get("/jobs/search?countryCode=GERMANY")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("countryCode must be a two-letter ISO code such as DE."));
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 21; i++) many.append(many.isEmpty() ? "" : "&").append("countryCode=").append((char) ('A' + i / 26)).append((char) ('A' + i % 26));
        mvc.perform(get("/jobs/search?" + many)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("countryCode must have at most 20 entries."));
        mvc.perform(get("/jobs/locations/countries?limit=0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("limit must be between 1 and 500."));
        mvc.perform(get("/jobs/locations/countries?available=maybe")).andExpect(status().isBadRequest());
        mvc.perform(get("/jobs/search?countryCode=")).andExpect(status().isOk());
    }

    // ----------------------------------------------------------------------------- notification clean-up

    private String notify(String recipient, boolean read, int daysOld) {
        notificationService.notify(recipient, NotificationType.SYSTEM, "Retention " + daysOld, "body", null, null);
        String id = jdbc.queryForObject("select id from notifications where recipient_username = ? order by created_at desc limit 1",
                String.class, recipient);
        jdbc.update("update notifications set is_read = ?, created_at = ? where id = ?", read,
                LocalDateTime.now().minusDays(daysOld), id);
        return id;
    }

    private boolean exists(String id) {
        return jdbc.queryForObject("select count(*) from notifications where id = ?", Integer.class, id) > 0;
    }

    @Test
    void oldReadNotificationsAndVeryOldOnesAreDeletedAndTheRestKept() throws Exception {
        String who = "ret_" + UUID.randomUUID().toString().substring(0, 8);
        String recentRead = notify(who, true, 10);
        String oldRead = notify(who, true, 100);
        String oldUnread = notify(who, false, 100);
        String ancientUnread = notify(who, false, 400);

        int[] deleted = retention.purge();

        assertEquals(true, exists(recentRead), "recent read stays");
        assertEquals(false, exists(oldRead), "read and older than 90 days goes");
        assertEquals(true, exists(oldUnread), "unread stays until it is a year old");
        assertEquals(false, exists(ancientUnread), "anything older than a year goes");
        assertEquals(true, deleted[0] >= 1 && deleted[1] >= 1);
        retention.scheduled(); // the nightly entry point runs the same thing without error
    }

    @Test
    void aValueOfZeroSwitchesARuleOff() throws Exception {
        String who = "ret0_" + UUID.randomUUID().toString().substring(0, 8);
        String oldRead = notify(who, true, 100);
        String ancient = notify(who, false, 400);
        int[] none = new NotificationRetention(notificationRepo, auditLog, clock, 0, 0).purge();
        assertEquals(0, none[0] + none[1]);
        assertEquals(true, exists(oldRead) && exists(ancient));
        jdbc.update("delete from notifications where recipient_username = ?", who);
    }

    // ---------------------------------------------------------------------------------- 404 wording

    @Test
    void notFoundMessagesAreFriendly() throws Exception {
        mvc.perform(get("/jobs/987654")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("We could not find that job."))
                .andExpect(jsonPath("$.status").value(404));
        mvc.perform(get("/companies/" + UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("We could not find that company."));
        mvc.perform(post("/notifications/" + UUID.randomUUID() + "/read").header(AUTHORIZATION, employer))
                .andExpect(jsonPath("$.message").value("We could not find that notification."));
        mvc.perform(get("/users/employers/" + UUID.randomUUID()).header(AUTHORIZATION, bearer(mvc, "admin")))
                .andExpect(jsonPath("$.message").value("We could not find that employer profile."));
        mvc.perform(get("/jobs/987654/images/" + UUID.randomUUID()))
                .andExpect(jsonPath("$.message").value("We could not find that job."));
    }
}
