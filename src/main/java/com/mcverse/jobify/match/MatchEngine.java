package com.mcverse.jobify.match;

import com.mcverse.jobify.match.MatchFacts.JobFacts;
import com.mcverse.jobify.match.MatchFacts.MatchResult;
import com.mcverse.jobify.match.MatchFacts.SeekerFacts;

/**
 * The port: what the rest of Jobify knows about matching. Only {@link GrpcMatchEngine} knows there is a network.
 * Implementations throw {@link com.mcverse.jobify.common.exception.ServiceUnavailableException} when the engine
 * cannot answer in time.
 */
public interface MatchEngine {

    MatchResult score(SeekerFacts seeker, JobFacts job);
}
