package com.mcverse.jobify.job.dto;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body to open or close a job posting")
public record UpdateJobAvailabilityRequest(
        @Schema(description = "true = position is open; false = position is closed/filled", example = "false")
        @NotNull(message = "is required")
        Boolean available
) {}
