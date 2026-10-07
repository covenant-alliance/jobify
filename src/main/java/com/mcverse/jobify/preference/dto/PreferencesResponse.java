package com.mcverse.jobify.preference.dto;

import com.mcverse.jobify.notification.model.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "The signed-in user's settings")
public record PreferencesResponse(
        @Schema(description = "Whether each kind of notification is delivered. Every type is always listed; a user "
                + "who never changed anything has all of them true. ACCOUNT (security messages) is always true.",
                example = "{\"INTERVIEW\":true,\"APPLICATION\":false,\"SYSTEM\":true,\"HIRING\":true,\"ACCOUNT\":true}")
        Map<NotificationType, Boolean> notifications,

        @Schema(description = "Accent colour as #rrggbb (lower case); null when the user never chose one",
                example = "#1a73e8", nullable = true)
        String accentColor
) {}
