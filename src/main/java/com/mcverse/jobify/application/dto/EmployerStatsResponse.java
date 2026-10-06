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
            List<WeekCount> weekly,
            @Schema(description = "How far applications actually got, from their recorded history (unlike byStatus, " +
                    "which only shows where they are now)")
            Funnel funnel
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
            Map<ApplicationStatus, Integer> byStatus,
            @Schema(description = "The same funnel, for this job only") Funnel funnel
    ) {}

    @Schema(description = "A true hiring funnel built from each application's recorded history")
    public record Funnel(
            @Schema(description = "How many applications EVER reached each stage, cumulative, for the keys APPLIED, " +
                    "IN_REVIEW, INTERVIEW and OFFER (always all four, zero-filled, in pipeline order). An application " +
                    "now at INTERVIEW counts for APPLIED, IN_REVIEW and INTERVIEW; one rejected after an interview " +
                    "counts up to INTERVIEW; one withdrawn while in review counts up to IN_REVIEW. The numbers never " +
                    "increase from one stage to the next. An application that was withdrawn and re-applied is counted " +
                    "by its latest attempt only.")
            Map<ApplicationStatus, Integer> reached,
            @Schema(description = "Median number of days applications stayed in each stage (same four keys), counting " +
                    "only stays that have ended: the application moved on, was rejected or was withdrawn. An application " +
                    "still sitting in a stage is not counted yet. The value is null while no stay has ended. " +
                    "Applications that predate history recording contribute no durations.",
                    nullable = true)
            Map<ApplicationStatus, Double> medianDaysInStage
    ) {}
}
