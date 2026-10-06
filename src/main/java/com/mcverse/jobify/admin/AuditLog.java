package com.mcverse.jobify.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Records what administrators do. Written to the dedicated "AUDIT" logger so it can be routed or kept separately
 * from the application log. Free text is flattened to one line, so a search term cannot forge a log entry.
 */
@Component
public class AuditLog {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    public void record(String admin, String action, String details) {
        audit.info("admin='{}' action={} {}", oneLine(admin), action, oneLine(details));
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replaceAll("[\\r\\n\\t]+", " ");
    }
}
