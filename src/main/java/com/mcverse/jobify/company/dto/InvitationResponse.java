package com.mcverse.jobify.company.dto;

import com.mcverse.jobify.company.model.InvitationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "An invitation to join a company")
public record InvitationResponse(
        @Schema(description = "UUID invitation id") String id,
        @Schema(description = "Company id (UUID)") String companyId,
        @Schema(description = "Company name", example = "TechCorp Ltd") String companyName,
        @Schema(description = "Username of the invited employer") String inviteeUsername,
        @Schema(description = "Username of the owner who invited") String invitedBy,
        @Schema(description = "PENDING while it can be answered, then ACCEPTED, DECLINED, CANCELLED or EXPIRED")
        InvitationStatus status,
        LocalDateTime createdAt,
        @Schema(description = "Valid until (14 days after it was sent)") LocalDateTime expiresAt,
        @Schema(nullable = true) LocalDateTime respondedAt
) {}
