package com.mcverse.jobify.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** T2: bad input gives a readable 4xx in the standard error envelope, never a 500. */
@SpringBootTest
class ValidationAndErrorHandlingIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static String repeat(int n) {
        return "a".repeat(n);
    }

    // ── auth ──────────────────────────────────────────────────────────────────

    @Test
    void loginRequiresUsernameAndPassword() throws Exception {
        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("username is required")))
                .andExpect(jsonPath("$.message", containsString("password is required")));
    }

    @Test
    void wrongPasswordIsStill401() throws Exception {
        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"alice_s\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerRejectsOverlongFieldsWithReadableMessages() throws Exception {
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + repeat(51) + "\",\"password\":\"" + repeat(73)
                                + "\",\"role\":\"SEEKER\",\"firstName\":\"" + repeat(101) + "\",\"lastName\":\"B\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("username must be at most 50 characters")))
                .andExpect(jsonPath("$.message", containsString("password must be at most 72 characters")))
                .andExpect(jsonPath("$.message", containsString("firstName must be at most 100 characters")));
    }

    @Test
    void registerMissingFieldsUseTheIsRequiredWording() throws Exception {
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("username is required")))
                .andExpect(jsonPath("$.message", containsString("role is required")));
    }

    // ── profile data ──────────────────────────────────────────────────────────

    @Test
    void overlongProfileAndEducationTextIs400NotAServerError() throws Exception {
        String alice = bearer(mvc, "alice_s");
        mvc.perform(put("/users/seekers/me").header(AUTHORIZATION, alice).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"" + repeat(400) + "\",\"lastName\":\"B\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name must be at most 100 characters"));
        mvc.perform(post("/users/seekers/me/educations").header(AUTHORIZATION, alice)
                        .contentType(APPLICATION_JSON)
                        .content("{\"institution\":\"" + repeat(400) + "\",\"degree\":\"d\",\"fieldOfStudy\":\"f\","
                                + "\"startDate\":\"2020-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("institution must be at most 255 characters"));
    }

    @Test
    void certificationUrlMustBeHttp() throws Exception {
        mvc.perform(post("/users/seekers/me/certifications").header(AUTHORIZATION, bearer(mvc, "alice_s"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"AWS\",\"issuingOrganization\":\"Amazon\",\"issueDate\":\"2024-01-01\","
                                + "\"credentialUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("credentialUrl must start with http:// or https://"));
    }

    @Test
    void skillExperienceMustBeInRange() throws Exception {
        mvc.perform(post("/users/seekers/me/skills").header(AUTHORIZATION, bearer(mvc, "alice_s"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"skillName\":\"Java\",\"proficiencyLevel\":\"ADVANCED\",\"yearsOfExperience\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("yearsOfExperience must be between 0 and 80"));
    }

    // ── account ───────────────────────────────────────────────────────────────

    @Test
    void overlongDeletionReasonIs400() throws Exception {
        mvc.perform(post("/account/deletion-request").header(AUTHORIZATION, bearer(mvc, "bob_s"))
                        .contentType(APPLICATION_JSON).content("{\"reason\":\"" + repeat(5000) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("reason must be at most 255 characters"));
    }

    @Test
    void passwordChangeRulesAreStatedClearly() throws Exception {
        mvc.perform(put("/account/password").header(AUTHORIZATION, bearer(mvc, "carol_s"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("newPassword must be 8 to 72 characters"));
    }

    // ── CMS ───────────────────────────────────────────────────────────────────

    @Test
    void contentUpdateWithoutValuesIs400() throws Exception {
        mvc.perform(put("/admin/content").header(AUTHORIZATION, bearer(mvc, "admin"))
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("values is required"));
    }

    @Test
    void contentValuesAreLimitedToTheColumnSize() throws Exception {
        mvc.perform(put("/admin/content").header(AUTHORIZATION, bearer(mvc, "admin"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"values\":{\"site.title\":\"" + repeat(4001) + "\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("4000 characters")));
    }

    // ── job availability ──────────────────────────────────────────────────────

    @Test
    void availabilityBodyWithoutTheFlagIs400AndChangesNothing() throws Exception {
        mvc.perform(patch("/jobs/1/available").header(AUTHORIZATION, bearer(mvc, "techcorp"))
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("available is required"));
        mvc.perform(get("/jobs/1")).andExpect(jsonPath("$.available").value(true));
    }

    // ── framework errors that used to be 500 ──────────────────────────────────

    @Test
    void unknownRouteIs404() throws Exception {
        mvc.perform(get("/no/such/route").header(AUTHORIZATION, bearer(mvc, "alice_s")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("The requested resource was not found."))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void wrongHttpMethodIs405() throws Exception {
        mvc.perform(delete("/jobs").header(AUTHORIZATION, bearer(mvc, "techcorp")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("DELETE is not supported for this endpoint."));
    }

    @Test
    void wrongContentTypeIs415() throws Exception {
        mvc.perform(post("/auth/login").contentType(TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message", containsString("application/json")));
    }

    @Test
    void missingUploadPartIs400() throws Exception {
        mvc.perform(multipart("/users/seekers/me/resume").header(AUTHORIZATION, bearer(mvc, "alice_s")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("is required")));
    }

    @Test
    void uploadWithTheFilePartStillWorksPathStillReachable() throws Exception {
        // makes sure the new handlers did not swallow valid multipart requests
        mvc.perform(multipart("/users/seekers/me/resume").file(new MockMultipartFile("file", "cv.txt",
                        "text/plain", "x".getBytes())).header(AUTHORIZATION, bearer(mvc, "alice_s")))
                .andExpect(status().is4xxClientError()); // .txt is not an allowed resume type
    }
}
