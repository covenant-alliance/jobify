package com.mcverse.jobify.company.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Whom to invite to the company")
public record InviteRequest(
        @Schema(description = "Username of an existing employer account", example = "jane_recruiter")
        @NotBlank(message = "is required")
        @Size(max = 100, message = "must be at most 100 characters")
        String username
) {}
