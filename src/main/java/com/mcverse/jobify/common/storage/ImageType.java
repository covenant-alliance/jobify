package com.mcverse.jobify.common.storage;

import java.util.Optional;

/**
 * The image formats Jobify accepts. The type comes from the file's first bytes, never from its name or the
 * client's content type, so a renamed script or an SVG (which can carry scripts) is refused.
 */
public enum ImageType {
    PNG("png", "image/png"),
    JPEG("jpg", "image/jpeg"),
    WEBP("webp", "image/webp");

    private final String extension;
    private final String contentType;

    ImageType(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension()   { return extension; }
    public String contentType() { return contentType; }

    public static Optional<ImageType> detect(byte[] head) {
        if (head.length >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                && head[4] == 0x0D && head[5] == 0x0A && head[6] == 0x1A && head[7] == 0x0A) {
            return Optional.of(PNG);
        }
        if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    public static Optional<ImageType> fromContentType(String contentType) {
        for (ImageType t : values()) {
            if (t.contentType.equals(contentType)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
