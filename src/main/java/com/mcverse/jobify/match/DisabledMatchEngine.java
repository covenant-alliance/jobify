package com.mcverse.jobify.match;

import com.mcverse.jobify.common.exception.ServiceUnavailableException;
import com.mcverse.jobify.match.MatchFacts.JobFacts;
import com.mcverse.jobify.match.MatchFacts.MatchResult;
import com.mcverse.jobify.match.MatchFacts.SeekerFacts;

/** Used while {@code matching.enabled} is false: every call is "unavailable", so nothing else needs a feature flag. */
public class DisabledMatchEngine implements MatchEngine {

    @Override
    public MatchResult score(SeekerFacts seeker, JobFacts job) {
        throw new ServiceUnavailableException(GrpcMatchEngine.UNAVAILABLE_MESSAGE);
    }
}
