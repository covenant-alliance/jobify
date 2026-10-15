package com.mcverse.jobify.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "The refresh token received at login, register or the last refresh")
public record RefreshRequest(
        @Schema(description = "Opaque refresh token") @NotBlank(message = "is required")
        @Size(max = 200, message = "must be at most 200 characters")
        String refreshToken
) {}
