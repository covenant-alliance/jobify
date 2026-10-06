package com.mcverse.jobify.application.service;

import com.mcverse.jobify.application.dto.ApplicationStatsResponse;
import com.mcverse.jobify.application.dto.ApplicationStatsResponse.WeekCount;
import com.mcverse.jobify.application.model.ApplicationStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure counting logic for the seeker's application statistics, kept free of the database so it is easy to test. */
public final class ApplicationStatsCalculator {

    /** How many weeks the time series covers, counting the current one. */
    public static final int WEEKS = 12;

    private static final Set<ApplicationStatus> ACTIVE = EnumSet.of(ApplicationStatus.APPLIED,
            ApplicationStatus.IN_REVIEW, ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER);

    /** One application reduced to what the statistics need. */
    public record Entry(ApplicationStatus status, LocalDateTime submittedAt) {}

    private ApplicationStatsCalculator() {}

    public static ApplicationStatsResponse calculate(List<Entry> entries, LocalDate today) {
        Map<ApplicationStatus, Integer> byStatus = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            byStatus.put(status, 0);
        }
        int active = 0;

        LocalDate currentWeek = weekStart(today);
        LocalDate firstWeek = currentWeek.minusWeeks(WEEKS - 1L);
        int[] perWeek = new int[WEEKS];

        for (Entry entry : entries) {
            byStatus.merge(entry.status(), 1, Integer::sum);
            if (ACTIVE.contains(entry.status())) {
                active++;
            }
            LocalDate week = weekStart(entry.submittedAt().toLocalDate());
            if (!week.isBefore(firstWeek) && !week.isAfter(currentWeek)) {
                perWeek[(int) java.time.temporal.ChronoUnit.WEEKS.between(firstWeek, week)]++;
            }
        }

        List<WeekCount> weekly = new ArrayList<>(WEEKS);
        for (int i = 0; i < WEEKS; i++) {
            weekly.add(new WeekCount(firstWeek.plusWeeks(i), perWeek[i]));
        }
        return new ApplicationStatsResponse(entries.size(), active, byStatus, weekly);
    }

    /** The Monday of the week the date falls in. */
    static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
