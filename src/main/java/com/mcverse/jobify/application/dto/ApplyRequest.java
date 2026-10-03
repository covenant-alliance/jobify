package com.mcverse.jobify.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Optional body when applying to a job")
public record ApplyRequest(
        @Schema(description = "A short note to the employer", example = "I have 6 years of Spring Boot experience.",
                nullable = true)
        @Size(max = 2000, message = "must be at most 2000 characters")
        String coverNote
) {}
