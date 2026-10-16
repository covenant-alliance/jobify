package com.mcverse.matching.score;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Weight of each part of the score. Tuned in configuration (matching.weights.*), not in code. */
@ConfigurationProperties(prefix = "matching.weights")
public record ScoringWeights(
        @DefaultValue("60") double skills,
        @DefaultValue("10") double location,
        @DefaultValue("8") double workMode,
        @DefaultValue("7") double employmentType,
        @DefaultValue("10") double rate,
        @DefaultValue("5") double experience) {

    public static ScoringWeights defaults() {
        return new ScoringWeights(60, 10, 8, 7, 10, 5);
    }
}
