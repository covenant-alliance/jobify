package com.mcverse.jobify.user;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The seeker profile: name, and the four sections (education, certifications, experiences, skills) with full CRUD.
 * Every section is checked the same way: create, list, update, delete, bad input, and that another seeker cannot
 * see or touch the item. Each test registers its own users, so it does not depend on the seeded data.
 */
@SpringBootTest
class SeekerProfileIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** Registers a new account and returns its Authorization header value. */
    private String register(String role) throws Exception {
        String username = role.toLowerCase().charAt(0) + "_" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(
                        "{\"username\":\"" + username + "\",\"password\":\"Profile-Pass-4321\",\"firstName\":\"Pat\","
                                + "\"lastName\":\"Doe\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    // ------------------------------------------------------------------------------------------ the profile itself

    @Test
    void seekerReadsAndUpdatesTheirName() throws Exception {
        String token = register("SEEKER");
        mvc.perform(get("/users/seekers/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pat"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.educations", hasSize(0)));
        mvc.perform(put("/users/seekers/me").header("Authorization", token).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Patricia\",\"lastName\":\"Smith\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Patricia"))
                .andExpect(jsonPath("$.lastName").value("Smith"));
        mvc.perform(get("/users/seekers/me").header("Authorization", token))
                .andExpect(jsonPath("$.name").value("Patricia"));
    }

    @Test
    void blankNameIsRejected() throws Exception {
        String token = register("SEEKER");
        mvc.perform(put("/users/seekers/me").header("Authorization", token).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"\",\"lastName\":\"Smith\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void aSeekerCanBeReadByIdAndAnUnknownIdIs404() throws Exception {
        String token = register("SEEKER");
        String id = JsonPath.read(mvc.perform(get("/users/seekers/me").header("Authorization", token))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/users/seekers/" + id).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/users/seekers/does-not-exist").header("Authorization", token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Pins today's behaviour: no valid token is answered 403, not 401 (issue #50 proposes 401, which is what the front
     * end uses to log the user out). When #50 is done, change these expectations.
     */
    @Test
    void profileRoutesRefuseCallersWithoutAValidToken() throws Exception {
        mvc.perform(get("/users/seekers/me")).andExpect(status().isForbidden());
        mvc.perform(get("/users/seekers/me/educations")).andExpect(status().isForbidden());
        mvc.perform(post("/users/seekers/me/skills").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/users/seekers/me").header("Authorization", "Bearer not.a.real-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anEmployerHasNoSeekerProfile() throws Exception {
        String employer = register("EMPLOYER");
        mvc.perform(get("/users/seekers/me").header("Authorization", employer)).andExpect(status().isNotFound());
        mvc.perform(get("/users/seekers/me/educations").header("Authorization", employer))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------------------------------- the four sections

    private record Section(String path, String create, String update, String field, String created, String updated,
                           String invalid) {}

    static Stream<Arguments> sections() {
        return Stream.of(
                Arguments.of(new Section("educations",
                        "{\"institution\":\"MIT\",\"degree\":\"BSc\",\"fieldOfStudy\":\"CS\","
                                + "\"startDate\":\"2018-09-01\",\"endDate\":\"2022-06-30\",\"grade\":\"A\"}",
                        "{\"institution\":\"Stanford\",\"degree\":\"MSc\",\"fieldOfStudy\":\"AI\","
                                + "\"startDate\":\"2022-09-01\"}",
                        "institution", "MIT", "Stanford", "{\"institution\":\"\"}")),
                Arguments.of(new Section("certifications",
                        "{\"name\":\"AWS Architect\",\"issuingOrganization\":\"Amazon\",\"issueDate\":\"2024-01-10\","
                                + "\"credentialUrl\":\"https://example.com/c/1\"}",
                        "{\"name\":\"AWS Professional\",\"issuingOrganization\":\"Amazon\",\"issueDate\":\"2025-02-01\"}",
                        "name", "AWS Architect", "AWS Professional",
                        "{\"name\":\"X\",\"issuingOrganization\":\"Y\",\"issueDate\":\"2024-01-10\","
                                + "\"credentialUrl\":\"ftp://nope\"}")),
                Arguments.of(new Section("experiences",
                        "{\"jobTitle\":\"Developer\",\"companyName\":\"Acme\",\"location\":\"Berlin\","
                                + "\"employmentType\":\"FULL_TIME\",\"startDate\":\"2020-01-01\","
                                + "\"endDate\":\"2022-01-01\",\"description\":\"Built things\"}",
                        "{\"jobTitle\":\"Senior Developer\",\"companyName\":\"Acme\",\"startDate\":\"2020-01-01\"}",
                        "jobTitle", "Developer", "Senior Developer", "{\"jobTitle\":\"Dev\"}")),
                Arguments.of(new Section("skills",
                        "{\"skillName\":\"Kotlin\",\"category\":\"Language\",\"proficiencyLevel\":\"ADVANCED\","
                                + "\"yearsOfExperience\":5}",
                        "{\"skillName\":\"Kotlin\",\"proficiencyLevel\":\"EXPERT\",\"yearsOfExperience\":7}",
                        "proficiencyLevel", "ADVANCED", "EXPERT",
                        "{\"skillName\":\"Kotlin\",\"proficiencyLevel\":\"ADVANCED\",\"yearsOfExperience\":99}")));
    }

    private String url(Section s) { return "/users/seekers/me/" + s.path(); }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sections")
    void createListUpdateAndDelete(Section s) throws Exception {
        String token = register("SEEKER");
        String body = mvc.perform(post(url(s)).header("Authorization", token).contentType(APPLICATION_JSON)
                        .content(s.create()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$." + s.field()).value(s.created()))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mvc.perform(get(url(s)).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/users/seekers/me").header("Authorization", token))
                .andExpect(jsonPath("$." + s.path(), hasSize(1)));

        mvc.perform(put(url(s) + "/" + id).header("Authorization", token).contentType(APPLICATION_JSON)
                        .content(s.update()))
                .andExpect(status().isOk()).andExpect(jsonPath("$." + s.field()).value(s.updated()));

        mvc.perform(delete(url(s) + "/" + id).header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get(url(s)).header("Authorization", token)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete(url(s) + "/" + id).header("Authorization", token)).andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sections")
    void invalidInputIs400(Section s) throws Exception {
        String token = register("SEEKER");
        mvc.perform(post(url(s)).header("Authorization", token).contentType(APPLICATION_JSON).content(s.invalid()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400));
        mvc.perform(post(url(s)).header("Authorization", token).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(url(s)).header("Authorization", token)).andExpect(jsonPath("$", hasSize(0)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sections")
    void anotherSeekerCannotSeeUpdateOrDeleteTheItem(Section s) throws Exception {
        String owner = register("SEEKER");
        String other = register("SEEKER");
        String id = JsonPath.read(mvc.perform(post(url(s)).header("Authorization", owner)
                        .contentType(APPLICATION_JSON).content(s.create()))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get(url(s)).header("Authorization", other)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(put(url(s) + "/" + id).header("Authorization", other).contentType(APPLICATION_JSON)
                .content(s.update())).andExpect(status().isNotFound());
        mvc.perform(delete(url(s) + "/" + id).header("Authorization", other)).andExpect(status().isNotFound());
        // still there and unchanged for the owner
        mvc.perform(get(url(s)).header("Authorization", owner))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0]." + s.field()).value(s.created()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sections")
    void unknownIdIs404(Section s) throws Exception {
        String token = register("SEEKER");
        mvc.perform(put(url(s) + "/no-such-id").header("Authorization", token).contentType(APPLICATION_JSON)
                .content(s.update())).andExpect(status().isNotFound());
        mvc.perform(delete(url(s) + "/no-such-id").header("Authorization", token)).andExpect(status().isNotFound());
    }

    @Test
    void anEndDateBeforeTheStartDateIs422ForEducationAndExperience() throws Exception {
        String token = register("SEEKER");
        mvc.perform(post("/users/seekers/me/educations").header("Authorization", token)
                        .contentType(APPLICATION_JSON).content("{\"institution\":\"X\",\"degree\":\"Y\","
                                + "\"fieldOfStudy\":\"Z\",\"startDate\":\"2022-01-01\",\"endDate\":\"2021-01-01\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("End date cannot be before start date."));
        mvc.perform(post("/users/seekers/me/experiences").header("Authorization", token)
                        .contentType(APPLICATION_JSON).content("{\"jobTitle\":\"X\",\"companyName\":\"Y\","
                                + "\"startDate\":\"2022-01-01\",\"endDate\":\"2021-01-01\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updatingWithABadDateRangeIs422AndKeepsTheOldValues() throws Exception {
        String token = register("SEEKER");
        String id = JsonPath.read(mvc.perform(post("/users/seekers/me/educations").header("Authorization", token)
                        .contentType(APPLICATION_JSON).content("{\"institution\":\"Keep\",\"degree\":\"Y\","
                                + "\"fieldOfStudy\":\"Z\",\"startDate\":\"2020-01-01\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/users/seekers/me/educations/" + id).header("Authorization", token)
                        .contentType(APPLICATION_JSON).content("{\"institution\":\"Changed\",\"degree\":\"Y\","
                                + "\"fieldOfStudy\":\"Z\",\"startDate\":\"2022-01-01\",\"endDate\":\"2021-01-01\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/users/seekers/me/educations").header("Authorization", token))
                .andExpect(jsonPath("$[0].institution").value("Keep"));
    }

    // ------------------------------------------------------------------------------------------------- skills only

    @Test
    void addingTheSameSkillTwiceIs422ButAnotherSeekerMayAddIt() throws Exception {
        String token = register("SEEKER");
        String skill = "{\"skillName\":\"Rust-" + UUID.randomUUID().toString().substring(0, 6)
                + "\",\"proficiencyLevel\":\"BEGINNER\"}";
        mvc.perform(post("/users/seekers/me/skills").header("Authorization", token).contentType(APPLICATION_JSON)
                .content(skill)).andExpect(status().isCreated());
        mvc.perform(post("/users/seekers/me/skills").header("Authorization", token).contentType(APPLICATION_JSON)
                        .content(skill))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("You already have this skill on your profile. Update it instead."));
        mvc.perform(post("/users/seekers/me/skills").header("Authorization", register("SEEKER"))
                .contentType(APPLICATION_JSON).content(skill)).andExpect(status().isCreated());
    }

    @Test
    void skillNamesMatchTheCatalogueIgnoringCase() throws Exception {
        String name = "Haskell" + UUID.randomUUID().toString().substring(0, 6);
        String first = register("SEEKER");
        mvc.perform(post("/users/seekers/me/skills").header("Authorization", first).contentType(APPLICATION_JSON)
                .content("{\"skillName\":\"" + name + "\",\"proficiencyLevel\":\"EXPERT\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/users/seekers/me/skills").header("Authorization", register("SEEKER"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"skillName\":\"" + name.toUpperCase() + "\",\"proficiencyLevel\":\"BEGINNER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skillName").value(name)); // the catalogue spelling wins
    }

    @Test
    void anUnknownProficiencyLevelIs400() throws Exception {
        mvc.perform(post("/users/seekers/me/skills").header("Authorization", register("SEEKER"))
                        .contentType(APPLICATION_JSON).content("{\"skillName\":\"Go\",\"proficiencyLevel\":\"GURU\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
    }
}
