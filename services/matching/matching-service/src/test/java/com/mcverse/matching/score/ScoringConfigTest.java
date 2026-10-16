package com.mcverse.matching.score;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ScoringConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(ScoringConfig.class);

    @Test
    void defaultsMatchThePlan() {
        runner.run(ctx -> assertEquals(ScoringWeights.defaults(), ctx.getBean(ScoringWeights.class)));
    }

    @Test
    void weightsAreChangedByConfigurationOnly() {
        runner.withPropertyValues("matching.weights.skills=80", "matching.weights.work-mode=0")
                .run(ctx -> {
                    ScoringWeights w = ctx.getBean(ScoringWeights.class);
                    assertEquals(80, w.skills());
                    assertEquals(0, w.workMode());
                    assertEquals(10, w.location());
                });
    }
}
