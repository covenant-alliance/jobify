package com.mcverse.jobify.application.dto;

import com.mcverse.jobify.application.model.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Schema(description = "A seeker's application activity, for dashboard charts")
public record ApplicationStatsResponse(
        @Schema(description = "All applications ever made, including withdrawn and rejected ones", example = "7")
        int total,

        @Schema(description = "Applications still in play: APPLIED, IN_REVIEW, INTERVIEW and OFFER. " +
                "REJECTED and WITHDRAWN are not active.", example = "4")
        int active,

        @Schema(description = "Count for every status, zero-filled so all six keys are always present")
        Map<ApplicationStatus, Integer> byStatus,

        @Schema(description = "Applications submitted per week for the last 12 weeks, oldest first, " +
                "zero-filled. The last entry is the current week.")
        List<WeekCount> weekly
) {
    @Schema(description = "Applications submitted in one calendar week (Monday to Sunday)")
    public record WeekCount(
            @Schema(description = "The Monday that starts the week", example = "2026-10-05") LocalDate weekStart,
            @Schema(example = "2") int count
    ) {}
}
