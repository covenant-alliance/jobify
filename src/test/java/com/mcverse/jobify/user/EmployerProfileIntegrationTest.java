package com.mcverse.jobify.user;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The employer profile and its one company: who may create and change what. */
@SpringBootTest
class EmployerProfileIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String register(String role) throws Exception {
        String username = role.toLowerCase().charAt(0) + "_" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(
                        "{\"username\":\"" + username + "\",\"password\":\"Employer-Pass-4321\",\"firstName\":\"Eve\","
                                + "\"lastName\":\"Boss\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private String createCompany(String token, String name) throws Exception {
        return JsonPath.read(mvc.perform(post("/users/companies").header("Authorization", token)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void employerReadsAndUpdatesTheirName() throws Exception {
        String token = register("EMPLOYER");
        mvc.perform(get("/users/employers/me").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Eve"))
                .andExpect(jsonPath("$.company").doesNotExist());
        mvc.perform(put("/users/employers/me").header("Authorization", token).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Evelyn\",\"lastName\":\"Chief\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Evelyn"))
                .andExpect(jsonPath("$.lastName").value("Chief"));
    }

    @Test
    void blankNameIs400AndSeekersHaveNoEmployerProfile() throws Exception {
        String employer = register("EMPLOYER");
        mvc.perform(put("/users/employers/me").header("Authorization", employer).contentType(APPLICATION_JSON)
                .content("{\"name\":\" \",\"lastName\":\"X\"}")).andExpect(status().isBadRequest());
        String seeker = register("SEEKER");
        mvc.perform(get("/users/employers/me").header("Authorization", seeker)).andExpect(status().isNotFound());
        mvc.perform(put("/users/employers/me").header("Authorization", seeker).contentType(APPLICATION_JSON)
                .content("{\"name\":\"A\",\"lastName\":\"B\"}")).andExpect(status().isNotFound());
    }

    @Test
    void anEmployerCanBeReadByIdAndAnUnknownIdIs404() throws Exception {
        String token = register("EMPLOYER");
        String id = JsonPath.read(mvc.perform(get("/users/employers/me").header("Authorization", token))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/users/employers/" + id).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/users/employers/nope").header("Authorization", token)).andExpect(status().isNotFound());
    }

    @Test
    void anEmployerHasOneCompanyWhichTheyCanRename() throws Exception {
        String token = register("EMPLOYER");
        String id = createCompany(token, "Acme Ltd");
        mvc.perform(get("/users/employers/me").header("Authorization", token))
                .andExpect(jsonPath("$.company.name").value("Acme Ltd"));
        mvc.perform(post("/users/companies").header("Authorization", token).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Second\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Employer already has a company. Update it instead."));
        mvc.perform(put("/users/companies/" + id).header("Authorization", token).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Acme Group\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Acme Group"));
        mvc.perform(get("/companies/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Acme Group"));
    }

    @Test
    void anotherEmployerCannotChangeTheCompany() throws Exception {
        String owner = register("EMPLOYER");
        String id = createCompany(owner, "Owner Co");
        String other = register("EMPLOYER");
        mvc.perform(put("/users/companies/" + id).header("Authorization", other).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Hijacked\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("You do not own this company."));
        // an employer with no company at all gets the same answer
        mvc.perform(put("/users/companies/" + id).header("Authorization", register("EMPLOYER"))
                .contentType(APPLICATION_JSON).content("{\"name\":\"Hijacked\"}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/companies/" + id)).andExpect(jsonPath("$.name").value("Owner Co"));
    }

    @Test
    void companyNameIsValidatedAndSeekersCannotCreateCompanies() throws Exception {
        String employer = register("EMPLOYER");
        mvc.perform(post("/users/companies").header("Authorization", employer).contentType(APPLICATION_JSON)
                .content("{\"name\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/users/companies").header("Authorization", employer).contentType(APPLICATION_JSON)
                .content("{\"name\":\"" + "x".repeat(300) + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/users/companies").header("Authorization", register("SEEKER")).contentType(APPLICATION_JSON)
                .content("{\"name\":\"Nope\"}")).andExpect(status().isNotFound());
    }

    @Test
    void anUnknownCompanyIs404ForThePublicLookup() throws Exception {
        mvc.perform(get("/companies/does-not-exist")).andExpect(status().isNotFound());
    }
}
