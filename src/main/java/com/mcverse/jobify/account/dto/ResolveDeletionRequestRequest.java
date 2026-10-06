package com.mcverse.jobify.account.dto;

import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body when an admin rejects a deletion request")
public record ResolveDeletionRequestRequest(
        @Schema(description = "Optional note explaining the decision", example = "Account has an active dispute", nullable = true)
        @Size(max = 255, message = "must be at most 255 characters")
        String note
) {}
