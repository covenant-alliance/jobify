package com.mcverse.jobify.notification.dto;

import com.mcverse.jobify.notification.model.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "A notification for the signed-in user. Title and body are plain text.")
public record NotificationResponse(
        @Schema(description = "Notification id (UUID)") String id,
        @Schema(description = "What it is about", example = "APPLICATION") NotificationType type,
        @Schema(example = "Your application is being reviewed") String title,
        @Schema(example = "TechCorp Ltd is now reviewing your application for Backend Engineer.") String body,
        @Schema(description = "Whether the user has already read it") boolean read,
        @Schema(description = "When it was created") LocalDateTime createdAt,
        @Schema(description = "The job it is about, if any", nullable = true) Integer jobId,
        @Schema(description = "The application it is about, if any", nullable = true) String applicationId
) {}
