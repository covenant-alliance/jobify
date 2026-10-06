package com.mcverse.jobify.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of marking every notification as read")
public record MarkAllReadResponse(@Schema(description = "How many were unread before", example = "3") int updated) {}
