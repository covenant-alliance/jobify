package com.mcverse.jobify.application.service;

import com.mcverse.jobify.application.dto.EmployerStatsResponse.Funnel;
import com.mcverse.jobify.application.model.ApplicationStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns application histories into a hiring funnel: how many applications ever reached each stage, and how long they
 * stayed in each one. Pure logic, free of the database, so every rule below is covered by plain unit tests.
 *
 * <p>The stages are the employer's pipeline, in order: APPLIED, IN_REVIEW, INTERVIEW, OFFER. REJECTED and WITHDRAWN
 * are ways out of the pipeline, not stages to reach.
 * <ul>
 *   <li><b>Reached</b> is cumulative. An application that was interviewed and then rejected counts for APPLIED,
 *       IN_REVIEW and INTERVIEW. An application that left from a stage counts up to that stage.</li>
 *   <li><b>Re-applying</b> after a withdrawal starts a fresh attempt: only the changes since then are used.</li>
 *   <li><b>Days in a stage</b> counts only stays that ended (the application moved on, was rejected or withdrawn).
 *       An application still sitting in a stage has no finished stay yet, so it is left out rather than guessed.</li>
 *   <li>Where there is no usable history (an application from before history existed), its current status gives a
 *       lower bound for "reached" and it contributes no durations.</li>
 * </ul>
 */
public final class ApplicationFunnelCalculator {

    /** The pipeline in order. */
    public static final List<ApplicationStatus> STAGES = List.of(ApplicationStatus.APPLIED,
            ApplicationStatus.IN_REVIEW, ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER);

    /** One change of status, reduced to what the funnel needs. */
    public record Change(ApplicationStatus from, ApplicationStatus to, LocalDateTime at) {}

    /** One application: its current status, when it was created, and its changes oldest first. */
    public record History(ApplicationStatus current, LocalDateTime createdAt, List<Change> changes) {}

    private ApplicationFunnelCalculator() {}

    public static Funnel calculate(List<History> histories) {
        int[] reached = new int[STAGES.size()];
        Map<ApplicationStatus, List<Double>> days = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus stage : STAGES) {
            days.put(stage, new ArrayList<>());
        }

        for (History history : histories) {
            List<Change> attempt = currentAttempt(history.changes());
            int furthest = furthestStage(history.current(), attempt);
            for (int i = 0; i <= furthest; i++) {
                reached[i]++;
            }
            collectStays(history, attempt, days);
        }

        Map<ApplicationStatus, Integer> reachedByStage = new LinkedHashMap<>();
        Map<ApplicationStatus, Double> median = new LinkedHashMap<>();
        for (int i = 0; i < STAGES.size(); i++) {
            ApplicationStatus stage = STAGES.get(i);
            reachedByStage.put(stage, reached[i]);
            median.put(stage, median(days.get(stage)));
        }
        return new Funnel(reachedByStage, median);
    }

    /** The changes since the last re-application (WITHDRAWN to APPLIED), or all of them if there was none. */
    private static List<Change> currentAttempt(List<Change> changes) {
        int start = 0;
        for (int i = 0; i < changes.size(); i++) {
            Change change = changes.get(i);
            if (change.from() == ApplicationStatus.WITHDRAWN && change.to() == ApplicationStatus.APPLIED) {
                start = i;
            }
        }
        return changes.subList(start, changes.size());
    }

    /** Index in {@link #STAGES} of the furthest stage this attempt reached. Everyone reached at least APPLIED. */
    private static int furthestStage(ApplicationStatus current, List<Change> attempt) {
        int furthest = Math.max(0, stageIndex(current));
        for (Change change : attempt) {
            furthest = Math.max(furthest, stageIndex(change.to()));
            // leaving from a stage (rejected, withdrawn) means it got that far
            furthest = Math.max(furthest, stageIndex(change.from()));
        }
        return furthest;
    }

    /** Position in the pipeline, or -1 for null (an application's creation has no earlier status), REJECTED, WITHDRAWN. */
    private static int stageIndex(ApplicationStatus status) {
        return status == null ? -1 : STAGES.indexOf(status);
    }

    /** Adds the length in days of every stay that has ended. */
    private static void collectStays(History history, List<Change> attempt,
                                     Map<ApplicationStatus, List<Double>> days) {
        if (attempt.isEmpty()) {
            return;
        }
        ApplicationStatus stage = ApplicationStatus.APPLIED;
        LocalDateTime enteredAt = history.createdAt();
        Change first = attempt.get(0);
        if (first.from() == null || first.from() == ApplicationStatus.WITHDRAWN) {
            // the attempt starts with its own creation or re-application row: that is when APPLIED began
            enteredAt = first.at();
        }
        for (Change change : attempt) {
            if (change.from() == null || change.from() == ApplicationStatus.WITHDRAWN) {
                stage = change.to();
                enteredAt = change.at();
                continue;
            }
            if (STAGES.contains(stage) && change.from() == stage && enteredAt != null) {
                days.get(stage).add(daysBetween(enteredAt, change.at()));
            }
            stage = change.to();
            enteredAt = change.at();
        }
    }

    private static double daysBetween(LocalDateTime from, LocalDateTime to) {
        return Math.max(0, Duration.between(from, to).toMillis()) / 86_400_000.0;
    }

    /** Median rounded to one decimal, or null when there is nothing to take a median of. */
    private static Double median(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        double middle = n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
        return Math.round(middle * 10.0) / 10.0;
    }
}
