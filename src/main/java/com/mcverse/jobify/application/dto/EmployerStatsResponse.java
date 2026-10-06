package com.mcverse.jobify.application.dto;

import com.mcverse.jobify.application.dto.ApplicationStatsResponse.WeekCount;
import com.mcverse.jobify.application.model.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "How an employer's job postings are performing, for the company dashboard")
public record EmployerStatsResponse(
        Jobs jobs,
        Applications applications,
        @Schema(description = "One entry per job the employer posted, open or closed, newest job first")
        List<JobStats> perJob
) {
    public record Jobs(
            @Schema(example = "5") int total,
            @Schema(description = "Open positions") int open,
            @Schema(description = "Closed or filled positions") int closed
    ) {}

    @Schema(description = "Applications across all of the employer's jobs")
    public record Applications(
            @Schema(description = "Every application ever received, including withdrawn and rejected ones") int total,
            @Schema(description = "Still in play: APPLIED, IN_REVIEW, INTERVIEW and OFFER") int active,
            @Schema(description = "Where applications are NOW, for every status, zero-filled. This is a snapshot of " +
                    "current stages, not a history: an application that was interviewed and then rejected counts " +
                    "only as REJECTED.")
            Map<ApplicationStatus, Integer> byStatus,
            @Schema(description = "Applications received in each of the last 12 weeks, oldest first, zero-filled, " +
                    "the last entry being the current week (weeks start on Monday)")
            List<WeekCount> weekly
    ) {}

    @Schema(description = "Applications to one job")
    public record JobStats(
            Integer postId,
            String jobTitle,
            @Schema(description = "false once the position was closed") boolean available,
            @Schema(description = "When the job was posted; null for jobs that predate the field", nullable = true)
            LocalDateTime createdAt,
            int total,
            int active,
            Map<ApplicationStatus, Integer> byStatus
    ) {}
}
