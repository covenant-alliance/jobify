package com.mcverse.jobify.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The security audit trail: who did what, as one line per event on the dedicated "AUDIT" logger so it can be routed
 * or kept separately from the application log. Covers administrator actions and security events (logins, lockouts,
 * rate limiting, password changes, account deletion, access denials).
 *
 * <p>Each line reads {@code actor='name' action=NAME details}. The request id, client address and signed-in user
 * come from the logging context ({@code requestId}, {@code clientIp}, {@code user}), so they appear on every line in
 * the JSON log format without being passed here. Free text is flattened to one line, so a search term or a
 * username cannot forge a second entry.
 *
 * <p>Never put a password, token or other secret in {@code details}.
 */
@Component
public class AuditLog {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    /** An administrator action. Same line format as {@link #event}. */
    public void record(String admin, String action, String details) {
        event(admin, action, details);
    }

    /** A security event by {@code actor} (the account concerned; for a failed login, the name that was tried). */
    public void event(String actor, String action, String details) {
        audit.info("actor='{}' action={} {}", oneLine(actor), action, oneLine(details));
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replaceAll("[\\r\\n\\t]+", " ");
    }
}
