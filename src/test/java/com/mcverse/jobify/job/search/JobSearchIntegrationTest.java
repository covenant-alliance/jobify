package com.mcverse.jobify.job.search;

import com.jayway.jsonpath.JsonPath;
import com.mcverse.jobify.job.model.JobPost;
import com.mcverse.jobify.job.repository.JobRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /jobs/search through the real API. Every test makes its own jobs under a unique tag in the title and searches
 * with that tag (q=tag), so it never depends on the jobs other tests left in the shared database.
 */
@SpringBootTest
class JobSearchIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JobRepo jobRepo;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JobSearchTextBackfill backfill;

    private MockMvc mvc;
    private String employer;
    private String tag;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        tag = "tg" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String username = "se_" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(
                        "{\"username\":\"" + username + "\",\"password\":\"Search-Pass-4321\",\"firstName\":\"S\","
                                + "\"lastName\":\"E\",\"role\":\"EMPLOYER\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        employer = "Bearer " + JsonPath.read(body, "$.token");
    }

    // ------------------------------------------------------------------------------------------------- helpers

    /** A job under this test's tag. Null fields are left out of the request. */
    private int job(String titleSuffix, String description, Number rate, String rateType, String location,
                    String workMode, String employmentType, String... skills) throws Exception {
        StringBuilder json = new StringBuilder("{\"jobTitle\":\"" + tag + " " + titleSuffix + "\",\"jobDescription\":\""
                + description.replace("\"", "\\\"") + "\",\"rate\":" + (rate == null ? 50 : rate)
                + ",\"rateType\":\"" + (rateType == null ? "HOURLY" : rateType) + "\"");
        if (location != null) json.append(",\"location\":\"").append(location).append("\"");
        if (workMode != null) json.append(",\"workMode\":\"").append(workMode).append("\"");
        if (employmentType != null) json.append(",\"employmentType\":\"").append(employmentType).append("\"");
        if (skills.length > 0) {
            json.append(",\"requiredSkills\":[");
            for (int i = 0; i < skills.length; i++) json.append(i > 0 ? "," : "").append("\"").append(skills[i]).append("\"");
            json.append("]");
        }
        json.append("}");
        String body = mvc.perform(post("/jobs").header(AUTHORIZATION, employer).contentType(APPLICATION_JSON)
                        .content(json.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.postId");
    }

    private int job(String titleSuffix) throws Exception {
        return job(titleSuffix, "<p>plain</p>", null, null, null, null, null);
    }

    private void close(int id) throws Exception {
        mvc.perform(patch("/jobs/" + id + "/available").header(AUTHORIZATION, employer)
                .contentType(APPLICATION_JSON).content("{\"available\":false}")).andExpect(status().isOk());
    }

    /** GET /jobs/search?q=<tag>&<more> */
    private ResultActions search(String more) throws Exception {
        return mvc.perform(get("/jobs/search?q=" + tag + (more.isEmpty() ? "" : "&" + more)));
    }

    private List<Integer> ids(String more) throws Exception {
        String body = search(more).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.content[*].postId");
    }

    // ------------------------------------------------------------------------------------- public and unchanged

    @Test
    void searchIsPublicAndIgnoresAStaleToken() throws Exception {
        job("public");
        mvc.perform(get("/jobs/search?q=" + tag)).andExpect(status().isOk());
        mvc.perform(get("/jobs/search?q=" + tag).header(AUTHORIZATION, "Bearer not.a.real-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void theOldJobsListIsStillThePlainArrayItAlwaysWas() throws Exception {
        int id = job("old route");
        mvc.perform(get("/jobs?available=true")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.postId==" + id + ")]").isArray())
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void anItemIsTheSameJobPostResponseAsGetJobById() throws Exception {
        int id = job("same shape", "<p>words</p>", 6000, "MONTHLY", "Berlin", "HYBRID", "B2B", "Java", "Spring");
        String item = mvc.perform(get("/jobs/search?q=" + tag)).andReturn().getResponse().getContentAsString();
        Object fromSearch = JsonPath.read(item, "$.content[0]");
        Object fromById = JsonPath.read(mvc.perform(get("/jobs/" + id)).andReturn().getResponse().getContentAsString(), "$");
        assertEquals(fromById, fromSearch);
        search("").andExpect(jsonPath("$.content[0].rate").value(6000.0))
                .andExpect(jsonPath("$.content[0].rateType").value("MONTHLY"))
                .andExpect(jsonPath("$.content[0].hourlyRate").doesNotExist())
                .andExpect(jsonPath("$.content[0].requiredSkills", containsInAnyOrder("Java", "Spring")))
                .andExpect(jsonPath("$.content[0].employerUsername").exists());
    }

    // ------------------------------------------------------------------------------------------------ defaults

    @Test
    void byDefaultOnlyOpenJobsNewestFirstAtMostTwentyPerPage() throws Exception {
        int first = job("a first");
        int second = job("b second");
        int closed = job("c closed");
        close(closed);
        search("").andExpect(jsonPath("$.content[*].postId", contains(second, first)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void noMatchIs200WithAnEmptyPageNever404() throws Exception {
        mvc.perform(get("/jobs/search?q=" + tag + "nothing")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.last").value(true));
    }

    // ------------------------------------------------------------------------------------------------ text (q)

    @Test
    void qMatchesTitleLocationDescriptionWordsCompanyNameAndSkillNamesInAnyCase() throws Exception {
        // the company name is set once for this employer
        String companyName = "Co" + tag;
        mvc.perform(post("/users/companies").header(AUTHORIZATION, employer).contentType(APPLICATION_JSON)
                .content("{\"name\":\"" + companyName + "\"}")).andExpect(status().isCreated());
        int byTitle = job("MixedCaseTitle");
        int byLocation = job("loc", "<p>x</p>", null, null, "Lisbon" + tag, null, null);
        int byDescription = job("desc", "<p>We want a <em>wizard" + tag + "</em></p>", null, null, null, null, null);
        int bySkill = job("skill", "<p>x</p>", null, null, null, null, null, "Skill" + tag);

        // all four carry the tag already, so search for the distinguishing part instead
        for (String term : new String[] {"mixedcasetitle", "MIXEDCASETITLE"}) {
            mvc.perform(get("/jobs/search?q=" + term + "&sort=newest")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].postId", org.hamcrest.Matchers.hasItem(byTitle)));
        }
        mvc.perform(get("/jobs/search?q=lisbon" + tag)).andExpect(jsonPath("$.content[*].postId", contains(byLocation)));
        mvc.perform(get("/jobs/search?q=WIZARD" + tag)).andExpect(jsonPath("$.content[*].postId", contains(byDescription)));
        mvc.perform(get("/jobs/search?q=skill" + tag)).andExpect(jsonPath("$.content[*].postId", contains(bySkill)));
        // the company name matches every job of that employer
        mvc.perform(get("/jobs/search?q=co" + tag + "&size=50")).andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void descriptionSearchLooksAtWordsNeverAtMarkup() throws Exception {
        int id = job("markup", "<p><strong>emphasis" + tag + "</strong></p>", null, null, null, null, null);
        mvc.perform(get("/jobs/search?q=emphasis" + tag)).andExpect(jsonPath("$.content[*].postId", contains(id)));
        // "strong" is only the name of a tag in this job, so it must not match it
        String body = mvc.perform(get("/jobs/search?q=strong&size=50&sort=newest")).andReturn().getResponse()
                .getContentAsString();
        List<Integer> strongMatches = JsonPath.read(body, "$.content[*].postId");
        assertFalse(strongMatches.contains(id), "a tag name is not a word of the description");
    }

    @Test
    void likeWildcardsInTheSearchAreLiteralCharacters() throws Exception {
        int percent = job("alpha%x");
        int plain = job("alphaZx");
        int underscore = job("beta_x");
        int other = job("betaQx");
        mvc.perform(get("/jobs/search").param("q", tag + " alpha%")).andExpect(jsonPath("$.content[*].postId", contains(percent)));
        mvc.perform(get("/jobs/search?q=" + tag + " beta_")).andExpect(jsonPath("$.content[*].postId", contains(underscore)));
        assertNotNull(plain);
        assertNotNull(other);
        // a lone wildcard must not turn into "match everything": only jobs that really contain a % or _ come back
        String body = mvc.perform(get("/jobs/search").param("q", "%").param("size", "50")).andReturn().getResponse().getContentAsString();
        List<Integer> percentOnly = JsonPath.read(body, "$.content[*].postId");
        assertFalse(percentOnly.contains(plain), "q=% must not match a job without a percent sign");
    }

    @Test
    void locationIsAContainsMatchOnItsOwn() throws Exception {
        int berlin = job("l1", "<p>x</p>", null, null, "Berlin, Germany", null, null);
        job("l2", "<p>x</p>", null, null, "Madrid", null, null);
        search("location=BERLIN").andExpect(jsonPath("$.content[*].postId", contains(berlin)));
        search("location=germ").andExpect(jsonPath("$.content[*].postId", contains(berlin)));
        search("location=Oslo").andExpect(jsonPath("$.content", empty()));
    }

    // --------------------------------------------------------------------------------------- enum filters

    @Test
    void workModeAndEmploymentTypeAreRepeatable() throws Exception {
        int remote = job("w1", "<p>x</p>", null, null, null, "REMOTE", "FULL_TIME");
        int hybrid = job("w2", "<p>x</p>", null, null, null, "HYBRID", "B2B");
        int onsite = job("w3", "<p>x</p>", null, null, null, "ONSITE", "FULL_TIME");
        int unset = job("w4");

        search("workMode=REMOTE").andExpect(jsonPath("$.content[*].postId", contains(remote)));
        search("workMode=REMOTE&workMode=HYBRID").andExpect(jsonPath("$.content[*].postId", containsInAnyOrder(remote, hybrid)));
        search("employmentType=FULL_TIME").andExpect(jsonPath("$.content[*].postId", containsInAnyOrder(remote, onsite)));
        search("employmentType=B2B&employmentType=PART_TIME").andExpect(jsonPath("$.content[*].postId", contains(hybrid)));
        // together they narrow (AND between filters, OR within one)
        search("workMode=REMOTE&workMode=ONSITE&employmentType=FULL_TIME")
                .andExpect(jsonPath("$.content[*].postId", containsInAnyOrder(remote, onsite)));
        search("workMode=REMOTE&employmentType=B2B").andExpect(jsonPath("$.content", empty()));
        // a job without the field is never matched by a filter on it
        assertFalse(ids("workMode=REMOTE&workMode=HYBRID&workMode=ONSITE").contains(unset));
    }

    // -------------------------------------------------------------------------------------------------- rate

    @Test
    void rateFilterComparesOnTheHourlyEquivalentAndExcludesContractTotals() throws Exception {
        int hourly = job("r1", "<p>x</p>", 50, "HOURLY", null, null, null);
        int monthly = job("r2", "<p>x</p>", 8666.5, "MONTHLY", null, null, null);   // 50.0001 per hour
        int yearly = job("r3", "<p>x</p>", 104000, "YEARLY", null, null, null);     // 50 per hour
        int cheap = job("r4", "<p>x</p>", 20, "HOURLY", null, null, null);
        int contract = job("r5", "<p>x</p>", 20000, "CONTRACT_TOTAL", null, null, null);

        search("minRate=45&maxRate=55").andExpect(jsonPath("$.content[*].postId", containsInAnyOrder(hourly, monthly, yearly)));
        search("minRate=45").andExpect(jsonPath("$.content[*].postId", containsInAnyOrder(hourly, monthly, yearly)));
        search("maxRate=25").andExpect(jsonPath("$.content[*].postId", contains(cheap)));
        // no rate bound: the contract total is just another job
        search("").andExpect(jsonPath("$.totalElements").value(5));
        assertFalse(ids("minRate=0").contains(contract), "any rate bound leaves out contract totals");
        assertFalse(ids("maxRate=1000000").contains(contract));
    }

    @Test
    void rateBoundsAreInclusiveAtCentPrecision() throws Exception {
        int id = job("cents", "<p>x</p>", 6000, "MONTHLY", null, null, null);   // 34.616.. -> 34.62 per hour
        assertTrue(ids("minRate=34.62").contains(id));
        assertTrue(ids("maxRate=34.62").contains(id));
        assertFalse(ids("minRate=34.63").contains(id));
        assertFalse(ids("maxRate=34.61").contains(id));
        assertTrue(ids("minRate=34.62&maxRate=34.62").contains(id));
    }

    // ------------------------------------------------------------------------------------------------ skills

    @Test
    void skillsAnyAllAndCaseInsensitive() throws Exception {
        int both = job("s1", "<p>x</p>", null, null, null, null, null, "Java" + tag, "Spring" + tag);
        int javaOnly = job("s2", "<p>x</p>", null, null, null, null, null, "Java" + tag);
        int springOnly = job("s3", "<p>x</p>", null, null, null, null, null, "Spring" + tag);
        int none = job("s4");

        search("skills=java" + tag).andExpect(jsonPath("$.content[*].postId", containsInAnyOrder(both, javaOnly)));
        search("skills=JAVA" + tag + "&skills=spring" + tag).andExpect(jsonPath("$.content[*].postId",
                containsInAnyOrder(both, javaOnly, springOnly)));
        search("skills=java" + tag + "," + "spring" + tag + "&skillsMatch=ALL")
                .andExpect(jsonPath("$.content[*].postId", contains(both)));
        search("skills=java" + tag + "&skills=nothing" + tag + "&skillsMatch=all")
                .andExpect(jsonPath("$.content", empty()));
        search("skills=unknown" + tag).andExpect(jsonPath("$.content", empty()));
        assertFalse(ids("skills=java" + tag).contains(none));
    }

    @Test
    void aJobMatchingSeveralRequestedSkillsAppearsOnceAndIsCountedOnce() throws Exception {
        job("once", "<p>x</p>", null, null, null, null, null, "A" + tag, "B" + tag, "C" + tag);
        search("skills=a" + tag + "&skills=b" + tag + "&skills=c" + tag)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ------------------------------------------------------------------------------------------- availability

    @Test
    void availableTrueFalseAllAndTheClosedJobCanBeFound() throws Exception {
        int open = job("open");
        int closed = job("closed");
        close(closed);
        search("").andExpect(jsonPath("$.content[*].postId", contains(open)));
        search("available=true").andExpect(jsonPath("$.content[*].postId", contains(open)));
        search("available=false").andExpect(jsonPath("$.content[*].postId", contains(closed)));
        search("available=all").andExpect(jsonPath("$.content[*].postId", contains(closed, open)));
    }

    // ----------------------------------------------------------------------------------------------- sorting

    @Test
    void newestOldestAndTitleSorts() throws Exception {
        int b = job("bravo");
        int a = job("Alpha");
        int c = job("charlie");
        assertEquals(List.of(c, a, b), ids("sort=newest"));
        assertEquals(List.of(b, a, c), ids("sort=oldest"));
        assertEquals(List.of(a, b, c), ids("sort=title"), "title order ignores case");
    }

    @Test
    void rateSortsUseTheHourlyEquivalentAndPutContractTotalsLast() throws Exception {
        int ten = job("p1", "<p>x</p>", 10, "HOURLY", null, null, null);
        int monthly = job("p2", "<p>x</p>", 8666.5, "MONTHLY", null, null, null);  // about 50 per hour
        int yearly = job("p3", "<p>x</p>", 41600, "YEARLY", null, null, null);      // 20 per hour
        int contract = job("p4", "<p>x</p>", 1, "CONTRACT_TOTAL", null, null, null);

        assertEquals(List.of(monthly, yearly, ten, contract), ids("sort=rateDesc"));
        assertEquals(List.of(ten, yearly, monthly, contract), ids("sort=rateAsc"));
    }

    @Test
    void equalRatesKeepAStableOrderSoPagesNeverOverlap() throws Exception {
        for (int i = 0; i < 5; i++) job("same" + i, "<p>x</p>", 50, "HOURLY", null, null, null);
        List<Integer> all = ids("sort=rateDesc&size=50");
        List<Integer> walked = new java.util.ArrayList<>();
        for (int page = 0; page < 3; page++) walked.addAll(ids("sort=rateDesc&size=2&page=" + page));
        assertEquals(all, walked);
        assertEquals(5, walked.stream().distinct().count());
    }

    // -------------------------------------------------------------------------------------------------- paging

    @Test
    void pagingReportsTotalsAndTheLastPage() throws Exception {
        for (int i = 0; i < 5; i++) job("page" + i);
        search("size=2&page=0").andExpect(jsonPath("$.content.length()").value(2)).andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.last").value(false));
        search("size=2&page=1").andExpect(jsonPath("$.content.length()").value(2)).andExpect(jsonPath("$.last").value(false));
        search("size=2&page=2").andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.last").value(true));
        search("size=2&page=3").andExpect(status().isOk()).andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.last").value(true)).andExpect(jsonPath("$.totalElements").value(5));
        search("size=50").andExpect(jsonPath("$.size").value(50)).andExpect(jsonPath("$.content.length()").value(5));
    }

    // -------------------------------------------------------------------------------------------- validation

    private void bad(String query, String message) throws Exception {
        mvc.perform(get("/jobs/search?" + query)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void badInputIs400WithAReadableMessage() throws Exception {
        bad("sort=price", "sort must be one of: newest, oldest, rateDesc, rateAsc, title.");
        bad("size=51", "size must be between 1 and 50.");
        bad("size=0", "size must be between 1 and 50.");
        bad("page=-1", "page must be 0 or more.");
        bad("minRate=90&maxRate=40", "minRate must not be greater than maxRate.");
        bad("minRate=-5", "minRate must be 0 or more.");
        bad("skillsMatch=SOME", "skillsMatch must be ANY or ALL.");
        bad("available=maybe", "available must be true, false or all.");
        bad("q=" + "x".repeat(101), "q must be at most 100 characters.");
    }

    @Test
    void anUnknownEnumValueIs400NamingTheParameter() throws Exception {
        mvc.perform(get("/jobs/search?workMode=UNDERWATER")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("workMode")));
        mvc.perform(get("/jobs/search?employmentType=GIG")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("employmentType")));
        mvc.perform(get("/jobs/search?minRate=lots")).andExpect(status().isBadRequest());
        mvc.perform(get("/jobs/search?page=first")).andExpect(status().isBadRequest());
    }

    @Test
    void aSortValueCannotReachTheQuery() throws Exception {
        job("inject");
        mvc.perform(get("/jobs/search?q=" + tag + "&sort=postId;drop table job_posts")).andExpect(status().isBadRequest());
        search("").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    // --------------------------------------------------------------------------------------------- the backfill

    @Test
    void jobsSavedBeforeThePlainTextColumnGetItFromTheBackfillOnce() throws Exception {
        int id = job("old", "<p>legacy<b>word" + tag + "</b></p>", null, null, null, null, null);
        jdbc.update("update job_posts set description_text = null where post_id = ?", id);
        assertTrue(ids("").contains(id), "found by its title even before the backfill");
        mvc.perform(get("/jobs/search?q=legacyword" + tag)).andExpect(jsonPath("$.content", empty()));

        backfill.run(null);

        JobPost reloaded = jobRepo.findById(id).orElseThrow();
        assertEquals("legacyword" + tag, reloaded.getDescriptionText().replace(" ", ""));
        mvc.perform(get("/jobs/search?q=word" + tag)).andExpect(jsonPath("$.content[*].postId", contains(id)));
        backfill.run(null); // nothing left to do, and no error
    }

    @Test
    void editingADescriptionKeepsTheSearchTextInStep() throws Exception {
        int id = job("edit", "<p>before" + tag + "</p>", null, null, null, null, null);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/jobs/" + id)
                        .header(AUTHORIZATION, employer).contentType(APPLICATION_JSON)
                        .content("{\"jobTitle\":\"" + tag + " edit\",\"jobDescription\":\"<p>after" + tag
                                + "</p>\",\"rate\":50,\"rateType\":\"HOURLY\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/jobs/search?q=after" + tag)).andExpect(jsonPath("$.content[*].postId", contains(id)));
        mvc.perform(get("/jobs/search?q=before" + tag)).andExpect(jsonPath("$.content", empty()));
    }
}
