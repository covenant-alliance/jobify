package com.mcverse.jobify.job;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Benefits (#35), job images (#35) and the company logo (#34). */
@SpringBootTest
class JobExtrasIntegrationTest {

    private static final String PASSWORD = "Extras-Pass-4321";
    private static final byte[] PNG = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4, 5, 6, 7, 8);
    private static final byte[] PNG2 = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 9, 9, 9, 9, 9, 9, 9, 9);
    private static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1);
    private static final byte[] WEBP = bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ');

    @Autowired
    private WebApplicationContext context;
    @Value("${app.upload.dir}")
    private String uploadDir;

    private MockMvc mvc;
    private String owner;
    private String other;
    private String companyId;

    private static byte[] bytes(int... v) {
        byte[] b = new byte[v.length];
        for (int i = 0; i < v.length; i++) b[i] = (byte) v[i];
        return b;
    }

    private String register(String role) throws Exception {
        String username = "ex_" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(
                        "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"firstName\":\"E\","
                                + "\"lastName\":\"X\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        owner = register("EMPLOYER");
        other = register("EMPLOYER");
        String company = mvc.perform(post("/users/companies").header(AUTHORIZATION, owner)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Logo Co " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        companyId = JsonPath.read(company, "$.id");
        mvc.perform(post("/users/companies").header(AUTHORIZATION, other).contentType(APPLICATION_JSON)
                .content("{\"name\":\"Other Co " + UUID.randomUUID() + "\"}")).andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------------------------------------- helpers

    private String jobJson(String benefitsJson) {
        return "{\"jobTitle\":\"Extras\",\"jobDescription\":\"<p>x</p>\",\"rate\":50,\"rateType\":\"HOURLY\","
                + "\"location\":\"Berlin\",\"workMode\":\"REMOTE\",\"employmentType\":\"FULL_TIME\""
                + (benefitsJson == null ? "" : ",\"benefits\":" + benefitsJson) + "}";
    }

    private int createJob(String benefitsJson) throws Exception {
        String body = mvc.perform(post("/jobs").header(AUTHORIZATION, owner).contentType(APPLICATION_JSON)
                        .content(jobJson(benefitsJson))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.postId");
    }

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private MvcResult addImage(int jobId, String token, byte[] content) throws Exception {
        return mvc.perform(multipart("/jobs/" + jobId + "/images").file(file("x.png", content))
                .header(AUTHORIZATION, token)).andReturn();
    }

    private MvcResult uploadLogo(String id, String token, String name, byte[] content) throws Exception {
        return mvc.perform(multipart("/users/companies/" + id + "/logo").file(file(name, content))
                .header(AUTHORIZATION, token)).andReturn();
    }

    private long filesIn(String folder) throws Exception {
        Path dir = Paths.get(uploadDir).toAbsolutePath().resolve(folder);
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }

    // --------------------------------------------------------------------------------------------- benefits

    @Test
    void benefitsAreStoredInOrderTrimmedAndReturned() throws Exception {
        int id = createJob("[\"  Remote budget \",\"30 days holiday\",\"Gym\"]");
        mvc.perform(get("/jobs/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.benefits", contains("Remote budget", "30 days holiday", "Gym")))
                .andExpect(jsonPath("$.images", empty()))
                .andExpect(jsonPath("$.logoUrl").doesNotExist());
    }

    @Test
    void jobsWithoutBenefitsReturnAnEmptyListNotNull() throws Exception {
        int id = createJob(null);
        mvc.perform(get("/jobs/" + id)).andExpect(jsonPath("$.benefits", empty()));
    }

    @Test
    void editingWithoutBenefitsKeepsThemAndAnEmptyListClearsThem() throws Exception {
        int id = createJob("[\"A\",\"B\"]");
        mvc.perform(put("/jobs/" + id).header(AUTHORIZATION, owner).contentType(APPLICATION_JSON)
                        .content(jobJson(null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.benefits", contains("A", "B")));
        mvc.perform(put("/jobs/" + id).header(AUTHORIZATION, owner).contentType(APPLICATION_JSON)
                        .content(jobJson("[\"C\"]")))
                .andExpect(jsonPath("$.benefits", contains("C")));
        mvc.perform(put("/jobs/" + id).header(AUTHORIZATION, owner).contentType(APPLICATION_JSON)
                        .content(jobJson("[]")))
                .andExpect(jsonPath("$.benefits", empty()));
    }

    @Test
    void benefitLimitsGiveReadableMessages() throws Exception {
        String sixteen = "[" + "\"x\",".repeat(15) + "\"x\"]";
        mvc.perform(post("/jobs").header(AUTHORIZATION, owner).contentType(APPLICATION_JSON).content(jobJson(sixteen)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("benefits must have at most 15 entries")));
        mvc.perform(post("/jobs").header(AUTHORIZATION, owner).contentType(APPLICATION_JSON)
                        .content(jobJson("[\"" + "y".repeat(81) + "\"]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("must be at most 80 characters")));
        mvc.perform(post("/jobs").header(AUTHORIZATION, owner).contentType(APPLICATION_JSON).content(jobJson("[\"  \"]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("must not be blank")));
        // exactly 15 of exactly 80 characters is fine
        String fifteen = "[" + ("\"" + "z".repeat(80) + "\",").repeat(14) + "\"" + "z".repeat(80) + "\"]";
        mvc.perform(post("/jobs").header(AUTHORIZATION, owner).contentType(APPLICATION_JSON).content(jobJson(fifteen)))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------------------------------ job images

    @Test
    void ownerAddsImagesAndTheyAreListedOldestFirstAndServedPublicly() throws Exception {
        int id = createJob(null);
        MvcResult first = addImage(id, owner, PNG);
        assertEquals(201, first.getResponse().getStatus());
        MvcResult second = addImage(id, owner, JPEG);
        assertEquals(201, second.getResponse().getStatus());
        String body = second.getResponse().getContentAsString();
        java.util.List<String> urls = JsonPath.read(body, "$.images");
        assertEquals(2, urls.size());
        assertTrue(urls.get(0).startsWith("/jobs/" + id + "/images/"));

        // public: no token, and a stale token is ignored
        MvcResult img = mvc.perform(get(urls.get(0))).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff")).andReturn();
        assertArrayEquals(PNG, img.getResponse().getContentAsByteArray());
        mvc.perform(get(urls.get(1)).header(AUTHORIZATION, "Bearer stale.token.value")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"));
        // the job itself lists them in the same order
        mvc.perform(get("/jobs/" + id)).andExpect(jsonPath("$.images", contains(urls.get(0), urls.get(1))));
    }

    @Test
    void pngJpegAndWebpAreAcceptedByTheirBytesWhateverTheyAreCalled() throws Exception {
        int id = createJob(null);
        for (byte[] b : new byte[][] {PNG, JPEG, WEBP}) {
            MvcResult r = mvc.perform(multipart("/jobs/" + id + "/images").file(file("photo.txt", b))
                    .header(AUTHORIZATION, owner)).andReturn();
            assertEquals(201, r.getResponse().getStatus());
        }
        String images = mvc.perform(get("/jobs/" + id)).andReturn().getResponse().getContentAsString();
        java.util.List<String> urls = JsonPath.read(images, "$.images");
        mvc.perform(get(urls.get(2))).andExpect(header().string("Content-Type", "image/webp"));
    }

    @Test
    void notRealImagesAreRefusedWith422() throws Exception {
        int id = createJob(null);
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes();
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        for (byte[] bad : new byte[][] {svg, html, new byte[] {1, 2}}) {
            mvc.perform(multipart("/jobs/" + id + "/images").file(file("evil.png", bad)).header(AUTHORIZATION, owner))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value("Only PNG, JPEG and WebP images are supported."));
        }
        mvc.perform(multipart("/jobs/" + id + "/images").file(file("empty.png", new byte[0]))
                        .header(AUTHORIZATION, owner))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Please choose an image to upload."));
        mvc.perform(get("/jobs/" + id)).andExpect(jsonPath("$.images", empty()));
    }

    @Test
    void anImageOverTwoMegabytesIsRefused() throws Exception {
        int id = createJob(null);
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        mvc.perform(multipart("/jobs/" + id + "/images").file(file("big.png", big)).header(AUTHORIZATION, owner))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Image must be 2 MB or smaller."));
    }

    @Test
    void aSeventhImageIsRefusedAndRemovingOneMakesRoom() throws Exception {
        int id = createJob(null);
        String last = null;
        for (int i = 0; i < 6; i++) last = addImage(id, owner, PNG).getResponse().getContentAsString();
        mvc.perform(multipart("/jobs/" + id + "/images").file(file("x.png", PNG)).header(AUTHORIZATION, owner))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("A job can have at most 6 images. Remove one first."));
        java.util.List<String> urls = JsonPath.read(last, "$.images");
        String imageId = urls.get(0).substring(urls.get(0).lastIndexOf('/') + 1);
        mvc.perform(delete("/jobs/" + id + "/images/" + imageId).header(AUTHORIZATION, owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.images.length()").value(5));
        assertEquals(201, addImage(id, owner, PNG).getResponse().getStatus());
    }

    @Test
    void removingAnImageDeletesItsFileAndItsAddressStopsWorking() throws Exception {
        int id = createJob(null);
        String body = addImage(id, owner, PNG).getResponse().getContentAsString();
        String url = ((java.util.List<String>) JsonPath.read(body, "$.images")).get(0);
        assertEquals(1, filesIn("job-images/" + id));
        mvc.perform(delete(url).header(AUTHORIZATION, owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.images", empty()));
        mvc.perform(get(url)).andExpect(status().isNotFound());
        assertEquals(0, filesIn("job-images/" + id));
        mvc.perform(delete(url).header(AUTHORIZATION, owner)).andExpect(status().isNotFound());
    }

    @Test
    void onlyTheOwnerCanAddOrRemoveAndAnonymousCallersGet401() throws Exception {
        int id = createJob(null);
        String body = addImage(id, owner, PNG).getResponse().getContentAsString();
        String url = ((java.util.List<String>) JsonPath.read(body, "$.images")).get(0);
        assertEquals(403, addImage(id, other, PNG).getResponse().getStatus());
        mvc.perform(delete(url).header(AUTHORIZATION, other)).andExpect(status().isForbidden());
        mvc.perform(multipart("/jobs/" + id + "/images").file(file("x.png", PNG))).andExpect(status().isUnauthorized());
        mvc.perform(delete(url)).andExpect(status().isUnauthorized());
        mvc.perform(get("/jobs/" + id)).andExpect(jsonPath("$.images.length()").value(1));
        String seeker = register("SEEKER");
        assertEquals(403, addImage(id, seeker, PNG).getResponse().getStatus());
    }

    @Test
    void unknownJobOrImageIs404AndAnImageIsOnlyServedUnderItsOwnJob() throws Exception {
        int id = createJob(null);
        int otherJob = createJob(null);
        String body = addImage(id, owner, PNG).getResponse().getContentAsString();
        String imageId = ((String) ((java.util.List<String>) JsonPath.read(body, "$.images")).get(0)).split("/")[3];
        assertEquals(404, addImage(987654, owner, PNG).getResponse().getStatus());
        mvc.perform(get("/jobs/987654/images/" + imageId)).andExpect(status().isNotFound());
        mvc.perform(get("/jobs/" + id + "/images/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/jobs/" + otherJob + "/images/" + imageId)).andExpect(status().isNotFound());
        mvc.perform(delete("/jobs/" + otherJob + "/images/" + imageId).header(AUTHORIZATION, owner))
                .andExpect(status().isNotFound());
    }

    @Test
    void approvingAnEmployersDeletionRemovesTheirImageAndLogoFiles() throws Exception {
        int id = createJob(null);
        addImage(id, owner, PNG);
        assertEquals(200, uploadLogo(companyId, owner, "logo.png", PNG).getResponse().getStatus());
        assertEquals(1, filesIn("job-images/" + id));
        assertEquals(1, filesIn("company-logos/" + companyId));
        String req = mvc.perform(post("/account/deletion-request").header(AUTHORIZATION, owner)
                        .contentType(APPLICATION_JSON).content("{\"reason\":\"closing\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String admin = com.mcverse.jobify.support.ApiTestSupport.bearer(mvc, "admin");
        mvc.perform(post("/admin/deletion-requests/" + (String) JsonPath.read(req, "$.id") + "/approve")
                .header(AUTHORIZATION, admin)).andExpect(status().isOk());
        assertEquals(0, filesIn("job-images/" + id));
        assertEquals(0, filesIn("company-logos/" + companyId));
    }

    // ------------------------------------------------------------------------------------------- the logo

    @Test
    void ownerUploadsALogoAndItAppearsOnTheCompanyAndOnTheirJobs() throws Exception {
        int id = createJob(null);
        MvcResult up = uploadLogo(companyId, owner, "logo.png", PNG);
        assertEquals(200, up.getResponse().getStatus());
        String url = JsonPath.read(up.getResponse().getContentAsString(), "$.logoUrl");
        assertTrue(url.startsWith("/companies/" + companyId + "/logo?v="));

        mvc.perform(get("/companies/" + companyId)).andExpect(jsonPath("$.logoUrl").value(url));
        mvc.perform(get("/jobs/" + id)).andExpect(jsonPath("$.logoUrl").value(url));
        mvc.perform(get("/jobs/search").param("q", "Extras").param("size", "50"))
                .andExpect(status().isOk());
        mvc.perform(get("/users/employers/me").header(AUTHORIZATION, owner))
                .andExpect(jsonPath("$.company.logoUrl").value(url));

        MvcResult img = mvc.perform(get("/companies/" + companyId + "/logo")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(header().string("Cache-Control", containsString("max-age=86400"))).andReturn();
        assertArrayEquals(PNG, img.getResponse().getContentAsByteArray());
        mvc.perform(get("/companies/" + companyId + "/logo").header(AUTHORIZATION, "Bearer stale"))
                .andExpect(status().isOk());
    }

    @Test
    void replacingTheLogoChangesTheUrlAndKeepsOnlyTheNewFile() throws Exception {
        String url1 = JsonPath.read(uploadLogo(companyId, owner, "a.png", PNG).getResponse().getContentAsString(),
                "$.logoUrl");
        Thread.sleep(5); // the version is a millisecond clock
        String url2 = JsonPath.read(uploadLogo(companyId, owner, "b.jpg", JPEG).getResponse().getContentAsString(),
                "$.logoUrl");
        assertNotEquals(url1, url2);
        assertEquals(1, filesIn("company-logos/" + companyId));
        mvc.perform(get("/companies/" + companyId + "/logo")).andExpect(header().string("Content-Type", "image/jpeg"));
    }

    @Test
    void removingTheLogo() throws Exception {
        uploadLogo(companyId, owner, "a.png", PNG);
        mvc.perform(delete("/users/companies/" + companyId + "/logo").header(AUTHORIZATION, owner))
                .andExpect(status().isNoContent());
        mvc.perform(get("/companies/" + companyId + "/logo")).andExpect(status().isNotFound());
        mvc.perform(get("/companies/" + companyId)).andExpect(jsonPath("$.logoUrl").doesNotExist());
        assertEquals(0, filesIn("company-logos/" + companyId));
        // nothing left to remove
        mvc.perform(delete("/users/companies/" + companyId + "/logo").header(AUTHORIZATION, owner))
                .andExpect(status().isNotFound());
    }

    @Test
    void aCompanyWithoutALogoAnswers404() throws Exception {
        mvc.perform(get("/companies/" + companyId + "/logo")).andExpect(status().isNotFound());
        mvc.perform(get("/companies/" + UUID.randomUUID() + "/logo")).andExpect(status().isNotFound());
    }

    @Test
    void onlyTheCompanysOwnerMayChangeItsLogo() throws Exception {
        assertEquals(403, uploadLogo(companyId, other, "a.png", PNG).getResponse().getStatus());
        assertEquals(403, uploadLogo(companyId, register("SEEKER"), "a.png", PNG).getResponse().getStatus());
        assertEquals(404, uploadLogo(UUID.randomUUID().toString(), owner, "a.png", PNG).getResponse().getStatus());
        mvc.perform(multipart("/users/companies/" + companyId + "/logo").file(file("a.png", PNG)))
                .andExpect(status().isUnauthorized());
        uploadLogo(companyId, owner, "a.png", PNG);
        mvc.perform(delete("/users/companies/" + companyId + "/logo").header(AUTHORIZATION, other))
                .andExpect(status().isForbidden());
        mvc.perform(get("/companies/" + companyId + "/logo")).andExpect(status().isOk());
    }

    @Test
    void logoMustBeARealImageUnderOneMegabyte() throws Exception {
        MvcResult notImage = uploadLogo(companyId, owner, "logo.png", "GIF89a".getBytes());
        assertEquals(422, notImage.getResponse().getStatus());
        assertTrue(notImage.getResponse().getContentAsString()
                .contains("Only PNG, JPEG and WebP images are supported."));
        byte[] big = new byte[1024 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        MvcResult tooBig = uploadLogo(companyId, owner, "big.png", big);
        assertEquals(422, tooBig.getResponse().getStatus());
        assertTrue(tooBig.getResponse().getContentAsString().contains("Logo must be 1 MB or smaller."));
        assertFalse(mvc.perform(get("/companies/" + companyId)).andReturn().getResponse().getContentAsString()
                .contains("logoUrl\":\"/"));
    }

    @Test
    void jobsOfACompanyWithoutALogoHaveNullLogoUrl() throws Exception {
        int id = createJob(null);
        mvc.perform(get("/jobs/" + id)).andExpect(jsonPath("$.logoUrl").doesNotExist())
                .andExpect(jsonPath("$.companyId").value(companyId));
        mvc.perform(get("/jobs/" + id)).andExpect(jsonPath("$.images", empty()))
                .andExpect(jsonPath("$.benefits", empty()));
        // the logo route does not answer to POST/PUT from the public side
        mvc.perform(post("/companies/" + companyId + "/logo")).andExpect(status().is4xxClientError());
    }
}
