package com.mcverse.matching.score;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ScoringWeights.class)
class ScoringConfig {

    @Bean
    Scorer scorer(ScoringWeights weights) {
        return new Scorer(weights);
    }

    @Bean
    Ranker ranker(Scorer scorer) {
        return new Ranker(scorer);
    }
}
