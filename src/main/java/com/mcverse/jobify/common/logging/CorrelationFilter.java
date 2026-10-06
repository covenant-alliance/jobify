package com.mcverse.jobify.common.logging;

import com.mcverse.jobify.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request a correlation id and puts it, the client address and (once known) the signed-in user in the
 * logging context, so every log line of one request can be found together, in the JSON log format as fields and in
 * the plain format in brackets.
 *
 * <p>The id is the caller's {@code X-Request-Id} when it is short and made only of letters, digits, dot, dash and
 * underscore (so a proxy or the front end can pass its own); anything else is replaced by a new random id, which
 * stops a caller from injecting log lines or huge values. It is returned in the {@code X-Request-Id} response
 * header. Runs first, before the security chain, so even rejected requests are correlated.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String REQUEST_ID = "requestId";
    public static final String CLIENT_IP = "clientIp";
    public static final String USER = "user";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final boolean trustForwardedFor;

    public CorrelationFilter(SecurityProperties properties) {
        this.trustForwardedFor = properties.isTrustForwardedFor();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || !SAFE_ID.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(REQUEST_ID, requestId);
        MDC.put(CLIENT_IP, ClientAddress.of(request, trustForwardedFor));
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID);
            MDC.remove(CLIENT_IP);
            MDC.remove(USER);
        }
    }
}
