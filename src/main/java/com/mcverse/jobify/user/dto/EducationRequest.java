package com.mcverse.jobify.user.dto;

import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "Request body to create or update an education entry")
public record EducationRequest(
        @Schema(description = "Institution name", example = "MIT")
        @NotBlank(message = "is required") @Size(max = 255, message = "must be at most 255 characters") String institution,

        @Schema(description = "Degree obtained", example = "Bachelor of Science")
        @NotBlank(message = "is required") @Size(max = 255, message = "must be at most 255 characters") String degree,

        @Schema(description = "Field of study", example = "Computer Science")
        @NotBlank(message = "is required") @Size(max = 255, message = "must be at most 255 characters") String fieldOfStudy,

        @Schema(description = "Start date", example = "2018-09-01")
        @NotNull(message = "is required") LocalDate startDate,

        @Schema(description = "End date, omit or null if ongoing", example = "2022-06-01", nullable = true)
        LocalDate endDate,

        @Schema(description = "Grade or GPA, optional", example = "3.8 GPA", nullable = true)
        @Size(max = 255, message = "must be at most 255 characters")
        String grade
) {}
