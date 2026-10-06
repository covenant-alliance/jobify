package com.mcverse.jobify.common.storage;

import com.mcverse.jobify.common.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Set;

/**
 * Stores per-user uploads under {@code <upload.dir>/<username>_resume/}. Every
 * user's files live in their own folder, one resume at a time — a new upload
 * replaces whatever was there before.
 */
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_RESUME_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final long MAX_RESUME_SIZE_BYTES = 5L * 1024 * 1024;

    private final Path uploadRoot;

    public FileStorageService(@Value("${app.upload.dir}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public StoredFile storeResume(String username, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Please choose a file to upload.");
        }
        if (file.getSize() > MAX_RESUME_SIZE_BYTES) {
            throw new BusinessRuleException("Resume must be 5MB or smaller.");
        }

        String originalName = sanitizeDisplayName(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        if (!ALLOWED_RESUME_EXTENSIONS.contains(extension)) {
            throw new BusinessRuleException("Only PDF, DOC, and DOCX resumes are supported.");
        }

        Path userDir = resolveUserFolder(username + "_resume");

        try {
            Files.createDirectories(userDir);
            clearFolder(userDir);

            String storedFileName = "resume." + extension;
            Path target = userDir.resolve(storedFileName);
            file.transferTo(target);

            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
            String relativePath = userDir.getFileName() + "/" + storedFileName;
            return new StoredFile(relativePath, contentType, originalName);
        } catch (IOException e) {
            throw new BusinessRuleException("Failed to store the uploaded file.");
        }
    }

    /**
     * Stores an image under {@code <upload.dir>/<folder>/<baseName>.<ext>} and returns where. The format is read from
     * the file's bytes (PNG, JPEG or WebP); {@code label} names the thing in error messages ("Logo", "Image").
     */
    public StoredImage storeImage(String folder, String baseName, MultipartFile file, long maxBytes, String label) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Please choose an image to upload.");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessRuleException(label + " must be " + (maxBytes / (1024 * 1024)) + " MB or smaller.");
        }
        ImageType type;
        try (var in = file.getInputStream()) {
            type = ImageType.detect(in.readNBytes(12))
                    .orElseThrow(() -> new BusinessRuleException("Only PNG, JPEG and WebP images are supported."));
        } catch (IOException e) {
            throw new BusinessRuleException("Failed to read the uploaded file.");
        }
        Path dir = uploadRoot.resolve(folder).normalize();
        if (!dir.startsWith(uploadRoot)) {
            throw new BusinessRuleException("Invalid file path.");
        }
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(baseName + "." + type.extension()));
        } catch (IOException e) {
            throw new BusinessRuleException("Failed to store the uploaded file.");
        }
        return new StoredImage(folder + "/" + baseName + "." + type.extension(), type.contentType());
    }

    public Path resolveForRead(String relativePath) {
        Path target = uploadRoot.resolve(relativePath).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new BusinessRuleException("Invalid file path.");
        }
        return target;
    }

    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolveForRead(relativePath));
        } catch (IOException ignored) {
            // Best-effort cleanup — the DB record is the source of truth.
        }
    }

    private Path resolveUserFolder(String folderName) {
        String safeName = folderName.replaceAll("[^a-zA-Z0-9_-]", "_");
        Path dir = uploadRoot.resolve(safeName).normalize();
        if (!dir.startsWith(uploadRoot)) {
            throw new BusinessRuleException("Invalid username.");
        }
        return dir;
    }

    private void clearFolder(Path dir) throws IOException {
        try (var entries = Files.list(dir)) {
            for (Path p : entries.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isRegularFile(p)) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && dot < filename.length() - 1 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    private String sanitizeDisplayName(String name) {
        if (name == null || name.isBlank()) return "resume";
        String base = Paths.get(name.replaceAll("[\\r\\n]", "")).getFileName().toString().trim();
        return base.length() > 150 ? base.substring(base.length() - 150) : base;
    }

    public record StoredImage(String relativePath, String contentType) {}

    public record StoredFile(String relativePath, String contentType, String originalFileName) {}
}
