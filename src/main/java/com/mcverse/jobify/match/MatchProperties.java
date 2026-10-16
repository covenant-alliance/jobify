package com.mcverse.jobify.match;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Settings of the connection to the matching service (docs/MATCHING_SERVICE.md, sections 6 and 7). */
@ConfigurationProperties(prefix = "matching")
public record MatchProperties(
        /* Off by default: the three match endpoints then answer 503 and nothing else is affected. */
        @DefaultValue("false") boolean enabled,
        /* gRPC target, for example matching:9090 or dns:///matching:9090. */
        @DefaultValue("localhost:9090") String target,
        /* Shared secret, sent as "authorization: Bearer ...". Required when enabled. */
        @DefaultValue("") String token,
        /* How long one call for a single job may take. */
        @DefaultValue("800ms") Duration deadline,
        /* How long a ranking call (many jobs or candidates) may take. */
        @DefaultValue("2s") Duration rankDeadline,
        /* Most jobs narrowed in SQL and sent for one ranking. The contract allows 500. */
        @DefaultValue("200") int maxCandidates,
        /* Most applicants of one job ranked in one call (newest applications first). The contract allows 500. */
        @DefaultValue("500") int maxApplicants,
        Breaker breaker,
        Alerts alerts) {

    public MatchProperties {
        if (breaker == null) {
            breaker = new Breaker(10, 5, 50, Duration.ofSeconds(10));
        }
        if (alerts == null) {
            alerts = new Alerts(false, 75, 200);
        }
    }

    /** Circuit breaker: after {@code minimumCalls} calls in the last {@code windowSize}, open when this share failed. */
    public record Breaker(
            @DefaultValue("10") int windowSize,
            @DefaultValue("5") int minimumCalls,
            @DefaultValue("50") int failureRatePercent,
            @DefaultValue("10s") Duration openFor) {}

    /** "A new job fits you" notifications (story M10). Off by default: it spends matching calls on every new job. */
    public record Alerts(
            @DefaultValue("false") boolean enabled,
            /* A seeker is told when the score of a new job for them is at least this (0 to 100). */
            @DefaultValue("75") int threshold,
            /* Most seekers scored for one new job. The contract allows 500. */
            @DefaultValue("200") int maxSeekers) {}
}
