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
        Breaker breaker) {

    public MatchProperties {
        if (breaker == null) {
            breaker = new Breaker(10, 5, 50, Duration.ofSeconds(10));
        }
    }

    /** Circuit breaker: after {@code minimumCalls} calls in the last {@code windowSize}, open when this share failed. */
    public record Breaker(
            @DefaultValue("10") int windowSize,
            @DefaultValue("5") int minimumCalls,
            @DefaultValue("50") int failureRatePercent,
            @DefaultValue("10s") Duration openFor) {}
}
