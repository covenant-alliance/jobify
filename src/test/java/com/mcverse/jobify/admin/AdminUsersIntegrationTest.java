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
        // not the bare word: a test account may legitimately be called "something_password"
        assertTrue(!body.toLowerCase().contains("\"password\""), "no password field in the response");
        assertTrue(!body.contains("$2a$") && !body.contains("$2b$") && !body.contains("$2y$"),
                "no password hash in the response");
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

    /** The seeded accounts: plain lowercase names that sort the same under every database collation. */
    private static final List<String> SEEDED = List.of("admin", "alice_s", "bob_s", "carol_s", "financegroup",
            "startupxyz", "techcorp");

    /**
     * Usernames are ordered by the database's own collation (PostgreSQL's en_US ignores punctuation such as "_",
     * H2 and the C locale do not), so tests must not assume Java's String order for names made of arbitrary
     * characters. Only the relative order of {@link #SEEDED} is the same everywhere.
     */
    private static List<String> onlySeeded(List<String> names) {
        return names.stream().filter(SEEDED::contains).toList();
    }

    private List<String> walk(int size) throws Exception {
        java.util.List<String> seen = new java.util.ArrayList<>();
        int page = 0;
        boolean last;
        do {
            String body = users("?size=" + size + "&page=" + page).andReturn().getResponse().getContentAsString();
            seen.addAll(JsonPath.<List<String>>read(body, "$.content[*].username"));
            last = JsonPath.read(body, "$.last");
            assertEquals(page, (int) JsonPath.read(body, "$.page"));
            page++;
        } while (!last);
        return seen;
    }

    @Test
    void pagingWalksThroughEveryAccountExactlyOnce() throws Exception {
        long total = ((Number) JsonPath.read(users("?size=100").andReturn().getResponse().getContentAsString(),
                "$.totalElements")).longValue();
        assertTrue(total >= 7, "the seeded accounts should exist");

        List<String> bySmallPages = walk(3);
        assertEquals(total, bySmallPages.size());
        assertEquals(total, bySmallPages.stream().distinct().count());
        // the order must not depend on how the list is cut into pages
        assertEquals(bySmallPages, walk(7));
        assertEquals(bySmallPages, walk(100));
        // and by default it is by username (checked on the accounts whose order is the same under every collation)
        assertEquals(SEEDED.stream().sorted().toList(), onlySeeded(bySmallPages));
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
        assertEquals(SEEDED.stream().sorted().toList(), onlySeeded(walk(100)), "username order, on the seeded accounts");
        assertEquals(names, walk(100).subList(0, names.size()), "the first page is the start of the same ordering");

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
