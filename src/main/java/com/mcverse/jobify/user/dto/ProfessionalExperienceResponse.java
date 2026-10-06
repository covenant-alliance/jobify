package com.mcverse.jobify.user.dto;

import com.mcverse.jobify.common.model.EmploymentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "A professional experience entry on a seeker's profile")
public record ProfessionalExperienceResponse(
        @Schema(description = "Experience entry UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        String id,

        @Schema(description = "Job title", example = "Software Engineer")
        String jobTitle,

        @Schema(description = "Company name", example = "Acme Corp")
        String companyName,

        @Schema(description = "Location, optional", example = "Remote", nullable = true)
        String location,

        @Schema(description = "Employment type, optional", example = "FULL_TIME", nullable = true)
        EmploymentType employmentType,

        @Schema(description = "Start date", example = "2020-03-01")
        LocalDate startDate,

        @Schema(description = "End date, null if this is the current position", example = "2023-01-15", nullable = true)
        LocalDate endDate,

        @Schema(description = "Description of responsibilities/achievements, optional", nullable = true)
        String description
) {}
