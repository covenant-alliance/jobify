package com.mcverse.jobify.company.dto;

import com.mcverse.jobify.user.model.CompanyRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "The new role of a team member")
public record ChangeRoleRequest(
        @Schema(description = "OWNER or MANAGER", example = "OWNER")
        @NotNull(message = "is required")
        CompanyRole role
) {}
