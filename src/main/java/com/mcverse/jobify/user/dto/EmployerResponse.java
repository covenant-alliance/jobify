package com.mcverse.jobify.user.dto;

import com.mcverse.jobify.user.model.CompanyRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Employer profile — includes an optional linked company")
public record EmployerResponse(
        @Schema(description = "UUID profile identifier", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        String id,

        @Schema(description = "Username", example = "acme_corp")
        String username,

        @Schema(description = "First name", example = "Alice")
        String name,

        @Schema(description = "Last name", example = "Smith")
        String lastName,

        @Schema(description = "Profile creation timestamp (ISO-8601)", example = "2024-01-15T10:30:00")
        LocalDateTime creationDate,

        @Schema(description = "Linked company — null if no company has been created yet", nullable = true)
        CompanyResponse company,

        @Schema(description = "This person's role in the company: OWNER (also edits the company, its logo and team) "
                + "or MANAGER (manages its jobs, applications and statistics); null when there is no company",
                nullable = true)
        CompanyRole companyRole
) {}
