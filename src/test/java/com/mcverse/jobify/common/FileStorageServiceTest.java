package com.mcverse.jobify.common;

import com.mcverse.jobify.common.exception.BusinessRuleException;
import com.mcverse.jobify.common.storage.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The upload folder is a trust boundary: nothing may be read or removed outside it. */
class FileStorageServiceTest {

    @TempDir
    Path root;

    private FileStorageService service() {
        return new FileStorageService(root.toString());
    }

    @Test
    void readingOutsideTheUploadFolderIsRefused() {
        FileStorageService service = service();
        assertThrows(BusinessRuleException.class, () -> service.resolveForRead("../secret.txt"));
        assertThrows(BusinessRuleException.class, () -> service.resolveForRead("a/../../secret.txt"));
        assertTrue(service.resolveForRead("alice_resume/cv.pdf").startsWith(root.toAbsolutePath().normalize()));
    }

    @Test
    void deletingOutsideTheUploadFolderIsRefusedAndAMissingFileIsFine() throws Exception {
        Path outside = Files.createTempFile("outside", ".txt");
        try {
            String relative = root.toAbsolutePath().relativize(outside.toAbsolutePath()).toString();
            assertThrows(BusinessRuleException.class, () -> service().delete(relative));
            assertTrue(Files.exists(outside), "a file outside the folder must survive");
        } finally {
            Files.deleteIfExists(outside);
        }
        assertDoesNotThrow(() -> service().delete("nobody_resume/missing.pdf"));
    }

    @Test
    void storedFilesLiveInAFolderNamedAfterTheUserWithOnlySafeCharacters() {
        var stored = service().storeResume("we!rd/../name", new MockMultipartFile(
                "file", "cv.pdf", "application/pdf", "x".getBytes()));
        assertTrue(stored.relativePath().startsWith("we_rd____name_resume"), stored.relativePath());
        assertTrue(Files.exists(service().resolveForRead(stored.relativePath())));
        assertEquals("cv.pdf", stored.originalFileName());
    }

    @Test
    void aMissingFileNameBecomesResumeAndStillNeedsAnAllowedExtension() {
        FileStorageService service = service();
        var noName = new MockMultipartFile("file", null, "application/pdf", "x".getBytes());
        assertThrows(BusinessRuleException.class, () -> service.storeResume("bob", noName));
        var oldNameWithNewline = new MockMultipartFile("file", "cv\r\n.pdf", "application/pdf", "x".getBytes());
        var stored = service.storeResume("bob", oldNameWithNewline);
        assertFalse(stored.originalFileName().contains("\n") || stored.originalFileName().contains("\r"));
    }

    @Test
    void aVeryLongFileNameIsShortenedForDisplay() {
        String longName = "a".repeat(300) + ".pdf";
        var stored = service().storeResume("carol", new MockMultipartFile(
                "file", longName, "application/pdf", "x".getBytes()));
        assertTrue(stored.originalFileName().length() <= 150);
        assertTrue(stored.originalFileName().endsWith(".pdf"));
    }
}
