package com.mcverse.jobify.match;

import com.mcverse.jobify.common.exception.ServiceUnavailableException;
import com.mcverse.jobify.job.event.JobPostedEvent;
import com.mcverse.jobify.match.MatchFacts.JobFacts;
import com.mcverse.jobify.match.MatchFacts.RankedCandidate;
import com.mcverse.jobify.match.MatchFactsLoader.SeekerWithFacts;
import com.mcverse.jobify.notification.model.NotificationType;
import com.mcverse.jobify.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tells seekers when a newly posted job fits them well. It starts only after the job is committed and runs on its own
 * thread, so posting a job is never slower or failed by it; if the matching service is down nobody is told and nothing
 * else happens. Only seekers who share a required skill with the job (and are in its country, or it is remote, or they
 * set no country) are scored, at most {@code matching.alerts.max-seekers} of them, and each gets at most one message.
 * A seeker who switched {@code MATCH} notifications off is skipped by the notification gate, as for every type.
 */
@Service
public class MatchNotifier {

    private static final Logger log = LoggerFactory.getLogger(MatchNotifier.class);

    private final MatchFactsLoader facts;
    private final MatchEngine engine;
    private final NotificationService notifications;
    private final MatchProperties properties;

    public MatchNotifier(MatchFactsLoader facts, MatchEngine engine, NotificationService notifications,
                         MatchProperties properties) {
        this.facts = facts;
        this.engine = engine;
        this.notifications = notifications;
        this.properties = properties;
    }

    @Async("matchNotifyExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobPosted(JobPostedEvent event) {
        try {
            notifyFor(event.jobId());
        } catch (RuntimeException e) {
            // never let a notification problem reach the thread that posted the job
            log.error("New-match notifications for job {} failed", event.jobId(), e);
        }
    }

    /** Scores the likely seekers for one job and notifies the ones at or above the threshold. Returns how many were told. */
    public int notifyFor(int jobId) {
        MatchProperties.Alerts settings = properties.alerts();
        if (!settings.enabled()) {
            return 0;
        }
        Optional<JobFacts> job = facts.openJob(jobId);
        if (job.isEmpty()) {
            return 0;
        }
        List<SeekerWithFacts> seekers = facts.seekersInterestedIn(job.get(), settings.maxSeekers());
        if (seekers.isEmpty()) {
            return 0;
        }
        List<RankedCandidate> ranked;
        try {
            ranked = engine.rankCandidates(job.get(), seekers.stream().map(SeekerWithFacts::facts).toList());
        } catch (ServiceUnavailableException e) {
            log.warn("Job {} was posted but matching is unavailable: nobody was told about it", jobId);
            return 0;
        }
        Map<String, SeekerWithFacts> bySeeker = new HashMap<>();
        seekers.forEach(s -> bySeeker.put(s.facts().seekerId(), s));
        int told = 0;
        for (RankedCandidate r : ranked) {
            SeekerWithFacts seeker = bySeeker.get(r.seekerId());
            if (seeker == null || r.result().score() < settings.threshold()) {
                continue;
            }
            notifications.notify(seeker.username(), NotificationType.MATCH,
                    "A new job fits you: " + job.get().title(),
                    "A job that was just posted is a " + r.result().score() + "% match for your skills and preferences.",
                    jobId, null);
            told++;
        }
        log.info("Job {}: {} of {} scored seekers were told", jobId, told, seekers.size());
        return told;
    }
}
