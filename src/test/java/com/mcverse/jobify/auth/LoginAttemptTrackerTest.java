package com.mcverse.jobify.auth;

import com.mcverse.jobify.auth.security.LoginAttemptTracker;
import com.mcverse.jobify.common.exception.TooManyRequestsException;
import com.mcverse.jobify.config.SecurityProperties;
import com.mcverse.jobify.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptTrackerTest {

    private final MutableClock clock = new MutableClock();
    private LoginAttemptTracker tracker;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties();
        properties.getLoginLock().setMaxFailures(3);
        properties.getLoginLock().setWindowMinutes(10);
        tracker = new LoginAttemptTracker(clock, properties);
    }

    private void fail(String username, int times) {
        for (int i = 0; i < times; i++) {
            tracker.recordFailure(username);
        }
    }

    @Test
    void notLockedBelowTheLimit() {
        fail("alice", 2);
        assertDoesNotThrow(() -> tracker.assertNotLocked("alice"));
    }

    @Test
    void lockedAtTheLimitWithAReadableMessageAndRetryTime() {
        fail("alice", 3);
        TooManyRequestsException e = assertThrows(TooManyRequestsException.class,
                () -> tracker.assertNotLocked("alice"));
        assertEquals("Too many failed login attempts. Try again in 10 minutes.", e.getMessage());
        assertEquals(600, e.getRetryAfterSeconds());
    }

    @Test
    void lockEndsWhenTheOldestFailureLeavesTheWindow() {
        fail("alice", 3);
        clock.advance(Duration.ofMinutes(10).plusSeconds(1));
        assertDoesNotThrow(() -> tracker.assertNotLocked("alice"));
    }

    @Test
    void retryTimeShrinksAsTimePasses() {
        fail("alice", 3);
        clock.advance(Duration.ofMinutes(9));
        TooManyRequestsException e = assertThrows(TooManyRequestsException.class,
                () -> tracker.assertNotLocked("alice"));
        assertEquals(60, e.getRetryAfterSeconds());
        assertTrue(e.getMessage().endsWith("1 minute."));
    }

    @Test
    void successClearsTheFailures() {
        fail("alice", 2);
        tracker.recordSuccess("alice");
        fail("alice", 2);
        assertDoesNotThrow(() -> tracker.assertNotLocked("alice"));
    }

    @Test
    void usernamesAreIndependentAndCaseInsensitive() {
        fail("Alice", 3);
        assertThrows(TooManyRequestsException.class, () -> tracker.assertNotLocked("alice"));
        assertThrows(TooManyRequestsException.class, () -> tracker.assertNotLocked("  ALICE "));
        assertDoesNotThrow(() -> tracker.assertNotLocked("bob"));
    }

    @Test
    void unknownUsernamesAreLockedLikeRealOnes() {
        fail("does-not-exist", 3);
        assertThrows(TooManyRequestsException.class, () -> tracker.assertNotLocked("does-not-exist"));
    }
}
