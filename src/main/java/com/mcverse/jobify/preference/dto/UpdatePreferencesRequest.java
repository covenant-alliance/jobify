package com.mcverse.jobify.preference.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "A change to the settings. Anything left out stays as it is, so a client can send only what "
        + "the user touched.")
public record UpdatePreferencesRequest(
        @Schema(description = "Notification types to switch on (true) or off (false), by name: INTERVIEW, "
                + "APPLICATION, SYSTEM, HIRING. Types left out are unchanged. ACCOUNT cannot be switched off.",
                example = "{\"APPLICATION\":false}", nullable = true)
        Map<String, Boolean> notifications,

        @Schema(description = "#rrggbb to set the accent colour; an empty string to clear it; omit to keep it",
                example = "#1a73e8", nullable = true)
        String accentColor
) {}
