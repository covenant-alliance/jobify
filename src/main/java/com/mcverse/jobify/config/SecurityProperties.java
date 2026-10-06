package com.mcverse.jobify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Brute-force protection settings ({@code app.security.*}).
 * <ul>
 *   <li>{@code auth-rate-limit}: at most {@code max-requests} calls to login and to register per client address
 *       in {@code window-seconds}.</li>
 *   <li>{@code login-lock}: after {@code max-failures} failed logins for one username within
 *       {@code window-minutes}, that username is locked until the oldest failure leaves the window.</li>
 *   <li>{@code trust-forwarded-for}: read the client address from {@code X-Forwarded-For}. Enable only behind a
 *       proxy you control that overwrites the header; otherwise clients can spoof it to dodge the limit.</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private final AuthRateLimit authRateLimit = new AuthRateLimit();
    private final LoginLock loginLock = new LoginLock();
    private boolean trustForwardedFor = false;

    public AuthRateLimit getAuthRateLimit() { return authRateLimit; }
    public LoginLock getLoginLock()         { return loginLock; }
    public boolean isTrustForwardedFor()    { return trustForwardedFor; }
    public void setTrustForwardedFor(boolean trustForwardedFor) { this.trustForwardedFor = trustForwardedFor; }

    public static class AuthRateLimit {
        private int maxRequests = 30;
        private int windowSeconds = 60;

        public int getMaxRequests()  { return maxRequests; }
        public int getWindowSeconds() { return windowSeconds; }
        public void setMaxRequests(int maxRequests)      { this.maxRequests = maxRequests; }
        public void setWindowSeconds(int windowSeconds)  { this.windowSeconds = windowSeconds; }
    }

    public static class LoginLock {
        private int maxFailures = 5;
        private int windowMinutes = 10;

        public int getMaxFailures()  { return maxFailures; }
        public int getWindowMinutes() { return windowMinutes; }
        public void setMaxFailures(int maxFailures)      { this.maxFailures = maxFailures; }
        public void setWindowMinutes(int windowMinutes)  { this.windowMinutes = windowMinutes; }
    }
}
