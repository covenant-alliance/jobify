package com.mcverse.jobify.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Close or reopen any job as an administrator")
public record JobModerationRequest(
        @Schema(description = "false closes the job, true reopens it") @NotNull(message = "is required")
        Boolean available,

        @Schema(description = "Required when closing; shown to the job's owners", example = "Does not meet the posting rules",
                nullable = true)
        @Size(max = 500, message = "must be at most 500 characters")
        String reason
) {}
