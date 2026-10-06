package com.mcverse.jobify.auth.security;

import com.mcverse.jobify.admin.AuditLog;
import com.mcverse.jobify.common.logging.ClientAddress;
import com.mcverse.jobify.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

/** Limits how often one client address may call login and register, whatever the credentials are. */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private final SlidingWindowCounter requests;
    private final int maxRequests;
    private final boolean trustForwardedFor;
    private final AuditLog auditLog;

    public AuthRateLimitFilter(Clock clock, SecurityProperties properties, AuditLog auditLog) {
        this.auditLog = auditLog;
        SecurityProperties.AuthRateLimit limit = properties.getAuthRateLimit();
        this.maxRequests = limit.getMaxRequests();
        this.requests = new SlidingWindowCounter(clock, Duration.ofSeconds(limit.getWindowSeconds()));
        this.trustForwardedFor = properties.isTrustForwardedFor();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !"POST".equals(request.getMethod())
                || !("/auth/login".equals(path) || "/auth/register".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = clientAddress(request) + " " + request.getRequestURI();
        if (requests.count(key) >= maxRequests) {
            long retryAfter = requests.secondsUntilOldestExpires(key);
            log.warn("Rate limit exceeded: {} sent more than {} requests; retry in {}s", key, maxRequests, retryAfter);
            auditLog.event("anonymous", "RATE_LIMITED", "path=" + request.getRequestURI() + " retryAfter=" + retryAfter + "s");
            reject(response, retryAfter);
            return;
        }
        requests.add(key);
        chain.doFilter(request, response);
    }

    private String clientAddress(HttpServletRequest request) {
        return ClientAddress.of(request, trustForwardedFor);
    }

    private static void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        int status = HttpStatus.TOO_MANY_REQUESTS.value();
        response.setStatus(status);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String message = "Too many requests. Please wait " + LoginAttemptTracker.minutes(retryAfterSeconds)
                + " and try again.";
        response.getWriter().write("{\"success\":false,\"message\":\"" + message
                + "\",\"data\":null,\"status\":" + status + "}");
    }
}
