package com.mcverse.jobify.common.storage;

import com.mcverse.jobify.common.exception.ResourceNotFoundException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** The public answer for an image download: its bytes with the type we detected, cacheable by browsers and CDNs. */
public final class ImageResponses {

    private ImageResponses() {}

    public static ResponseEntity<Resource> of(Path file, String contentType, Duration maxAge) {
        if (!Files.isRegularFile(file)) {
            throw new ResourceNotFoundException("Image", file.getFileName().toString());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(maxAge).cachePublic())
                .body(new FileSystemResource(file));
    }
}
