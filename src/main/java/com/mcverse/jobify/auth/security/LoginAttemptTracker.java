package com.mcverse.jobify.auth.security;

import com.mcverse.jobify.common.exception.TooManyRequestsException;
import com.mcverse.jobify.config.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

/**
 * Locks a username after too many failed logins. Unknown usernames are tracked exactly like real ones, so the
 * lock never reveals whether an account exists. A successful login clears the count.
 */
@Component
public class LoginAttemptTracker {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptTracker.class);

    private final SlidingWindowCounter failures;
    private final int maxFailures;

    public LoginAttemptTracker(Clock clock, SecurityProperties properties) {
        SecurityProperties.LoginLock lock = properties.getLoginLock();
        this.maxFailures = lock.getMaxFailures();
        this.failures = new SlidingWindowCounter(clock, Duration.ofMinutes(lock.getWindowMinutes()));
    }

    /** @throws TooManyRequestsException when the username is locked */
    public void assertNotLocked(String username) {
        String key = key(username);
        if (failures.count(key) >= maxFailures) {
            long retryAfter = failures.secondsUntilOldestExpires(key);
            log.warn("Login blocked: '{}' is locked after {} failed attempts, retry in {}s",
                    key, maxFailures, retryAfter);
            throw new TooManyRequestsException(
                    "Too many failed login attempts. Try again in " + minutes(retryAfter) + ".", retryAfter);
        }
    }

    /** @return true if this failure locked the account */
    public boolean recordFailure(String username) {
        String key = key(username);
        failures.add(key);
        int count = failures.count(key);
        log.warn("Login failed for '{}' ({} of {} allowed failures)", key, count, maxFailures);
        if (count == maxFailures) {
            log.warn("Account '{}' is now locked after {} failed logins", key, maxFailures);
        }
        return count == maxFailures;
    }

    public void recordSuccess(String username) {
        failures.clear(key(username));
    }

    static String minutes(long seconds) {
        long minutes = (seconds + 59) / 60;
        return minutes <= 1 ? "1 minute" : minutes + " minutes";
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
