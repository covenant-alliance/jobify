package com.mcverse.jobify.admin.dto;

import com.mcverse.jobify.application.model.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Headline numbers for the admin dashboard")
public record AdminStatsResponse(
        Users users,
        Jobs jobs,
        Applications applications,
        @Schema(description = "Account deletion requests waiting for an admin", example = "2") long pendingDeletionRequests
) {
    public record Users(
            @Schema(example = "7") long total,
            @Schema(description = "SEEKER, EMPLOYER and ADMIN, all present, zero-filled") Map<String, Long> byRole,
            @Schema(description = "Profiles created in the last 7 days") long newLast7Days,
            @Schema(description = "Profiles created in the last 30 days") long newLast30Days
    ) {}

    public record Jobs(
            long total,
            @Schema(description = "Open positions (available = true)") long open,
            @Schema(description = "Closed or filled positions") long closed,
            @Schema(description = "Jobs posted in the last 7 days") long postedLast7Days
    ) {}

    public record Applications(
            long total,
            @Schema(description = "All six statuses, zero-filled") Map<ApplicationStatus, Long> byStatus,
            @Schema(description = "Applications submitted in the last 7 days") long submittedLast7Days
    ) {}
}
