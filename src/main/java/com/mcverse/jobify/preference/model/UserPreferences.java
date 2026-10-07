package com.mcverse.jobify.preference.model;

import com.mcverse.jobify.notification.model.NotificationType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * One user's settings, kept by username (the same key notifications use). Only what differs from the defaults is
 * stored: every notification type is on unless it appears in {@link #disabledNotifications}.
 */
@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    @Id
    @Column(length = 255)
    private String username;

    /** "#rrggbb" in lower case, or null for the app's own colour. */
    @Column(length = 7)
    private String accentColor;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_disabled_notifications", joinColumns = @JoinColumn(name = "username"))
    @Column(name = "notification_type", nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR) // VARCHAR, not a database enum, so a new type never needs a schema change
    private Set<NotificationType> disabledNotifications = EnumSet.noneOf(NotificationType.class);

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected UserPreferences() {}

    public UserPreferences(String username) {
        this.username = username;
    }

    public String getUsername()                              { return username; }
    public String getAccentColor()                           { return accentColor; }
    public Set<NotificationType> getDisabledNotifications()  { return disabledNotifications; }

    public void setAccentColor(String accentColor)           { this.accentColor = accentColor; touch(); }

    public void setEnabled(NotificationType type, boolean enabled) {
        if (enabled) {
            disabledNotifications.remove(type);
        } else {
            disabledNotifications.add(type);
        }
        touch();
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
