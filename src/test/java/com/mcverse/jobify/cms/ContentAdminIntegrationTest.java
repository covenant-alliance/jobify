package com.mcverse.jobify.cms;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static com.mcverse.jobify.support.ApiTestSupport.bearer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The CMS texts: public read, admin read and edit, unknown keys ignored, and edits show up on the public route. */
@SpringBootTest
class ContentAdminIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void adminEditsATextAndThePublicRouteShowsIt() throws Exception {
        String admin = bearer(mvc, "admin");
        String list = mvc.perform(get("/admin/content").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String key = JsonPath.read(list, "$[0].key");
        String original = JsonPath.read(list, "$[0].value");
        try {
            mvc.perform(put("/admin/content").header("Authorization", admin).contentType(APPLICATION_JSON)
                            .content("{\"values\":{\"" + key + "\":\"Edited by test\",\"no.such.key\":\"ignored\"}}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.key=='" + key + "')].value").value("Edited by test"));
            String publicContent = mvc.perform(get("/content")).andReturn().getResponse().getContentAsString();
            assertEquals("Edited by test", JsonPath.read(publicContent, "$['" + key + "']"));
            assertTrue(!publicContent.contains("no.such.key"), "unknown keys are ignored, not created");
        } finally {
            mvc.perform(put("/admin/content").header("Authorization", admin).contentType(APPLICATION_JSON)
                    .content("{\"values\":{\"" + key + "\":" + jsonString(original) + "}}"));
        }
    }

    @Test
    void anEmptyUpdateChangesNothingAndNonAdminsAreRefused() throws Exception {
        mvc.perform(put("/admin/content").header("Authorization", bearer(mvc, "admin")).contentType(APPLICATION_JSON)
                .content("{\"values\":{}}")).andExpect(status().isOk());
        mvc.perform(put("/admin/content").header("Authorization", bearer(mvc, "alice_s")).contentType(APPLICATION_JSON)
                .content("{\"values\":{}}")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/content").header("Authorization", bearer(mvc, "techcorp")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/content")).andExpect(status().isOk());
    }

    private static String jsonString(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
