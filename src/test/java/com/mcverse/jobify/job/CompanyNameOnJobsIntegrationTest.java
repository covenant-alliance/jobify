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
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P5: company name and id on job responses, and the public company endpoint. */
@SpringBootTest
class CompanyNameOnJobsIntegrationTest {

    private static final String JOB = "{\"jobTitle\":\"Company test\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,"
            + "\"rateType\":\"HOURLY\",\"location\":\"Berlin\",\"workMode\":\"REMOTE\",\"employmentType\":\"FULL_TIME\"}";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void seededJobsCarryTheirCompanyNameAndId() throws Exception {
        mvc.perform(get("/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.employerUsername == 'techcorp')].companyName", hasItem("TechCorp Ltd")))
                .andExpect(jsonPath("$[?(@.employerUsername == 'startupxyz')].companyName", hasItem("StartupXYZ Inc")))
                .andExpect(jsonPath("$[?(@.employerUsername == 'techcorp')].companyId").isNotEmpty());
    }

    @Test
    void singleJobAndMyJobsCarryTheCompanyToo() throws Exception {
        String employer = bearer(mvc, "financegroup");
        String created = mvc.perform(post("/jobs").header(AUTHORIZATION, employer)
                        .contentType(APPLICATION_JSON).content(JOB))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName").value("Finance Group SA"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.postId");

        mvc.perform(get("/jobs/" + id)).andExpect(jsonPath("$.companyName").value("Finance Group SA"));
        mvc.perform(get("/jobs/mine").header(AUTHORIZATION, employer))
                .andExpect(jsonPath("$[*].companyName", hasItem("Finance Group SA")));
    }

    @Test
    void employerWithoutACompanyHasNullCompanyFields() throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"no_company_employer\",\"password\":\"s3cur3Pass\","
                                + "\"role\":\"EMPLOYER\",\"firstName\":\"N\",\"lastName\":\"C\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + JsonPath.read(body, "$.token");

        mvc.perform(post("/jobs").header(AUTHORIZATION, token).contentType(APPLICATION_JSON).content(JOB))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employerUsername").value("no_company_employer"))
                .andExpect(jsonPath("$.companyName").doesNotExist())
                .andExpect(jsonPath("$.companyId").doesNotExist());
    }

    @Test
    void companyIsReadablePublicly() throws Exception {
        String job = mvc.perform(get("/jobs")).andReturn().getResponse().getContentAsString();
        String companyId = JsonPath.<java.util.List<String>>read(job,
                "$[?(@.employerUsername == 'techcorp')].companyId").get(0);

        mvc.perform(get("/companies/" + companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(companyId))
                .andExpect(jsonPath("$.name").value("TechCorp Ltd"));
    }

    @Test
    void unknownCompanyIs404() throws Exception {
        mvc.perform(get("/companies/does-not-exist")).andExpect(status().isNotFound());
    }
}
