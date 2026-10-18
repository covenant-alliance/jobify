package com.mcverse.jobify.match;

import com.mcverse.jobify.match.MatchFacts.JobFacts;
import com.mcverse.jobify.match.MatchFacts.MatchResult;
import com.mcverse.jobify.match.MatchFacts.RankedCandidate;
import com.mcverse.jobify.match.MatchFacts.RankedJob;
import com.mcverse.jobify.match.MatchFacts.SeekerFacts;

import java.util.List;

/**
 * The port: what the rest of Jobify knows about matching. Only {@link GrpcMatchEngine} knows there is a network.
 * Implementations throw {@link com.mcverse.jobify.common.exception.ServiceUnavailableException} when the engine
 * cannot answer in time.
 */
public interface MatchEngine {

    MatchResult score(SeekerFacts seeker, JobFacts job);

    /** The given jobs scored for one seeker, best first. At most 500 jobs per call (the contract's limit). */
    List<RankedJob> rankJobs(SeekerFacts seeker, List<JobFacts> jobs);

    /** The given candidates scored for one job, best first. At most 500 per call (the contract's limit). */
    List<RankedCandidate> rankCandidates(JobFacts job, List<SeekerFacts> candidates);
}
