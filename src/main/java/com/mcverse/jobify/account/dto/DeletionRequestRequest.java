package com.mcverse.jobify.account.dto;

import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body to ask an admin to delete the authenticated account")
public record DeletionRequestRequest(
        @Schema(description = "Optional reason for the request", example = "No longer job hunting", nullable = true)
        @Size(max = 255, message = "must be at most 255 characters")
        String reason
) {}
