package com.mcverse.jobify.user.dto;

import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for creating or updating a company")
public record CompanyRequest(
        @Schema(description = "Company name — must be unique per employer", example = "Acme Corporation")
        @NotBlank(message = "is required") @Size(max = 150, message = "must be at most 150 characters") String name
) {}
