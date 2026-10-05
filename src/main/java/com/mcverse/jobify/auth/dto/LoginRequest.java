package com.mcverse.jobify.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for authentication")
public record LoginRequest(
        @Schema(description = "Registered username", example = "jane_doe")
        @NotBlank(message = "is required") @Size(max = 50, message = "must be at most 50 characters")
        String username,

        @Schema(description = "Account password", example = "s3cur3P@ss")
        @NotBlank(message = "is required") @Size(max = 72, message = "must be at most 72 characters")
        String password
) {}
