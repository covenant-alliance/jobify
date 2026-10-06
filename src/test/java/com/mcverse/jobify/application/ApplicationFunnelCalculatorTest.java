package com.mcverse.jobify.application;

import com.mcverse.jobify.application.dto.EmployerStatsResponse.Funnel;
import com.mcverse.jobify.application.model.ApplicationStatus;
import com.mcverse.jobify.application.service.ApplicationFunnelCalculator;
import com.mcverse.jobify.application.service.ApplicationFunnelCalculator.Change;
import com.mcverse.jobify.application.service.ApplicationFunnelCalculator.History;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mcverse.jobify.application.model.ApplicationStatus.APPLIED;
import static com.mcverse.jobify.application.model.ApplicationStatus.INTERVIEW;
import static com.mcverse.jobify.application.model.ApplicationStatus.IN_REVIEW;
import static com.mcverse.jobify.application.model.ApplicationStatus.OFFER;
import static com.mcverse.jobify.application.model.ApplicationStatus.REJECTED;
import static com.mcverse.jobify.application.model.ApplicationStatus.WITHDRAWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The funnel rules, on plain data, with exact days. Day 0 is 2026-01-01 00:00. */
class ApplicationFunnelCalculatorTest {

    private static final LocalDateTime DAY0 = LocalDateTime.of(2026, 1, 1, 0, 0);

    private static LocalDateTime day(double days) {
        return DAY0.plusSeconds((long) (days * 86_400));
    }

    /** current status, then the steps as pairs (to status, day), starting with the creation at day 0. */
    private static History history(ApplicationStatus current, Object... steps) {
        List<Change> changes = new ArrayList<>();
        ApplicationStatus previous = null;
        for (int i = 0; i < steps.length; i += 2) {
            ApplicationStatus to = (ApplicationStatus) steps[i];
            double at = ((Number) steps[i + 1]).doubleValue();
            changes.add(new Change(previous, to, day(at)));
            previous = to;
        }
        return new History(current, DAY0, changes);
    }

    private static Map<ApplicationStatus, Integer> reached(Funnel funnel) {
        return funnel.reached();
    }

    @Test
    void noApplicationsGivesZerosAndNoMedians() {
        Funnel funnel = ApplicationFunnelCalculator.calculate(List.of());
        assertEquals(Map.of(APPLIED, 0, IN_REVIEW, 0, INTERVIEW, 0, OFFER, 0), reached(funnel));
        for (ApplicationStatus stage : ApplicationFunnelCalculator.STAGES) {
            assertNull(funnel.medianDaysInStage().get(stage));
        }
        assertEquals(List.of(APPLIED, IN_REVIEW, INTERVIEW, OFFER), List.copyOf(funnel.reached().keySet()),
                "keys come in pipeline order");
    }

    @Test
    void reachIsCumulativeAndNeverIncreasesDownTheFunnel() {
        Funnel funnel = ApplicationFunnelCalculator.calculate(List.of(
                history(APPLIED, APPLIED, 0),
                history(IN_REVIEW, APPLIED, 0, IN_REVIEW, 1),
                history(INTERVIEW, APPLIED, 0, IN_REVIEW, 1, INTERVIEW, 2),
                history(OFFER, APPLIED, 0, IN_REVIEW, 1, INTERVIEW, 2, OFFER, 3)));
        assertEquals(4, reached(funnel).get(APPLIED));
        assertEquals(3, reached(funnel).get(IN_REVIEW));
        assertEquals(2, reached(funnel).get(INTERVIEW));
        assertEquals(1, reached(funnel).get(OFFER));
    }

    @Test
    void aRejectionCountsUpToTheStageItLeftFrom() {
        Funnel funnel = ApplicationFunnelCalculator.calculate(List.of(
                history(REJECTED, APPLIED, 0, REJECTED, 1),                                    // left from APPLIED
                history(REJECTED, APPLIED, 0, IN_REVIEW, 1, REJECTED, 2),                      // left from IN_REVIEW
                history(REJECTED, APPLIED, 0, IN_REVIEW, 1, INTERVIEW, 2, REJECTED, 3),        // left from INTERVIEW
                history(REJECTED, APPLIED, 0, IN_REVIEW, 1, INTERVIEW, 2, OFFER, 3, REJECTED, 4))); // after an offer
        assertEquals(4, reached(funnel).get(APPLIED));
        assertEquals(3, reached(funnel).get(IN_REVIEW));
        assertEquals(2, reached(funnel).get(INTERVIEW));
        assertEquals(1, reached(funnel).get(OFFER));
    }

    @Test
    void aWithdrawalCountsUpToTheStageItLeftFrom() {
        Funnel funnel = ApplicationFunnelCalculator.calculate(List.of(
                history(WITHDRAWN, APPLIED, 0, WITHDRAWN, 2),
                history(WITHDRAWN, APPLIED, 0, IN_REVIEW, 1, WITHDRAWN, 3)));
        assertEquals(2, reached(funnel).get(APPLIED));
        assertEquals(1, reached(funnel).get(IN_REVIEW));
        assertEquals(0, reached(funnel).get(INTERVIEW));
    }

    @Test
    void reapplyingStartsAFreshAttemptAndIgnoresTheEarlierProgress() {
        // got to interview, withdrew, applied again and is back at APPLIED
        History history = history(APPLIED, APPLIED, 0, IN_REVIEW, 1, INTERVIEW, 2, WITHDRAWN, 3, APPLIED, 5);
        Funnel funnel = ApplicationFunnelCalculator.calculate(List.of(history));
        assertEquals(1, reached(funnel).get(APPLIED));
        assertEquals(0, reached(funnel).get(IN_REVIEW));
        assertEquals(0, reached(funnel).get(INTERVIEW));
    }

    @Test
    void daysInStageAreTheMedianOfFinishedStays() {
        // APPLIED stays ended after 1, 3 and 8 days (median 3). IN_REVIEW stays ended after 2 and 4 days (median 3);
        // the first application is still in review, so its stay is not counted.
        Funnel funnel = ApplicationFunnelCalculator.calculate(List.of(
                history(IN_REVIEW, APPLIED, 0, IN_REVIEW, 1),
                history(INTERVIEW, APPLIED, 0, IN_REVIEW, 3, INTERVIEW, 5),
                history(REJECTED, APPLIED, 0, IN_REVIEW, 8, REJECTED, 12)));
        assertEquals(3.0, funnel.medianDaysInStage().get(APPLIED));
        assertEquals(3.0, funnel.medianDaysInStage().get(IN_REVIEW));
    }

    @Test
    void aStayThatHasNotEndedIsNotCounted() {
        Funnel funnel = ApplicationFunnelCalculator.calculate(List.of(
                history(IN_REVIEW, APPLIED, 0, IN_REVIEW, 2)));
        assertEquals(2.0, funnel.medianDaysInStage().get(APPLIED));
        assertNull(funnel.medianDaysInStage().get(IN_REVIEW), "still in review: no finished stay yet");
        assertNull(funnel.medianDaysInStage().get(OFFER));
    }

    @Test
    void anEvenNumberOfStaysAveragesTheMiddleTwoAndRoundsToOneDecimal() {
        Funnel funnel = ApplicationFunnelCalculator.calculate(List.of(
                history(IN_REVIEW, APPLIED, 0, IN_REVIEW, 1),
                history(IN_REVIEW, APPLIED, 0, IN_REVIEW, 2.5)));
        assertEquals(1.8, funnel.medianDaysInStage().get(APPLIED)); // (1 + 2.5) / 2 = 1.75 -> 1.8
    }

    @Test
    void aWithdrawalEndsTheStayOfTheStageItLeft() {
        Funnel funnel = ApplicationFunnelCalculator.calculate(List.of(
                history(WITHDRAWN, APPLIED, 0, IN_REVIEW, 1, WITHDRAWN, 4)));
        assertEquals(1.0, funnel.medianDaysInStage().get(APPLIED));
        assertEquals(3.0, funnel.medianDaysInStage().get(IN_REVIEW));
    }

    @Test
    void aReapplicationMeasuresStaysFromTheNewAttemptOnly() {
        // first attempt: 1 day APPLIED. Second attempt starts on day 10 and moves on at day 14: 4 days.
        Funnel funnel = ApplicationFunnelCalculator.calculate(List.of(
                history(IN_REVIEW, APPLIED, 0, WITHDRAWN, 1, APPLIED, 10, IN_REVIEW, 14)));
        assertEquals(4.0, funnel.medianDaysInStage().get(APPLIED));
    }

    @Test
    void anApplicationWithoutHistoryFallsBackToItsCurrentStatusAndHasNoDurations() {
        Funnel funnel = ApplicationFunnelCalculator.calculate(List.of(
                new History(INTERVIEW, DAY0, List.of()),
                new History(REJECTED, DAY0, List.of()),
                new History(OFFER, DAY0, List.of())));
        assertEquals(3, reached(funnel).get(APPLIED));
        assertEquals(2, reached(funnel).get(IN_REVIEW), "interview and offer imply a review");
        assertEquals(2, reached(funnel).get(INTERVIEW));
        assertEquals(1, reached(funnel).get(OFFER));
        for (ApplicationStatus stage : ApplicationFunnelCalculator.STAGES) {
            assertNull(funnel.medianDaysInStage().get(stage));
        }
    }

    @Test
    void aBackfilledHistoryCountsOnlyWhatIsKnown() {
        // created, then "APPLIED to REJECTED" on day 6 (the stage it left from was never recorded)
        Funnel funnel = ApplicationFunnelCalculator.calculate(List.of(
                history(REJECTED, APPLIED, 0, REJECTED, 6)));
        assertEquals(1, reached(funnel).get(APPLIED));
        assertEquals(0, reached(funnel).get(IN_REVIEW));
        assertEquals(6.0, funnel.medianDaysInStage().get(APPLIED));
    }

    @Test
    void anOutOfOrderClockNeverProducesANegativeNumberOfDays() {
        Funnel funnel = ApplicationFunnelCalculator.calculate(List.of(
                history(IN_REVIEW, APPLIED, 5, IN_REVIEW, 1)));
        assertEquals(0.0, funnel.medianDaysInStage().get(APPLIED));
    }
}
