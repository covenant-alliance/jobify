package com.mcverse.jobify.application;

import com.mcverse.jobify.application.dto.ApplicationStatsResponse;
import com.mcverse.jobify.application.model.ApplicationStatus;
import com.mcverse.jobify.application.service.ApplicationStatsCalculator;
import com.mcverse.jobify.application.service.ApplicationStatsCalculator.Entry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationStatsCalculatorTest {

    /** Wednesday 7 October 2026; its week starts on Monday 5 October. */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    private static Entry entry(ApplicationStatus status, String dateTime) {
        return new Entry(status, LocalDateTime.parse(dateTime));
    }

    private static int countForWeek(ApplicationStatsResponse stats, String monday) {
        return stats.weekly().stream().filter(w -> w.weekStart().equals(LocalDate.parse(monday)))
                .findFirst().orElseThrow().count();
    }

    @Test
    void noApplicationsGivesZerosEverywhere() {
        ApplicationStatsResponse stats = ApplicationStatsCalculator.calculate(List.of(), TODAY);
        assertEquals(0, stats.total());
        assertEquals(0, stats.active());
        assertEquals(6, stats.byStatus().size());
        stats.byStatus().values().forEach(count -> assertEquals(0, count));
        assertEquals(12, stats.weekly().size());
        stats.weekly().forEach(w -> assertEquals(0, w.count()));
    }

    @Test
    void weeklySeriesIsTwelveMondaysEndingWithTheCurrentWeek() {
        ApplicationStatsResponse stats = ApplicationStatsCalculator.calculate(List.of(), TODAY);
        assertEquals(LocalDate.of(2026, 10, 5), stats.weekly().get(11).weekStart());
        assertEquals(LocalDate.of(2026, 7, 20), stats.weekly().get(0).weekStart());
        stats.weekly().forEach(w -> assertEquals(java.time.DayOfWeek.MONDAY, w.weekStart().getDayOfWeek()));
    }

    @Test
    void countsPerStatusAndActiveExcludesRejectedAndWithdrawn() {
        List<Entry> entries = List.of(
                entry(ApplicationStatus.APPLIED, "2026-10-06T10:00:00"),
                entry(ApplicationStatus.IN_REVIEW, "2026-10-06T11:00:00"),
                entry(ApplicationStatus.INTERVIEW, "2026-10-01T09:00:00"),
                entry(ApplicationStatus.OFFER, "2026-09-20T09:00:00"),
                entry(ApplicationStatus.REJECTED, "2026-09-10T09:00:00"),
                entry(ApplicationStatus.WITHDRAWN, "2026-09-11T09:00:00"),
                entry(ApplicationStatus.WITHDRAWN, "2026-09-12T09:00:00"));
        ApplicationStatsResponse stats = ApplicationStatsCalculator.calculate(entries, TODAY);

        assertEquals(7, stats.total());
        assertEquals(4, stats.active());
        assertEquals(1, stats.byStatus().get(ApplicationStatus.APPLIED));
        assertEquals(1, stats.byStatus().get(ApplicationStatus.IN_REVIEW));
        assertEquals(1, stats.byStatus().get(ApplicationStatus.INTERVIEW));
        assertEquals(1, stats.byStatus().get(ApplicationStatus.OFFER));
        assertEquals(1, stats.byStatus().get(ApplicationStatus.REJECTED));
        assertEquals(2, stats.byStatus().get(ApplicationStatus.WITHDRAWN));
    }

    @Test
    void weeksStartOnMondayAtTheBoundaries() {
        List<Entry> entries = List.of(
                entry(ApplicationStatus.APPLIED, "2026-10-04T23:59:59"),   // Sunday: previous week
                entry(ApplicationStatus.APPLIED, "2026-10-05T00:00:00"),   // Monday: current week
                entry(ApplicationStatus.APPLIED, "2026-10-07T12:00:00"),   // Wednesday: current week
                entry(ApplicationStatus.APPLIED, "2026-10-11T23:59:59"));  // Sunday, same week as today
        ApplicationStatsResponse stats = ApplicationStatsCalculator.calculate(entries, TODAY);

        assertEquals(1, countForWeek(stats, "2026-09-28"));
        assertEquals(3, countForWeek(stats, "2026-10-05"));
    }

    @Test
    void applicationsOlderThanTwelveWeeksCountInTotalsButNotInTheSeries() {
        List<Entry> entries = List.of(
                entry(ApplicationStatus.APPLIED, "2026-07-19T12:00:00"),   // Sunday before the first week
                entry(ApplicationStatus.APPLIED, "2026-07-20T00:00:00"),   // first week
                entry(ApplicationStatus.REJECTED, "2025-01-01T12:00:00"));
        ApplicationStatsResponse stats = ApplicationStatsCalculator.calculate(entries, TODAY);

        assertEquals(3, stats.total());
        assertEquals(1, stats.weekly().stream().mapToInt(w -> w.count()).sum());
        assertEquals(1, countForWeek(stats, "2026-07-20"));
    }

    @Test
    void weeksWithoutApplicationsAreZeroFilledBetweenBusyOnes() {
        List<Entry> entries = List.of(
                entry(ApplicationStatus.APPLIED, "2026-08-04T12:00:00"),
                entry(ApplicationStatus.APPLIED, "2026-08-04T13:00:00"),
                entry(ApplicationStatus.APPLIED, "2026-10-06T12:00:00"));
        ApplicationStatsResponse stats = ApplicationStatsCalculator.calculate(entries, TODAY);

        assertEquals(2, countForWeek(stats, "2026-08-03"));
        assertEquals(0, countForWeek(stats, "2026-08-10"));
        assertEquals(0, countForWeek(stats, "2026-09-28"));
        assertEquals(1, countForWeek(stats, "2026-10-05"));
        assertEquals(3, stats.weekly().stream().mapToInt(w -> w.count()).sum());
    }

    @Test
    void applicationsFromTheFutureAreIgnoredInTheSeries() {
        ApplicationStatsResponse stats = ApplicationStatsCalculator.calculate(
                List.of(entry(ApplicationStatus.APPLIED, "2026-12-01T12:00:00")), TODAY);
        assertEquals(1, stats.total());
        assertEquals(0, stats.weekly().stream().mapToInt(w -> w.count()).sum());
    }
}
