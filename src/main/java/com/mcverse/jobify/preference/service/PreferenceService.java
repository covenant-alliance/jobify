package com.mcverse.jobify.preference.service;

import com.mcverse.jobify.notification.model.NotificationType;
import com.mcverse.jobify.notification.service.NotificationGate;
import com.mcverse.jobify.preference.dto.PreferencesResponse;
import com.mcverse.jobify.preference.dto.UpdatePreferencesRequest;
import com.mcverse.jobify.preference.model.UserPreferences;
import com.mcverse.jobify.preference.repository.UserPreferencesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Reads and changes a user's settings, and tells the notification system what the user has switched off. */
@Service
public class PreferenceService implements NotificationGate {

    private static final Pattern COLOUR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final UserPreferencesRepository repository;

    public PreferenceService(UserPreferencesRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PreferencesResponse get(String username) {
        return repository.findById(username).map(PreferenceService::toResponse)
                .orElseGet(() -> toResponse(new UserPreferences(username)));
    }

    @Transactional
    public PreferencesResponse update(String username, UpdatePreferencesRequest request) {
        // check everything first so a bad request changes nothing
        Map<NotificationType, Boolean> changes = parseNotifications(request.notifications());
        String colour = parseColour(request.accentColor());

        UserPreferences preferences = repository.findById(username).orElseGet(() -> new UserPreferences(username));
        changes.forEach(preferences::setEnabled);
        if (request.accentColor() != null) {
            preferences.setAccentColor(colour);
        }
        return toResponse(repository.save(preferences));
    }

    /** Called when an account is removed. */
    @Transactional
    public void deleteFor(String username) {
        repository.deleteById(username);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean allows(String recipientUsername, NotificationType type) {
        if (type == NotificationType.ACCOUNT) {
            return true; // password changes and deletion decisions are never optional
        }
        return repository.findById(recipientUsername)
                .map(p -> !p.getDisabledNotifications().contains(type)).orElse(true);
    }

    private static Map<NotificationType, Boolean> parseNotifications(Map<String, Boolean> raw) {
        Map<NotificationType, Boolean> parsed = new EnumMap<>(NotificationType.class);
        if (raw == null) {
            return parsed;
        }
        for (Map.Entry<String, Boolean> entry : raw.entrySet()) {
            NotificationType type = typeOf(entry.getKey());
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("notifications." + type + " must be true or false.");
            }
            if (type == NotificationType.ACCOUNT && !entry.getValue()) {
                throw new IllegalArgumentException(
                        "ACCOUNT notifications cannot be turned off: they tell you about your password and account.");
            }
            parsed.put(type, entry.getValue());
        }
        return parsed;
    }

    private static NotificationType typeOf(String name) {
        try {
            return NotificationType.valueOf(name == null ? "" : name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown notification type '" + name + "'. Use one of: "
                    + String.join(", ", Arrays.stream(NotificationType.values()).map(Enum::name).toList()) + ".");
        }
    }

    /** Null for "keep" and for "clear" alike: the caller tells them apart by whether the field was sent. */
    private static String parseColour(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String trimmed = raw.trim();
        if (!COLOUR.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("accentColor must look like #1a73e8, or be empty to clear it.");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static PreferencesResponse toResponse(UserPreferences p) {
        Map<NotificationType, Boolean> notifications = new EnumMap<>(NotificationType.class);
        for (NotificationType type : NotificationType.values()) {
            notifications.put(type, !p.getDisabledNotifications().contains(type));
        }
        return new PreferencesResponse(notifications, p.getAccentColor());
    }
}
