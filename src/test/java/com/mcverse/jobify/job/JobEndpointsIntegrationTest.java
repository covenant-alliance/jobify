package com.mcverse.jobify.job;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class JobEndpointsIntegrationTest {

    private static final String VALID_JOB = """
            {"jobTitle":"Platform Engineer","jobDescription":"<p>Build things</p>","rate":60,"rateType":"HOURLY","location":"Remote","workMode":"REMOTE","employmentType":"FULL_TIME",
             "requiredSkills":["Java","Spring Boot"]}""";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ── B1 ────────────────────────────────────────────────────────────────────

    @Test
    void corsPreflightAllowsPatch() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/jobs/1/available")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Methods", containsString("PATCH")));
    }

    // ── public routes ─────────────────────────────────────────────────────────

    @Test
    void anonymousCanListAndReadJobs() throws Exception {
        mvc.perform(get("/jobs")).andExpect(status().isOk());
        mvc.perform(get("/jobs?available=true")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].available", everyItem(equalTo(true))));
    }

    @Test
    void anonymousCannotWrite() throws Exception {
        mvc.perform(post("/jobs").contentType(APPLICATION_JSON).content(VALID_JOB))
                .andExpect(status().isUnauthorized());
    }

    // ── B2: /jobs/mine ────────────────────────────────────────────────────────

    @Test
    void employerSeesOnlyOwnJobsIncludingClosed() throws Exception {
        mvc.perform(get("/jobs/mine").header(AUTHORIZATION, bearer(mvc, "techcorp")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].employerUsername", everyItem(equalTo("techcorp"))))
                .andExpect(jsonPath("$.length()").isNotEmpty());
    }

    @Test
    void seekerCannotUseMine() throws Exception {
        mvc.perform(get("/jobs/mine").header(AUTHORIZATION, bearer(mvc, "alice_s")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotUseMine() throws Exception {
        mvc.perform(get("/jobs/mine")).andExpect(status().isUnauthorized());
    }

    // ── B3 / B5: create ───────────────────────────────────────────────────────

    @Test
    void seekerCreatingJobGets403WithReadableMessage() throws Exception {
        mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "alice_s"))
                        .contentType(APPLICATION_JSON).content(VALID_JOB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Only employers can post jobs."))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void employerCreatesJobOpenAndOwned() throws Exception {
        mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON).content(VALID_JOB))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employerUsername").value("techcorp"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.requiredSkills[0]").value("Java"))
                .andExpect(jsonPath("$.workMode").value("REMOTE"));
    }

    @Test
    void clientCannotForceOwnershipOrAvailabilityOnCreate() throws Exception {
        String body = """
                {"jobTitle":"T","jobDescription":"<p>d</p>","rate":10,"rateType":"HOURLY","available":false,"postId":9999,
                 "employer":{"username":"startupxyz"}}""";
        mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employerUsername").value("techcorp"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.postId").value(not(9999)));
    }

    @Test
    void invalidCreateRequestGets400WithReadableMessage() throws Exception {
        String body = "{\"jobTitle\":\"\",\"jobDescription\":\"<p>d</p>\",\"rate\":0}";
        mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("jobTitle is required")))
                .andExpect(jsonPath("$.message", containsString("rate must be greater than 0")))
                .andExpect(jsonPath("$.message", containsString("rateType is required")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void malformedJsonGets400() throws Exception {
        mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request body is missing or malformed."));
    }

    @Test
    void oversizedDescriptionIsRejected() throws Exception {
        String big = "a".repeat(20_001);
        String body = "{\"jobTitle\":\"T\",\"jobDescription\":\"" + big + "\",\"rate\":1,\"rateType\":\"HOURLY\"}";
        mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("jobDescription must be at most 20000")));
    }

    // ── B4: sanitizing on write ───────────────────────────────────────────────

    @Test
    void scriptsAndEventHandlersAreStrippedOnCreate() throws Exception {
        String body = "{\"jobTitle\":\"T\",\"jobDescription\":\"<p onclick=\\\"x()\\\">Hi</p>"
                + "<script>alert(1)</script><a href=\\\"javascript:alert(1)\\\">bad</a><strong>ok</strong>\",\"rate\":1,\"rateType\":\"HOURLY\"}";
        mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobDescription", not(containsString("script"))))
                .andExpect(jsonPath("$.jobDescription", not(containsString("onclick"))))
                .andExpect(jsonPath("$.jobDescription", not(containsString("javascript:"))))
                .andExpect(jsonPath("$.jobDescription", containsString("<strong>ok</strong>")));
    }

    // ── B3 / B5: edit and availability ownership ──────────────────────────────

    @Test
    void ownerCanEditAndToggleButOthersCannot() throws Exception {
        String owner = bearer(mvc, "techcorp");
        String created = mvc.perform(post("/jobs").header(AUTHORIZATION, owner)
                        .contentType(APPLICATION_JSON).content(VALID_JOB))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.postId");

        String edited = VALID_JOB.replace("Platform Engineer", "Staff Engineer");
        mvc.perform(put("/jobs/" + id).header(AUTHORIZATION, owner)
                        .contentType(APPLICATION_JSON).content(edited))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Staff Engineer"))
                .andExpect(jsonPath("$.available").value(true));

        mvc.perform(patch("/jobs/" + id + "/available").header(AUTHORIZATION, owner)
                        .contentType(APPLICATION_JSON).content("{\"available\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        String other = bearer(mvc, "startupxyz");
        mvc.perform(patch("/jobs/" + id + "/available").header(AUTHORIZATION, other)
                        .contentType(APPLICATION_JSON).content("{\"available\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only change job postings that you created."));
        mvc.perform(put("/jobs/" + id).header(AUTHORIZATION, other)
                        .contentType(APPLICATION_JSON).content(edited))
                .andExpect(status().isForbidden());

        String seeker = bearer(mvc, "alice_s");
        mvc.perform(patch("/jobs/" + id + "/available").header(AUTHORIZATION, seeker)
                        .contentType(APPLICATION_JSON).content("{\"available\":true}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/jobs/" + id))
                .andExpect(jsonPath("$.jobTitle").value("Staff Engineer"))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void editingMissingJobIs404() throws Exception {
        mvc.perform(put("/jobs/987654").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON).content(VALID_JOB))
                .andExpect(status().isNotFound());
    }

    // ── P11: compensation model ───────────────────────────────────────────────

    private String createJobJson(String rate, String rateType) {
        return "{\"jobTitle\":\"Pay test\",\"jobDescription\":\"<p>x</p>\",\"rate\":" + rate
                + (rateType == null ? "" : ",\"rateType\":\"" + rateType + "\"") + "}";
    }

    @Test
    void hourlyRateIsReturnedAsIs() throws Exception {
        mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON).content(createJobJson("75.5", "HOURLY")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rate").value(75.5))
                .andExpect(jsonPath("$.rateType").value("HOURLY"))
                .andExpect(jsonPath("$.hourlyRate").value(75.5));
    }

    @Test
    void monthlyYearlyAndContractRatesGetTheirHourlyEquivalent() throws Exception {
        String token = bearer(mvc, "techcorp");
        mvc.perform(post("/jobs").header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                        .content(createJobJson("5200", "MONTHLY")))
                .andExpect(jsonPath("$.rate").value(5200.0))
                .andExpect(jsonPath("$.rateType").value("MONTHLY"))
                .andExpect(jsonPath("$.hourlyRate").value(30.0));
        mvc.perform(post("/jobs").header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                        .content(createJobJson("104000", "YEARLY")))
                .andExpect(jsonPath("$.hourlyRate").value(50.0));
        mvc.perform(post("/jobs").header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                        .content(createJobJson("20000", "CONTRACT_TOTAL")))
                .andExpect(jsonPath("$.rate").value(20000.0))
                .andExpect(jsonPath("$.rateType").value("CONTRACT_TOTAL"))
                .andExpect(jsonPath("$.hourlyRate").value(0.0));
    }

    @Test
    void rateMustBePositiveAndRateTypeRequired() throws Exception {
        String token = bearer(mvc, "techcorp");
        mvc.perform(post("/jobs").header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                        .content(createJobJson("0", "HOURLY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("rate must be greater than 0"));
        mvc.perform(post("/jobs").header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                        .content(createJobJson("-5", "HOURLY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("rate must be greater than 0"));
        mvc.perform(post("/jobs").header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                        .content(createJobJson("50", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("rateType is required"));
        mvc.perform(post("/jobs").header(AUTHORIZATION, token).contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"T\",\"jobDescription\":\"<p>x</p>\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("rate is required")));
    }

    @Test
    void unknownRateTypeIs400() throws Exception {
        mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON).content(createJobJson("50", "WEEKLY")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void legacyFieldsFromOldClientsAreIgnoredNotRejected() throws Exception {
        String body = "{\"jobTitle\":\"Old client\",\"jobDescription\":\"<p>x</p>\",\"rate\":40,"
                + "\"rateType\":\"HOURLY\",\"jobRating\":4.5,\"hourlyRate\":999}";
        mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hourlyRate").value(40.0))
                .andExpect(jsonPath("$.jobRating").doesNotExist());
    }

    @Test
    void b2bIsAValidEmploymentType() throws Exception {
        String body = "{\"jobTitle\":\"Contractor\",\"jobDescription\":\"<p>x</p>\",\"rate\":80,"
                + "\"rateType\":\"HOURLY\",\"employmentType\":\"B2B\"}";
        mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employmentType").value("B2B"));
    }

    @Test
    void editingChangesTheCompensation() throws Exception {
        String owner = bearer(mvc, "techcorp");
        String created = mvc.perform(post("/jobs").header(AUTHORIZATION, owner)
                        .contentType(APPLICATION_JSON).content(createJobJson("40", "HOURLY")))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.postId");
        mvc.perform(put("/jobs/" + id).header(AUTHORIZATION, owner).contentType(APPLICATION_JSON)
                        .content(createJobJson("6000", "MONTHLY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateType").value("MONTHLY"))
                .andExpect(jsonPath("$.hourlyRate").value(34.62));
    }

    @Test
    void seededJobsExposeRateAndHaveNoJobRating() throws Exception {
        mvc.perform(get("/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rate").isNumber())
                .andExpect(jsonPath("$[0].rateType").value("HOURLY"))
                .andExpect(jsonPath("$[0].jobRating").doesNotExist());
    }
}
