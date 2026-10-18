package com.mcverse.jobify.match;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcverse.jobify.common.exception.ServiceUnavailableException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Which engine is chosen, and what a missing secret does. */
class MatchWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(MatchConfig.class);

    @Test
    void offByDefaultAndEveryCallIsUnavailable() {
        runner.run(ctx -> {
            MatchEngine engine = ctx.getBean(MatchEngine.class);
            assertInstanceOf(DisabledMatchEngine.class, engine);
            assertThrows(ServiceUnavailableException.class, () -> engine.score(null, null));
        });
    }

    @Test
    void switchedOnWithATokenItIsTheGrpcEngine() {
        runner.withPropertyValues("matching.enabled=true", "matching.token=a-secret", "matching.target=localhost:1")
                .run(ctx -> assertInstanceOf(GrpcMatchEngine.class, ctx.getBean(MatchEngine.class)));
    }

    @Test
    void switchedOnWithoutATokenRefusesToStart() {
        runner.withPropertyValues("matching.enabled=true").run(ctx -> {
            assertTrue(ctx.getStartupFailure() != null);
            assertTrue(ctx.getStartupFailure().getMessage().contains("MATCHING_TOKEN")
                    || String.valueOf(ctx.getStartupFailure().getCause()).contains("MATCHING_TOKEN"));
        });
    }

    @Test
    void defaultsMatchThePlan() {
        runner.run(ctx -> {
            MatchProperties p = ctx.getBean(MatchProperties.class);
            assertTrue(!p.enabled());
            assertTrue(p.deadline().equals(Duration.ofMillis(800)));
            assertTrue(p.rankDeadline().equals(Duration.ofSeconds(2)));
            assertTrue(p.breaker().windowSize() == 10 && p.breaker().minimumCalls() == 5
                    && p.breaker().failureRatePercent() == 50 && p.breaker().openFor().equals(Duration.ofSeconds(10)));
        });
    }
}
