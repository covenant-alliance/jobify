package com.mcverse.jobify.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Walks every route the application really registers and checks who may call it, so a new endpoint cannot ship
 * unprotected by accident:
 * <ul>
 *   <li>a caller without a token is refused everywhere except the explicit public list below;</li>
 *   <li>the public list really is public (not accidentally locked);</li>
 *   <li>{@code /admin/**} refuses seekers and employers and lets an admin in.</li>
 * </ul>
 * "Refused" is 403 today; issue #50 proposes 401 for callers without a valid token, then {@link #REFUSED} changes.
 */
@SpringBootTest
class RouteSecurityMatrixTest {

    /** Status for a caller without a valid token. Becomes 401 if issue #50 is done. */
    private static final int REFUSED = 403;

    /** Every public route, as "METHOD pattern". Adding to this list is a deliberate, reviewable decision. */
    private static final Set<String> PUBLIC = Set.of(
            "GET /jobs",
            "GET /jobs/{id}",
            "GET /content",
            "GET /companies/{id}",
            "POST /auth/login",
            "POST /auth/register");

    @Autowired
    private WebApplicationContext context;
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** "METHOD pattern" to a concrete request with dummy ids. */
    private Map<String, MockHttpServletRequestBuilder> routes() {
        Map<String, MockHttpServletRequestBuilder> routes = new TreeMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            if (!entry.getValue().getBeanType().getPackageName().startsWith("com.mcverse.jobify")) continue;
            Set<String> patterns = entry.getKey().getPathPatternsCondition().getPatternValues();
            Set<org.springframework.web.bind.annotation.RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
            for (String pattern : patterns) {
                for (var method : methods) {
                    String url = pattern.replaceAll("\\{[^/}]+}", "1");
                    routes.put(method + " " + pattern, MockMvcRequestBuilders
                            .request(HttpMethod.valueOf(method.name()), url)
                            .contentType(APPLICATION_JSON).content("{}"));
                }
            }
        }
        return routes;
    }

    private int status(MockHttpServletRequestBuilder request, String authorization) throws Exception {
        if (authorization != null) request.header("Authorization", authorization);
        MvcResult result = mvc.perform(request).andReturn();
        return result.getResponse().getStatus();
    }

    @Test
    void theWholeApiIsFoundAndNothingIsPublicByAccident() throws Exception {
        Map<String, MockHttpServletRequestBuilder> routes = routes();
        assertTrue(routes.size() >= 55, "expected to find the whole API, found " + routes.size());

        List<String> wronglyOpen = new ArrayList<>();
        for (Map.Entry<String, MockHttpServletRequestBuilder> route : routes.entrySet()) {
            if (PUBLIC.contains(route.getKey())) continue;
            int status = status(route.getValue(), null);
            if (status != REFUSED) wronglyOpen.add(route.getKey() + " answered " + status);
        }
        assertTrue(wronglyOpen.isEmpty(),
                "These routes are reachable without a token and are not on the PUBLIC list: " + wronglyOpen);
    }

    @Test
    void thePublicListMatchesRealRoutesAndTheyAreNotRefused() throws Exception {
        Map<String, MockHttpServletRequestBuilder> routes = routes();
        List<String> problems = new ArrayList<>();
        for (String publicRoute : PUBLIC) {
            MockHttpServletRequestBuilder request = routes.get(publicRoute);
            if (request == null) {
                problems.add(publicRoute + " is on the list but is not a registered route");
                continue;
            }
            int status = status(request, null);
            if (status == 401 || status == 403) problems.add(publicRoute + " answered " + status);
        }
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void adminRoutesRefuseSeekersAndEmployersAndAcceptAnAdmin() throws Exception {
        String seeker = bearer(mvc, "alice_s");
        String employer = bearer(mvc, "techcorp");
        String admin = bearer(mvc, "admin");
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<String, MockHttpServletRequestBuilder> route : routes().entrySet()) {
            if (!route.getKey().contains(" /admin/")) continue;
            checked++;
            for (String who : new String[] {seeker, employer}) {
                int status = status(freshCopy(route.getKey()), who);
                if (status != 403) problems.add(route.getKey() + " answered " + status + " to a non-admin");
            }
            int adminStatus = status(freshCopy(route.getKey()), admin);
            if (adminStatus == 401 || adminStatus == 403) {
                problems.add(route.getKey() + " refused an admin with " + adminStatus);
            }
        }
        assertTrue(checked >= 7, "expected to find the admin routes, found " + checked);
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void roleSpecificListsRefuseTheOtherRole() throws Exception {
        String seeker = bearer(mvc, "alice_s");
        String employer = bearer(mvc, "techcorp");
        assertTrue(status(MockMvcRequestBuilders.get("/jobs/mine"), seeker) == 403, "/jobs/mine is for employers");
        assertTrue(status(MockMvcRequestBuilders.get("/jobs/mine"), employer) == 200);
        assertTrue(status(MockMvcRequestBuilders.get("/jobs/saved"), employer) == 403, "/jobs/saved is for seekers");
        assertTrue(status(MockMvcRequestBuilders.get("/jobs/saved"), seeker) == 200);
    }

    /** A request builder is single-use once headers are added, so rebuild from the key. */
    private MockHttpServletRequestBuilder freshCopy(String key) {
        return routes().get(key);
    }
}
