package com.mcverse.jobify.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body to change the authenticated account's password")
public record ChangePasswordRequest(
        @Schema(description = "The account's current password", example = "hunter2")
        @NotBlank(message = "is required") @Size(max = 72, message = "must be at most 72 characters") String currentPassword,

        @Schema(description = "The new password (min 8 characters)", example = "correct-horse-battery")
        @NotBlank(message = "is required") @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String newPassword
) {}
