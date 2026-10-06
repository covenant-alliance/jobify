package com.mcverse.jobify.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "An account as an admin sees it")
public record AdminUserSummary(
        @Schema(description = "Numeric account id (not the profile UUID)", example = "4") Long id,
        @Schema(example = "alice_s") String username,
        @Schema(description = "SEEKER, EMPLOYER or ADMIN", example = "SEEKER") String role,
        @Schema(description = "First name from the profile; null for accounts without one (admins)", nullable = true)
        String name,
        @Schema(nullable = true) String lastName,
        @Schema(description = "Profile UUID, for GET /users/seekers/{id} or /users/employers/{id}", nullable = true)
        String profileId,
        @Schema(description = "When the profile was created; null for accounts without a profile (admins)", nullable = true)
        LocalDateTime creationDate
) {}
