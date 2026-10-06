package com.mcverse.jobify.admin;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P7: GET /admin/users with paging, search, role filter and sort. */
@SpringBootTest
class AdminUsersIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin = bearer(mvc, "admin");
    }

    private org.springframework.test.web.servlet.ResultActions users(String query) throws Exception {
        return mvc.perform(get("/admin/users" + query).header(AUTHORIZATION, admin));
    }

    // ── shape ─────────────────────────────────────────────────────────────────

    @Test
    void returnsAPagedEnvelopeWithProfileDetails() throws Exception {
        users("?q=alice_s")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].username").value("alice_s"))
                .andExpect(jsonPath("$.content[0].role").value("SEEKER"))
                .andExpect(jsonPath("$.content[0].name").value("Alice"))
                .andExpect(jsonPath("$.content[0].lastName").value("Johnson"))
                .andExpect(jsonPath("$.content[0].id").isNumber())
                .andExpect(jsonPath("$.content[0].profileId").isNotEmpty())
                .andExpect(jsonPath("$.content[0].creationDate").isNotEmpty());
    }

    @Test
    void adminAccountsHaveNoProfileDetails() throws Exception {
        users("?q=admin&role=ADMIN")
                .andExpect(jsonPath("$.content[0].username").value("admin"))
                .andExpect(jsonPath("$.content[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.content[0].name").doesNotExist())
                .andExpect(jsonPath("$.content[0].profileId").doesNotExist());
    }

    @Test
    void passwordsNeverAppearInTheResponse() throws Exception {
        String body = users("?size=100").andReturn().getResponse().getContentAsString();
        assertTrue(!body.toLowerCase().contains("password"), "response must not mention passwords");
    }

    // ── search ────────────────────────────────────────────────────────────────

    @Test
    void searchMatchesUsernameFirstNameAndLastNameInAnyCase() throws Exception {
        users("?q=ALICE").andExpect(jsonPath("$.content[*].username", hasItem("alice_s")));
        users("?q=carol").andExpect(jsonPath("$.content[*].username", hasItem("carol_s")));
        users("?q=MARTINEZ").andExpect(jsonPath("$.content[*].username", hasItem("carol_s")));
        users("?q=chen").andExpect(jsonPath("$.content[*].username", hasItem("techcorp")));
        users("?q=  techcorp  ").andExpect(jsonPath("$.content[*].username", hasItem("techcorp")));
    }

    @Test
    void searchWildcardsAreLiteralCharacters() throws Exception {
        users("?q=e_s").andExpect(jsonPath("$.content[*].username", hasItem("alice_s")));
        users("?q=e%25s").andExpect(jsonPath("$.totalElements").value(0)); // a literal "e%s" matches nobody
        users("?q=%25").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void noMatchIsAnEmptyPageNotAnError() throws Exception {
        users("?q=zzz-nobody-has-this")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    // ── role filter ───────────────────────────────────────────────────────────

    @Test
    void roleFilterNarrowsTheResults() throws Exception {
        users("?role=EMPLOYER&size=100")
                .andExpect(jsonPath("$.content[*].role", everyItem(equalTo("EMPLOYER"))))
                .andExpect(jsonPath("$.content[*].username", hasItem("techcorp")))
                .andExpect(jsonPath("$.content[*].username", not(hasItem("alice_s"))));
        users("?role=SEEKER&q=bob").andExpect(jsonPath("$.content[*].username", hasItem("bob_s")));
        users("?role=EMPLOYER&q=bob").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void unknownRoleIs400WithTheAllowedValues() throws Exception {
        users("?role=BOSS")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("role has an invalid value")))
                .andExpect(jsonPath("$.message", containsString("EMPLOYER")));
    }

    // ── paging and sort ───────────────────────────────────────────────────────

    @Test
    void pagingWalksThroughEveryAccountExactlyOnce() throws Exception {
        long total = ((Number) JsonPath.read(users("?size=100").andReturn().getResponse().getContentAsString(),
                "$.totalElements")).longValue();
        assertTrue(total >= 7, "the seeded accounts should exist");

        java.util.List<String> seen = new java.util.ArrayList<>();
        int page = 0;
        boolean last;
        do {
            String body = users("?size=3&page=" + page).andReturn().getResponse().getContentAsString();
            seen.addAll(JsonPath.<List<String>>read(body, "$.content[*].username"));
            last = JsonPath.read(body, "$.last");
            assertEquals(page, (int) JsonPath.read(body, "$.page"));
            page++;
        } while (!last);

        assertEquals(total, seen.size());
        assertEquals(total, seen.stream().distinct().count());
        assertEquals(seen.stream().sorted().toList(), seen); // default order is by username
    }

    @Test
    void aPageBeyondTheEndIsEmptyAndMarkedLast() throws Exception {
        users("?page=9999&size=20")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void sortOptionsChangeTheOrder() throws Exception {
        String byName = users("?size=100&sort=username").andReturn().getResponse().getContentAsString();
        List<String> names = JsonPath.read(byName, "$.content[*].username");
        assertEquals(names.stream().sorted().toList(), names);

        String newest = users("?size=100&sort=newest").andReturn().getResponse().getContentAsString();
        List<Integer> newestIds = JsonPath.read(newest, "$.content[*].id");
        assertEquals(newestIds.stream().sorted(java.util.Comparator.reverseOrder()).toList(), newestIds);

        String oldest = users("?size=100&sort=oldest").andReturn().getResponse().getContentAsString();
        List<Integer> oldestIds = JsonPath.read(oldest, "$.content[*].id");
        assertEquals(oldestIds.stream().sorted().toList(), oldestIds);

        users("?size=100&sort=role").andExpect(jsonPath("$.content[0].role").value("ADMIN"));
    }

    @Test
    void aNewlyRegisteredAccountAppearsFirstInNewest() throws Exception {
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"admin_list_new\",\"password\":\"a-decent-passphrase\","
                                + "\"role\":\"SEEKER\",\"firstName\":\"Newly\",\"lastName\":\"Added\"}"))
                .andExpect(status().isCreated());
        users("?sort=newest&size=1").andExpect(jsonPath("$.content[0].username").value("admin_list_new"));
    }

    // ── validation ────────────────────────────────────────────────────────────

    @Test
    void badPagingAndSortAreReadable400s() throws Exception {
        users("?size=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("size must be between 1 and 100."));
        users("?size=101").andExpect(status().isBadRequest());
        users("?page=-1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("page must be 0 or more."));
        users("?sort=password").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("sort must be one of: username, role, newest, oldest."));
        users("?size=abc").andExpect(status().isBadRequest());
        users("?q=" + "a".repeat(101)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("q must be at most 100 characters."));
    }

    // ── access ────────────────────────────────────────────────────────────────

    @Test
    void onlyAdminsMayListAccounts() throws Exception {
        for (String user : new String[] {"alice_s", "techcorp"}) {
            mvc.perform(get("/admin/users").header(AUTHORIZATION, bearer(mvc, user)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/admin/users")).andExpect(status().isUnauthorized());
    }
}
