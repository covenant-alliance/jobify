package com.mcverse.matching.score;

import com.mcverse.matching.v1.JobProfile;
import com.mcverse.matching.v1.Match;
import com.mcverse.matching.v1.RankedCandidate;
import com.mcverse.matching.v1.RankedJob;
import com.mcverse.matching.v1.SeekerProfile;
import java.util.Comparator;
import java.util.List;

/**
 * Orders scored jobs or candidates. Ties are broken the same way every time: newer job first, then
 * the lower job id; candidates by seeker id.
 */
public final class Ranker {

    private final Scorer scorer;

    public Ranker(Scorer scorer) {
        this.scorer = scorer;
    }

    public List<RankedJob> rankJobs(SeekerProfile seeker, List<JobProfile> jobs, int limit) {
        record Scored(JobProfile job, Match match) { }
        List<RankedJob> ranked = jobs.stream()
                .map(job -> new Scored(job, scorer.score(seeker, job)))
                .sorted(Comparator
                        .comparingInt((Scored s) -> s.match().getScore()).reversed()
                        .thenComparing(Comparator.comparingLong((Scored s) -> s.job().getPostedAtEpochSeconds()).reversed())
                        .thenComparingInt(s -> s.job().getJobId()))
                .map(s -> RankedJob.newBuilder().setJobId(s.job().getJobId()).setMatch(s.match()).build())
                .toList();
        return limited(ranked, limit);
    }

    public List<RankedCandidate> rankCandidates(JobProfile job, List<SeekerProfile> candidates, int limit) {
        record Scored(SeekerProfile seeker, Match match) { }
        List<RankedCandidate> ranked = candidates.stream()
                .map(seeker -> new Scored(seeker, scorer.score(seeker, job)))
                .sorted(Comparator
                        .comparingInt((Scored s) -> s.match().getScore()).reversed()
                        .thenComparing(s -> s.seeker().getSeekerId()))
                .map(s -> RankedCandidate.newBuilder().setSeekerId(s.seeker().getSeekerId()).setMatch(s.match()).build())
                .toList();
        return limited(ranked, limit);
    }

    private static <T> List<T> limited(List<T> list, int limit) {
        return limit > 0 && limit < list.size() ? list.subList(0, limit) : list;
    }
}
