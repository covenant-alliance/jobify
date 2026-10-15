package com.mcverse.jobify.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Whether an account can sign in")
public record AccountStateResponse(String username, boolean enabled) {}
