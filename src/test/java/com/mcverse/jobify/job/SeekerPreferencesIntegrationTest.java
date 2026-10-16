package com.mcverse.jobify.job;

import com.jayway.jsonpath.JsonPath;
import com.mcverse.jobify.account.service.AdminAccountService;
import com.mcverse.jobify.auth.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M5 (#104): GET/PUT /users/seekers/me/preferences. Each test registers its own seeker. */
@SpringBootTest
class SeekerPreferencesIntegrationTest {

    private static final String URL = "/users/seekers/me/preferences";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AdminAccountService accounts;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String newSeeker(String username) throws Exception {
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"s3cur3Pass\",\"role\":\"SEEKER\","
                                + "\"firstName\":\"P\",\"lastName\":\"R\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private static final String FULL = """
            {"preferredJobTitle":"Backend developer","countryCodes":["nl","DE","de"],
             "workModes":["HYBRID","REMOTE"],"employmentTypes":["CONTRACT","FULL_TIME"],
             "minRate":60000,"maxRate":80000,"rateType":"YEARLY","experienceLevel":"MID"}""";

    // ---- read ----

    @Test
    void aSeekerWhoSavedNothingGetsEmptyPreferences() throws Exception {
        mvc.perform(get(URL).header(AUTHORIZATION, newSeeker("pref_empty")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCodes.length()").value(0))
                .andExpect(jsonPath("$.workModes.length()").value(0))
                .andExpect(jsonPath("$.employmentTypes.length()").value(0))
                .andExpect(jsonPath("$.minRate").doesNotExist())
                .andExpect(jsonPath("$.rateType").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    // ---- save ----

    @Test
    void savingReturnsAndKeepsTheNormalisedPreferences() throws Exception {
        String seeker = newSeeker("pref_full");
        mvc.perform(put(URL).header(AUTHORIZATION, seeker).contentType(APPLICATION_JSON).content(FULL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredJobTitle").value("Backend developer"))
                .andExpect(jsonPath("$.countryCodes.length()").value(2))
                .andExpect(jsonPath("$.countryCodes[0]").value("DE"))
                .andExpect(jsonPath("$.countryCodes[1]").value("NL"))
                .andExpect(jsonPath("$.workModes[0]").value("REMOTE"))
                .andExpect(jsonPath("$.workModes[1]").value("HYBRID"))
                .andExpect(jsonPath("$.employmentTypes[0]").value("FULL_TIME"))
                .andExpect(jsonPath("$.employmentTypes[1]").value("CONTRACT"))
                .andExpect(jsonPath("$.minRate").value(60000.0))
                .andExpect(jsonPath("$.maxRate").value(80000.0))
                .andExpect(jsonPath("$.rateType").value("YEARLY"))
                .andExpect(jsonPath("$.experienceLevel").value("MID"))
                .andExpect(jsonPath("$.updatedAt").exists());

        mvc.perform(get(URL).header(AUTHORIZATION, seeker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCodes[0]").value("DE"))
                .andExpect(jsonPath("$.rateType").value("YEARLY"));
    }

    @Test
    void savingAgainReplacesEverythingAndLeavesNoOldValuesBehind() throws Exception {
        String seeker = newSeeker("pref_replace");
        mvc.perform(put(URL).header(AUTHORIZATION, seeker).contentType(APPLICATION_JSON).content(FULL))
                .andExpect(status().isOk());

        mvc.perform(put(URL).header(AUTHORIZATION, seeker).contentType(APPLICATION_JSON)
                        .content("{\"countryCodes\":[\"FR\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCodes.length()").value(1))
                .andExpect(jsonPath("$.workModes.length()").value(0))
                .andExpect(jsonPath("$.employmentTypes.length()").value(0))
                .andExpect(jsonPath("$.preferredJobTitle").doesNotExist())
                .andExpect(jsonPath("$.minRate").doesNotExist())
                .andExpect(jsonPath("$.experienceLevel").doesNotExist());

        mvc.perform(get(URL).header(AUTHORIZATION, seeker))
                .andExpect(jsonPath("$.countryCodes[0]").value("FR"))
                .andExpect(jsonPath("$.workModes.length()").value(0));
    }

    @Test
    void aRateTypeWithoutARateIsDropped() throws Exception {
        mvc.perform(put(URL).header(AUTHORIZATION, newSeeker("pref_unit_only")).contentType(APPLICATION_JSON)
                        .content("{\"rateType\":\"HOURLY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateType").doesNotExist());
    }

    @Test
    void aSingleBoundIsAllowed() throws Exception {
        mvc.perform(put(URL).header(AUTHORIZATION, newSeeker("pref_min_only")).contentType(APPLICATION_JSON)
                        .content("{\"minRate\":50,\"rateType\":\"HOURLY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minRate").value(50.0))
                .andExpect(jsonPath("$.maxRate").doesNotExist());
    }

    @Test
    void aBlankTitleIsStoredAsNothing() throws Exception {
        mvc.perform(put(URL).header(AUTHORIZATION, newSeeker("pref_blank")).contentType(APPLICATION_JSON)
                        .content("{\"preferredJobTitle\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredJobTitle").doesNotExist());
    }

    // ---- validation ----

    private void rejects(String username, String body, String message) throws Exception {
        mvc.perform(put(URL).header(AUTHORIZATION, newSeeker(username)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString(message)));
    }

    @Test
    void minAboveMaxIsRefused() throws Exception {
        rejects("pref_bad_range", "{\"minRate\":90,\"maxRate\":10,\"rateType\":\"YEARLY\"}",
                "minRate must not be greater than maxRate");
    }

    @Test
    void aRateWithoutAUnitIsRefused() throws Exception {
        rejects("pref_no_unit", "{\"minRate\":10}", "rateType is required");
        rejects("pref_no_unit2", "{\"maxRate\":10}", "rateType is required");
    }

    @Test
    void contractTotalIsNotAUnitForAPreference() throws Exception {
        rejects("pref_total", "{\"minRate\":10,\"rateType\":\"CONTRACT_TOTAL\"}", "HOURLY, MONTHLY or YEARLY");
    }

    @Test
    void anUnknownCountryCodeIsRefusedByName() throws Exception {
        rejects("pref_country", "{\"countryCodes\":[\"DE\",\"XX\"]}", "unknown country code: XX");
        rejects("pref_country_null", "{\"countryCodes\":[null]}", "unknown country code");
    }

    @Test
    void anUnknownEnumValueIsAMalformedBody() throws Exception {
        rejects("pref_enum", "{\"workModes\":[\"ON_THE_MOON\"]}", "malformed");
    }

    @Test
    void negativeRatesAndLongTitlesAreRefused() throws Exception {
        rejects("pref_negative", "{\"minRate\":-1,\"rateType\":\"YEARLY\"}", "minRate must not be negative");
        rejects("pref_title", "{\"preferredJobTitle\":\"" + "x".repeat(101) + "\"}", "preferredJobTitle");
    }

    // ---- who may call ----

    @Test
    void employersAndAdminsAreForbiddenAndAnonymousIsUnauthorised() throws Exception {
        for (String user : new String[] {"techcorp", "admin"}) {
            String token = bearer(mvc, user);
            mvc.perform(get(URL).header(AUTHORIZATION, token)).andExpect(status().isForbidden());
            mvc.perform(put(URL).header(AUTHORIZATION, token).contentType(APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(put(URL).contentType(APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void oneSeekerNeverSeesAnothersPreferences() throws Exception {
        String a = newSeeker("pref_owner_a");
        String b = newSeeker("pref_owner_b");
        mvc.perform(put(URL).header(AUTHORIZATION, a).contentType(APPLICATION_JSON).content(FULL))
                .andExpect(status().isOk());

        mvc.perform(get(URL).header(AUTHORIZATION, b))
                .andExpect(jsonPath("$.countryCodes.length()").value(0))
                .andExpect(jsonPath("$.preferredJobTitle").doesNotExist());
    }

    // ---- account removal ----

    @Test
    void removingTheAccountRemovesThePreferencesToo() throws Exception {
        String seeker = newSeeker("pref_removed");
        mvc.perform(put(URL).header(AUTHORIZATION, seeker).contentType(APPLICATION_JSON).content(FULL))
                .andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("select count(*) from seeker_job_preferences p join users u on u.id = p.seeker_id"
                + " where u.username = 'pref_removed'", Integer.class));

        accounts.removeAccount("pref_removed", Role.SEEKER);

        assertEquals(0, jdbc.queryForObject("select count(*) from seeker_preference_countries c"
                + " where c.seeker_id not in (select id from users)", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from seeker_job_preferences p"
                + " where p.seeker_id not in (select id from users)", Integer.class));
    }
}
