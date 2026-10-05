package com.mcverse.jobify.application.dto;

import com.mcverse.jobify.application.model.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body for an employer to move an application to another stage")
public record ChangeApplicationStatusRequest(
        @Schema(description = "The new stage: IN_REVIEW, INTERVIEW, OFFER or REJECTED (subject to the allowed moves)",
                example = "IN_REVIEW")
        @NotNull(message = "is required")
        ApplicationStatus status
) {}
