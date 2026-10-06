package com.mcverse.jobify.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Answers the two refusals that Spring Security produces itself, before a controller runs, in the standard error
 * envelope {@code {success:false, message, data:null, status}} that the front end shows verbatim:
 * <ul>
 *   <li><b>401</b> when there is no valid token (none, malformed, expired or for a deleted account). The front end
 *       logs the user out on 401, so an expired session ends cleanly instead of failing every call;</li>
 *   <li><b>403</b> when the caller is signed in but the route is not for their role (for example a seeker calling
 *       {@code /admin/**}).</li>
 * </ul>
 * Ownership and role rules inside services keep using the exceptions handled by {@code GlobalExceptionHandler}.
 */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String UNAUTHENTICATED_MESSAGE = "Your session has expired or is not valid. Please sign in again.";
    static final String FORBIDDEN_MESSAGE = "You do not have permission to do that.";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        write(response, HttpStatus.UNAUTHORIZED, UNAUTHENTICATED_MESSAGE);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        write(response, HttpStatus.FORBIDDEN, FORBIDDEN_MESSAGE);
    }

    private static void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // the messages are fixed text without quotes or control characters, so no escaping is needed
        response.getWriter().write("{\"success\":false,\"message\":\"" + message
                + "\",\"data\":null,\"status\":" + status.value() + "}");
    }
}
