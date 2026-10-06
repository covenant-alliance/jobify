package com.mcverse.jobify.auth;

import com.mcverse.jobify.auth.security.SlidingWindowCounter;
import com.mcverse.jobify.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlidingWindowCounterTest {

    private final MutableClock clock = new MutableClock();
    private final SlidingWindowCounter counter = new SlidingWindowCounter(clock, Duration.ofSeconds(60));

    @Test
    void countsEventsPerKey() {
        counter.add("a");
        counter.add("a");
        counter.add("b");
        assertEquals(2, counter.count("a"));
        assertEquals(1, counter.count("b"));
        assertEquals(0, counter.count("never-seen"));
    }

    @Test
    void eventsLeaveTheWindowAsTimePasses() {
        counter.add("a");
        clock.advance(Duration.ofSeconds(30));
        counter.add("a");
        assertEquals(2, counter.count("a"));

        clock.advance(Duration.ofSeconds(31)); // first event is now 61s old
        assertEquals(1, counter.count("a"));

        clock.advance(Duration.ofSeconds(30));
        assertEquals(0, counter.count("a"));
    }

    @Test
    void reportsWhenTheOldestEventExpires() {
        counter.add("a");
        clock.advance(Duration.ofSeconds(20));
        counter.add("a");
        assertEquals(40, counter.secondsUntilOldestExpires("a"));
        clock.advance(Duration.ofSeconds(39));
        assertEquals(1, counter.secondsUntilOldestExpires("a"));
    }

    @Test
    void retryAfterIsAtLeastOneSecondEvenForUnknownKeys() {
        assertEquals(1, counter.secondsUntilOldestExpires("never-seen"));
    }

    @Test
    void clearForgetsAKey() {
        counter.add("a");
        counter.clear("a");
        assertEquals(0, counter.count("a"));
    }

    @Test
    void retryAfterIsOneSecondForUnknownOrExpiredKeys() {
        assertEquals(1, counter.secondsUntilOldestExpires("never-seen"));
        counter.add("a");
        clock.advance(Duration.ofSeconds(120));
        assertEquals(1, counter.secondsUntilOldestExpires("a"));
    }

    @Test
    void retryAfterCountsDownToTheOldestEvent() {
        counter.add("a");
        clock.advance(Duration.ofSeconds(20));
        assertEquals(40, counter.secondsUntilOldestExpires("a"));
    }

    @Test
    void manyOldKeysAreDroppedSoMemoryDoesNotGrowForever() {
        for (int i = 0; i < 10_001; i++) {
            counter.add("old-" + i);
        }
        clock.advance(Duration.ofSeconds(61));
        counter.add("trigger"); // crossing the threshold again prunes everything that has expired
        assertEquals(0, counter.count("old-0"));
        assertEquals(1, counter.count("trigger"));
    }
}
