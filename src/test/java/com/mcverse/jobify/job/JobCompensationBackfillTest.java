package com.mcverse.jobify.job;

import com.jayway.jsonpath.JsonPath;
import com.mcverse.jobify.job.service.JobCompensationBackfill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Simulates a row written before the compensation model: no rate, no rateType, only an hourly rate. */
@SpringBootTest
class JobCompensationBackfillTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JobCompensationBackfill backfill;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private int createJob() throws Exception {
        String body = mvc.perform(post("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Legacy\",\"jobDescription\":\"<p>x</p>\",\"rate\":55,"
                                + "\"rateType\":\"HOURLY\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.postId");
    }

    @Test
    void legacyRowGetsItsHourlyRateAsRateAndHourlyType() throws Exception {
        int id = createJob();
        jdbc.update("update job_posts set rate = null, rate_type = null, hourly_rate = 42.5 where post_id = ?", id);

        backfill.run(null);

        mvc.perform(get("/jobs/" + id))
                .andExpect(jsonPath("$.rate").value(42.5))
                .andExpect(jsonPath("$.rateType").value("HOURLY"))
                .andExpect(jsonPath("$.hourlyRate").value(42.5));
    }

    @Test
    void rowsAlreadyMigratedAreLeftAlone() throws Exception {
        int id = createJob();
        jdbc.update("update job_posts set rate = 5000, rate_type = 'MONTHLY', hourly_rate = 28.85 where post_id = ?", id);

        backfill.run(null);

        mvc.perform(get("/jobs/" + id))
                .andExpect(jsonPath("$.rate").value(5000.0))
                .andExpect(jsonPath("$.rateType").value("MONTHLY"));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from job_posts where rate is null or rate_type is null", Integer.class));
    }

    @Test
    void responseNeverEmptyEvenBeforeTheBackfillRuns() throws Exception {
        int id = createJob();
        jdbc.update("update job_posts set rate = null, rate_type = null, hourly_rate = 33 where post_id = ?", id);

        mvc.perform(get("/jobs/" + id))
                .andExpect(jsonPath("$.rate").value(33.0))
                .andExpect(jsonPath("$.rateType").value("HOURLY"));
    }
}
