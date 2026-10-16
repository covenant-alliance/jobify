package com.mcverse.jobify.notification.model;

/** What a notification is about. Mirrors the front end's union: interview, application, system, hiring, account, match. */
public enum NotificationType {
    INTERVIEW,
    APPLICATION,
    SYSTEM,
    HIRING,
    ACCOUNT,
    /** A newly posted job that fits the seeker well (matching story M10). */
    MATCH
}
