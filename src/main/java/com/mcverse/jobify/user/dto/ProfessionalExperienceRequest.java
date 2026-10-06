package com.mcverse.jobify.user.dto;

import com.mcverse.jobify.common.model.EmploymentType;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "Request body to create or update a professional experience entry")
public record ProfessionalExperienceRequest(
        @Schema(description = "Job title", example = "Software Engineer")
        @NotBlank(message = "is required") @Size(max = 255, message = "must be at most 255 characters") String jobTitle,

        @Schema(description = "Company name", example = "Acme Corp")
        @NotBlank(message = "is required") @Size(max = 255, message = "must be at most 255 characters") String companyName,

        @Schema(description = "Location, optional", example = "Remote", nullable = true)
        @Size(max = 255, message = "must be at most 255 characters")
        String location,

        @Schema(description = "Employment type, optional", example = "FULL_TIME", nullable = true)
        EmploymentType employmentType,

        @Schema(description = "Start date", example = "2020-03-01")
        @NotNull(message = "is required") LocalDate startDate,

        @Schema(description = "End date, omit or null if this is the current position", example = "2023-01-15", nullable = true)
        LocalDate endDate,

        @Schema(description = "Description of responsibilities/achievements, optional", nullable = true)
        @Size(max = 5000, message = "must be at most 5000 characters")
        String description
) {}
