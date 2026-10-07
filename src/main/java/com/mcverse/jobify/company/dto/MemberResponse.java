package com.mcverse.jobify.company.dto;

import com.mcverse.jobify.user.model.CompanyRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "A person connected to a company")
public record MemberResponse(
        String username,
        String name,
        String lastName,
        @Schema(description = "OWNER or MANAGER") CompanyRole role,
        @Schema(description = "When they connected to the company") LocalDateTime joinedAt
) {}
