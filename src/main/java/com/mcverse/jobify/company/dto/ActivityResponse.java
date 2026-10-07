package com.mcverse.jobify.company.dto;

import com.mcverse.jobify.user.model.ActivityType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "One line of the company's activity feed. Plain text: show it as text, never as HTML.")
public record ActivityResponse(
        String id,
        ActivityType type,
        @Schema(description = "Username of the person who did it") String actor,
        @Schema(description = "A sentence describing it", example = "alice posted \"Java Developer\"") String summary,
        @Schema(nullable = true) Integer jobId,
        @Schema(nullable = true) String applicationId,
        LocalDateTime createdAt
) {}
