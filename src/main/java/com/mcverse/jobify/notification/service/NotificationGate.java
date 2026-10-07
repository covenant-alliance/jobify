package com.mcverse.jobify.notification.service;

import com.mcverse.jobify.notification.model.NotificationType;

/**
 * Lets another part of the system veto a notification before it is stored (the user's own settings do, see the
 * preference package). Defined here so the notification package never depends on who decides.
 */
@FunctionalInterface
public interface NotificationGate {

    /** False to drop the notification silently. */
    boolean allows(String recipientUsername, NotificationType type);
}
