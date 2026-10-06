package com.mcverse.jobify.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How many notifications the user has not read yet, for the bell badge")
public record UnreadCountResponse(@Schema(example = "3") long count) {}
