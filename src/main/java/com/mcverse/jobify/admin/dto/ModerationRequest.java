package com.mcverse.jobify.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Why an administrator is doing this. Kept in the audit log; for jobs it is also shown to the owners.")
public record ModerationRequest(
        @Schema(description = "Optional for disabling or enabling an account", example = "Repeated reports of fake postings",
                nullable = true)
        @Size(max = 500, message = "must be at most 500 characters")
        String reason
) {}
