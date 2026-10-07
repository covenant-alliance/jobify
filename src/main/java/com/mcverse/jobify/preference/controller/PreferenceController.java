package com.mcverse.jobify.preference.controller;

import com.mcverse.jobify.preference.dto.PreferencesResponse;
import com.mcverse.jobify.preference.dto.UpdatePreferencesRequest;
import com.mcverse.jobify.preference.service.PreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Preferences", description = "The signed-in user's own settings — requires Bearer token")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/users/me/preferences")
public class PreferenceController {

    private final PreferenceService preferences;

    public PreferenceController(PreferenceService preferences) {
        this.preferences = preferences;
    }

    @Operation(summary = "Get my settings",
            description = "Any signed-in user. A user who never saved anything gets the defaults: every notification on, "
                    + "no accent colour.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The settings"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
    })
    @GetMapping
    public PreferencesResponse get(@AuthenticationPrincipal UserDetails principal) {
        return preferences.get(principal.getUsername());
    }

    @Operation(summary = "Change my settings",
            description = "Only what is sent changes. A notification type switched off is no longer delivered to me "
                    + "(nothing is stored and the unread badge does not count it); ACCOUNT notifications cannot be "
                    + "switched off. Returns the full settings.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The settings after the change"),
            @ApiResponse(responseCode = "400", description = "Unknown notification type, ACCOUNT switched off, or a "
                    + "bad colour; nothing is changed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
    })
    @PutMapping
    public PreferencesResponse update(@AuthenticationPrincipal UserDetails principal,
                                      @Valid @RequestBody UpdatePreferencesRequest request) {
        return preferences.update(principal.getUsername(), request);
    }
}
