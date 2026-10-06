package com.mcverse.jobify.auth.dto;

import com.mcverse.jobify.auth.model.Role;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body for account registration")
public record RegisterRequest(
        @Schema(description = "Unique username (used for login)", example = "jane_doe")
        @NotBlank(message = "is required") @Size(max = 50, message = "must be at most 50 characters") String username,

        @Schema(description = "Password — minimum 8 characters recommended", example = "s3cur3P@ss")
        @NotBlank(message = "is required") @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String password,

        @Schema(description = "SEEKER or EMPLOYER only — ADMIN is seeded and cannot be self-registered",
                example = "SEEKER", allowableValues = {"SEEKER", "EMPLOYER"})
        @NotNull(message = "is required") Role role,

        @Schema(description = "First name", example = "Jane")
        @NotBlank(message = "is required") @Size(max = 100, message = "must be at most 100 characters") String firstName,

        @Schema(description = "Last name", example = "Doe")
        @NotBlank(message = "is required") @Size(max = 100, message = "must be at most 100 characters") String lastName
) {}
