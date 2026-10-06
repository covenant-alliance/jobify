package com.mcverse.jobify.user.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "Request body to create or update a certification entry")
public record CertificationRequest(
        @Schema(description = "Certification name", example = "AWS Certified Solutions Architect")
        @NotBlank(message = "is required") @Size(max = 255, message = "must be at most 255 characters") String name,

        @Schema(description = "Issuing organization", example = "Amazon Web Services")
        @NotBlank(message = "is required") @Size(max = 255, message = "must be at most 255 characters") String issuingOrganization,

        @Schema(description = "Date issued", example = "2023-05-10")
        @NotNull(message = "is required") LocalDate issueDate,

        @Schema(description = "Expiration date, omit or null if it does not expire", example = "2026-05-10", nullable = true)
        LocalDate expirationDate,

        @Schema(description = "Credential ID, optional", example = "AWS-SAA-123456", nullable = true)
        @Size(max = 255, message = "must be at most 255 characters")
        String credentialId,

        @Schema(description = "Credential verification URL, optional", example = "https://aws.amazon.com/verify/123456", nullable = true)
        @Size(max = 255, message = "must be at most 255 characters")
        @Pattern(regexp = "^https?://.*", message = "must start with http:// or https://")
        String credentialUrl
) {}
