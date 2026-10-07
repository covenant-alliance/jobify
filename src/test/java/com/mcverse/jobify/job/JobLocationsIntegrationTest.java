package com.mcverse.jobify.job;

import com.jayway.jsonpath.JsonPath;
import com.mcverse.jobify.job.repository.JobRepo;
import com.mcverse.jobify.job.service.LocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

/** Places (#37): free-text locations resolved to clean, shared values; the facet list; the search filter. */
@SpringBootTest
class JobLocationsIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JobRepo jobRepo;
    @Autowired
    private LocationService locationService;

    private MockMvc mvc;
    private String employer;
    /** A city name no other test uses, letters only, e.g. "Qkbdcfhaej". */
    private String city;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        StringBuilder letters = new StringBuilder("Q");
        for (char c : UUID.randomUUID().toString().replace("-", "").substring(0, 10).toCharArray()) {
            letters.append((char) (c >= 'a' ? c : 'a' + (c - '0')));
        }
        city = letters.toString();
        String username = "lo_" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(
                        "{\"username\":\"" + username + "\",\"password\":\"Place-Pass-4321\",\"firstName\":\"L\","
                                + "\"lastName\":\"O\",\"role\":\"EMPLOYER\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        employer = "Bearer " + JsonPath.read(body, "$.token");
    }

    // ---------------------------------------------------------------------------------------------- helpers

    private String jobJson(String location) {
        return "{\"jobTitle\":\"" + city + " job\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,\"rateType\":\"HOURLY\","
                + "\"location\":\"" + location + "\",\"workMode\":\"REMOTE\",\"employmentType\":\"FULL_TIME\"}";
    }

    private String create(String location) throws Exception {
        return mvc.perform(post("/jobs").header(AUTHORIZATION, employer).contentType(APPLICATION_JSON)
                        .content(jobJson(location))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String placeOf(String location) throws Exception {
        return JsonPath.read(create(location), "$.locationId");
    }

    private int idOf(String jobJson) {
        return JsonPath.read(jobJson, "$.postId");
    }

    private List<Map<String, Object>> facets(String query) throws Exception {
        String body = mvc.perform(get("/jobs/locations?" + query)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$");
    }

    private Map<String, Object> facetOf(String placeId, String query) throws Exception {
        return facets(query).stream().filter(f -> placeId.equals(f.get("id"))).findFirst().orElse(null);
    }

    private void close(int jobId) throws Exception {
        mvc.perform(patch("/jobs/" + jobId + "/available").header(AUTHORIZATION, employer)
                .contentType(APPLICATION_JSON).content("{\"available\":false}")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------------------------------- merging

    @Test
    void spellingsOfOnePlaceShareOneEntryAndTheTypedTextIsKept() throws Exception {
        String a = create(city + ", DE");
        String b = create(city.toLowerCase() + ", Germany");
        String c = create(city.toUpperCase() + ",Deutschland");
        String id = JsonPath.read(a, "$.locationId");
        assertEquals(id, JsonPath.read(b, "$.locationId"));
        assertEquals(id, JsonPath.read(c, "$.locationId"));
        assertEquals(city.toLowerCase() + ", Germany", JsonPath.read(b, "$.location"), "free text stays as typed");

        List<Map<String, Object>> found = facets("q=" + city);
        assertEquals(1, found.size());
        Map<String, Object> place = found.get(0);
        assertEquals(city + ", Germany", place.get("name"));
        assertEquals(city, place.get("city"));
        assertEquals("DE", place.get("countryCode"));
        assertEquals("Germany", place.get("country"));
        assertEquals(false, place.get("remote"));
        assertEquals(3, ((Number) place.get("jobs")).intValue());
        assertEquals(id, place.get("id"));
    }

    @Test
    void differentCountriesAreDifferentPlacesMostJobsFirst() throws Exception {
        String de = placeOf(city + ", DE");
        String fr = placeOf(city + ", FR");
        placeOf(city + ", FR");
        List<Map<String, Object>> found = facets("q=" + city);
        assertEquals(List.of(fr, de), found.stream().map(f -> f.get("id")).toList());
        assertEquals(2, ((Number) found.get(0).get("jobs")).intValue());
    }

    @Test
    void aCityWithoutACountryJoinsTheOneKnownCountryButNotWhenItIsAmbiguous() throws Exception {
        String known = placeOf(city + ", DE");
        assertEquals(known, placeOf(city));            // exactly one candidate
        String other = placeOf(city + ", FR");
        assertNotEquals(known, other);
        String bare = placeOf(city);                   // now two candidates: stays on its own
        assertNotEquals(known, bare);
        assertNotEquals(other, bare);
        assertNull(facetOf(bare, "q=" + city).get("countryCode"));
    }

    @Test
    void usStatesCarryTheirCountryAndRegion() throws Exception {
        String texas = placeOf(city + ", TX");
        assertEquals(texas, placeOf(city + ", Texas, USA"));
        Map<String, Object> place = facetOf(texas, "q=" + city);
        assertEquals(city + ", TX, United States", place.get("name"));
        assertEquals("TX", place.get("region"));
        assertEquals("US", place.get("countryCode"));
    }

    @Test
    void remoteIsItsOwnKindOfPlace() throws Exception {
        String a = placeOf("Remote — US");
        assertEquals(a, placeOf("Remote, USA"));
        assertEquals(a, placeOf("remote (us)"));
        Map<String, Object> us = facetOf(a, "q=remote&limit=500");
        assertEquals("Remote, United States", us.get("name"));
        assertEquals(true, us.get("remote"));
        assertNull(us.get("city"));

        String plain = placeOf("Remote");
        assertNotEquals(a, plain);
        assertEquals("Remote", facetOf(plain, "q=remote&limit=500").get("name"));

        // a remote job in a city is not the same place as the on-site city
        String onsite = placeOf(city + ", DE");
        String remoteInCity = placeOf(city + ", DE (remote)");
        assertNotEquals(onsite, remoteInCity);
        assertEquals(city + ", Germany (remote)", facetOf(remoteInCity, "q=" + city).get("name"));
    }

    @Test
    void textThatNamesNoPlaceHasNoLocationId() throws Exception {
        String job = create("Hybrid");
        assertNull(JsonPath.read(job, "$.locationId"));
        mvc.perform(get("/jobs/" + idOf(job))).andExpect(jsonPath("$.locationId").doesNotExist())
                .andExpect(jsonPath("$.location").value("Hybrid"));
    }

    // --------------------------------------------------------------------------------------------- the list

    @Test
    void countsCoverOpenJobsByDefaultAndOtherStatesOnRequest() throws Exception {
        String open = create(city + ", DE");
        String closed = create(city + ", DE");
        String place = JsonPath.read(open, "$.locationId");
        close(idOf(closed));
        assertEquals(1, ((Number) facetOf(place, "q=" + city).get("jobs")).intValue());
        assertEquals(1, ((Number) facetOf(place, "q=" + city + "&available=true").get("jobs")).intValue());
        assertEquals(1, ((Number) facetOf(place, "q=" + city + "&available=false").get("jobs")).intValue());
        assertEquals(2, ((Number) facetOf(place, "q=" + city + "&available=all").get("jobs")).intValue());
    }

    @Test
    void aPlaceWithNoJobsInThatStateDoesNotAppear() throws Exception {
        String only = create(city + ", DE");
        close(idOf(only));
        assertTrue(facets("q=" + city).isEmpty());
        assertEquals(1, facets("q=" + city + "&available=false").size());
    }

    @Test
    void nameFilterLimitAndPublicAccess() throws Exception {
        placeOf(city + ", DE");
        placeOf(city + ", FR");
        placeOf(city + ", ES");
        assertEquals(3, facets("q=" + city.toLowerCase()).size());
        assertEquals(1, facets("q=" + city + "&limit=1").size());
        mvc.perform(get("/jobs/locations").param("q", city + ", germany")) // contains match on the shown name
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/jobs/locations").param("q", "%")).andExpect(jsonPath("$", empty())); // a character, not a wildcard
        mvc.perform(get("/jobs/locations").param("q", "_")).andExpect(jsonPath("$", empty()));
        mvc.perform(get("/jobs/locations?q=" + city).header(AUTHORIZATION, "Bearer stale.token.here"))
                .andExpect(status().isOk());
    }

    @Test
    void badParametersAre400WithReadableMessages() throws Exception {
        mvc.perform(get("/jobs/locations?limit=0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("limit must be between 1 and 500."));
        mvc.perform(get("/jobs/locations?limit=501")).andExpect(status().isBadRequest());
        mvc.perform(get("/jobs/locations?available=maybe")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("available must be true, false or all."));
        mvc.perform(get("/jobs/locations?q=" + "x".repeat(101))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("q must be at most 100 characters."));
    }

    // ------------------------------------------------------------------------------------------------ search

    @Test
    void searchFiltersByPlaceIdAndTheFreeTextFilterStillWorks() throws Exception {
        int inDe = idOf(create(city + ", DE"));
        int inFr = idOf(create(city + ", FR"));
        String de = placeOf(city + ", Germany");
        String fr = placeOf(city + ", France");
        mvc.perform(get("/jobs/search").param("q", city).param("locationId", de).param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].location", containsInAnyOrder(city + ", DE", city + ", Germany")))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/jobs/search").param("q", city).param("locationId", de).param("locationId", fr)
                        .param("size", "50")).andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/jobs/search").param("q", city).param("locationId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", empty()));
        mvc.perform(get("/jobs/search").param("q", city).param("location", "france")) // text filter: only ", France"
                .andExpect(jsonPath("$.totalElements").value(1));
        assertNotEquals(inDe, inFr);
    }

    @Test
    void locationIdSearchValidation() throws Exception {
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 21; i++) many.append(many.isEmpty() ? "" : "&").append("locationId=id").append(i);
        mvc.perform(get("/jobs/search?" + many)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("locationId must have at most 20 entries."));
        mvc.perform(get("/jobs/search?locationId=" + "z".repeat(65))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("locationId must be at most 64 characters."));
        mvc.perform(get("/jobs/search?locationId=")).andExpect(status().isOk()); // blank = no filter
    }

    // ---------------------------------------------------------------------------------------------- editing

    @Test
    void editingTheLocationMovesTheJobToItsNewPlace() throws Exception {
        String created = create(city + ", DE");
        int id = idOf(created);
        String before = JsonPath.read(created, "$.locationId");
        mvc.perform(put("/jobs/" + id).header(AUTHORIZATION, employer).contentType(APPLICATION_JSON)
                        .content(jobJson(city + ", Spain")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationId").value(org.hamcrest.Matchers.not(before)))
                .andExpect(jsonPath("$.location").value(city + ", Spain"));
        assertNull(facetOf(before, "q=" + city), "the old place has no jobs left");
        assertEquals(1, facets("q=" + city).size());
    }

    @Test
    void everyJobRouteCarriesTheLocationId() throws Exception {
        String job = create(city + ", DE");
        String place = JsonPath.read(job, "$.locationId");
        mvc.perform(get("/jobs/" + idOf(job))).andExpect(jsonPath("$.locationId").value(place));
        mvc.perform(get("/jobs/mine").header(AUTHORIZATION, employer))
                .andExpect(jsonPath("$[0].locationId").value(place));
        mvc.perform(get("/jobs?available=true")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.postId==" + idOf(job) + ")].locationId", contains(place)));
    }

    // ---------------------------------------------------------------------------------------------- backfill

    @Test
    void jobsFromBeforePlacesExistGetTheirPlaceFromTheBackfill() throws Exception {
        int id = idOf(create(city + ", DE"));
        int unparseable = idOf(create(city + ", FR"));
        jdbc.update("update job_posts set location_id = null where post_id in (?, ?)", id, unparseable);
        jdbc.update("update job_posts set location = 'hybrid' where post_id = ?", unparseable);

        int filled = locationService.backfill();

        assertTrue(filled >= 1);
        assertNotNull(jdbc.queryForObject("select location_id from job_posts where post_id = ?", String.class, id));
        assertNull(jdbc.queryForObject("select location_id from job_posts where post_id = ?", String.class,
                unparseable));
        locationService.backfill(); // running it again is harmless
        assertEquals(city + ", Germany",
                facets("q=" + city).stream().map(f -> (String) f.get("name")).findFirst().orElseThrow());
        assertTrue(jobRepo.findById(id).isPresent());
    }
}
