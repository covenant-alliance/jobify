package com.mcverse.jobify.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A job after moderation")
public record JobModerationResponse(Integer postId, boolean available) {}
