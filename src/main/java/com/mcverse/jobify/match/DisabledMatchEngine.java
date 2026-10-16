package com.mcverse.jobify.match;

import com.mcverse.jobify.common.exception.ServiceUnavailableException;
import com.mcverse.jobify.match.MatchFacts.JobFacts;
import com.mcverse.jobify.match.MatchFacts.MatchResult;
import com.mcverse.jobify.match.MatchFacts.RankedCandidate;
import com.mcverse.jobify.match.MatchFacts.RankedJob;
import com.mcverse.jobify.match.MatchFacts.SeekerFacts;

import java.util.List;

/** Used while {@code matching.enabled} is false: every call is "unavailable", so nothing else needs a feature flag. */
public class DisabledMatchEngine implements MatchEngine {

    @Override
    public MatchResult score(SeekerFacts seeker, JobFacts job) {
        throw new ServiceUnavailableException(GrpcMatchEngine.UNAVAILABLE_MESSAGE);
    }

    @Override
    public List<RankedJob> rankJobs(SeekerFacts seeker, List<JobFacts> jobs) {
        throw new ServiceUnavailableException(GrpcMatchEngine.UNAVAILABLE_MESSAGE);
    }

    @Override
    public List<RankedCandidate> rankCandidates(JobFacts job, List<SeekerFacts> candidates) {
        throw new ServiceUnavailableException(GrpcMatchEngine.UNAVAILABLE_MESSAGE);
    }
}
