package com.mcverse.jobify.user;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Resume upload, download, replace and delete, including what must be refused and kept inside the upload folder. */
@SpringBootTest
class ResumeIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    @Value("${app.upload.dir}")
    private String uploadDir;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String[] newUser(String role) throws Exception {
        String username = role.toLowerCase().charAt(0) + "_" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(
                        "{\"username\":\"" + username + "\",\"password\":\"Resume-Pass-4321\",\"firstName\":\"R\","
                                + "\"lastName\":\"S\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new String[] {username, "Bearer " + JsonPath.read(body, "$.token")};
    }

    private static MockMultipartFile file(String name, String type, byte[] bytes) {
        return new MockMultipartFile("file", name, type, bytes);
    }

    private static final byte[] PDF = "%PDF-1.4 resume one".getBytes(StandardCharsets.UTF_8);

    @Test
    void uploadThenDownloadReturnsTheSameBytesWithTheOriginalName() throws Exception {
        String[] user = newUser("SEEKER");
        mvc.perform(multipart("/users/seekers/me/resume").file(file("My CV.pdf", "application/pdf", PDF))
                        .header("Authorization", user[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cv.originalFileName").value("My CV.pdf"))
                .andExpect(jsonPath("$.cv.fileType").value("application/pdf"));

        byte[] downloaded = mvc.perform(get("/users/seekers/me/resume").header("Authorization", user[1]))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("My CV.pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(PDF, downloaded);
    }

    @Test
    void aNewUploadReplacesTheOldOneAndLeavesOnlyOneFile() throws Exception {
        String[] user = newUser("SEEKER");
        mvc.perform(multipart("/users/seekers/me/resume").file(file("first.pdf", "application/pdf", PDF))
                .header("Authorization", user[1])).andExpect(status().isOk());
        byte[] second = "second resume content".getBytes(StandardCharsets.UTF_8);
        mvc.perform(multipart("/users/seekers/me/resume").file(file("second.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", second))
                .header("Authorization", user[1])).andExpect(status().isOk())
                .andExpect(jsonPath("$.cv.originalFileName").value("second.docx"));

        assertArrayEquals(second, mvc.perform(get("/users/seekers/me/resume").header("Authorization", user[1]))
                .andReturn().getResponse().getContentAsByteArray());
        try (Stream<Path> files = Files.list(Path.of(uploadDir).resolve(user[0] + "_resume"))) {
            assertTrue(files.count() == 1, "the old file must be removed");
        }
    }

    @Test
    void deletingRemovesTheRecordAndTheFile() throws Exception {
        String[] user = newUser("SEEKER");
        mvc.perform(multipart("/users/seekers/me/resume").file(file("cv.pdf", "application/pdf", PDF))
                .header("Authorization", user[1])).andExpect(status().isOk());
        mvc.perform(delete("/users/seekers/me/resume").header("Authorization", user[1]))
                .andExpect(status().isNoContent());
        mvc.perform(get("/users/seekers/me/resume").header("Authorization", user[1]))
                .andExpect(status().isNotFound());
        mvc.perform(get("/users/seekers/me").header("Authorization", user[1]))
                .andExpect(jsonPath("$.cv").doesNotExist());
        mvc.perform(delete("/users/seekers/me/resume").header("Authorization", user[1]))
                .andExpect(status().isNotFound());
        Path folder = Path.of(uploadDir).resolve(user[0] + "_resume");
        if (Files.exists(folder)) {
            try (Stream<Path> files = Files.list(folder)) {
                assertTrue(files.findAny().isEmpty(), "the file must be gone");
            }
        }
    }

    @Test
    void downloadingWithoutAResumeIs404() throws Exception {
        mvc.perform(get("/users/seekers/me/resume").header("Authorization", newUser("SEEKER")[1]))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void onlyPdfDocAndDocxAreAccepted() throws Exception {
        String token = newUser("SEEKER")[1];
        for (String bad : new String[] {"virus.exe", "notes.txt", "photo.png", "script.pdf.sh", "noextension"}) {
            mvc.perform(multipart("/users/seekers/me/resume").file(file(bad, "application/octet-stream", PDF))
                            .header("Authorization", token))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value("Only PDF, DOC, and DOCX resumes are supported."));
        }
        mvc.perform(multipart("/users/seekers/me/resume").file(file("OK.DOC", "application/msword", PDF))
                .header("Authorization", token)).andExpect(status().isOk());
    }

    @Test
    void anEmptyFileIsRefused() throws Exception {
        mvc.perform(multipart("/users/seekers/me/resume").file(file("empty.pdf", "application/pdf", new byte[0]))
                        .header("Authorization", newUser("SEEKER")[1]))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Please choose a file to upload."));
    }

    @Test
    void aFileOverFiveMegabytesIsRefused() throws Exception {
        byte[] tooBig = new byte[5 * 1024 * 1024 + 1];
        mvc.perform(multipart("/users/seekers/me/resume").file(file("big.pdf", "application/pdf", tooBig))
                        .header("Authorization", newUser("SEEKER")[1]))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Resume must be 5MB or smaller."));
    }

    @Test
    void aPathInTheFileNameCannotEscapeTheUploadFolder() throws Exception {
        String[] user = newUser("SEEKER");
        String body = mvc.perform(multipart("/users/seekers/me/resume")
                        .file(file("../../escape-attempt.pdf", "application/pdf", PDF)).header("Authorization", user[1]))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String shown = JsonPath.read(body, "$.cv.originalFileName");
        assertFalse(shown.contains("..") || shown.contains("/"), "display name is reduced to a plain name: " + shown);
        Path root = Path.of(uploadDir).toAbsolutePath().normalize();
        assertFalse(Files.exists(root.getParent().resolve("escape-attempt.pdf")), "nothing written above the upload dir");
        assertFalse(Files.exists(root.getParent().getParent().resolve("escape-attempt.pdf")));
        assertTrue(Files.exists(root.resolve(user[0] + "_resume")), "stored in the user's own folder");
    }

    @Test
    void resumesAreSeekerOnlyAndNeedAToken() throws Exception {
        String employer = newUser("EMPLOYER")[1];
        mvc.perform(multipart("/users/seekers/me/resume").file(file("cv.pdf", "application/pdf", PDF))
                .header("Authorization", employer)).andExpect(status().isNotFound());
        mvc.perform(get("/users/seekers/me/resume").header("Authorization", employer)).andExpect(status().isNotFound());
        mvc.perform(multipart("/users/seekers/me/resume").file(file("cv.pdf", "application/pdf", PDF)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/users/seekers/me/resume")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/users/seekers/me/resume")).andExpect(status().isUnauthorized());
    }
}
