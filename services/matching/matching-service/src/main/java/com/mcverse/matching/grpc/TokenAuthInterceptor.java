package com.mcverse.matching.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

/**
 * Rejects every call that does not carry {@code authorization: Bearer <MATCHING_TOKEN>}. The standard
 * health service stays open so a platform probe needs no secret. Applied to all services by
 * {@link GlobalServerInterceptor}.
 */
@Component
@GlobalServerInterceptor
public class TokenAuthInterceptor implements ServerInterceptor {

    static final String DEV_PLACEHOLDER = "dev-matching-token";
    private static final String HEALTH_PREFIX = "grpc.health.v1.Health/";
    private static final String BEARER = "Bearer ";
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Logger log = LoggerFactory.getLogger(TokenAuthInterceptor.class);

    private final byte[] expected;

    public TokenAuthInterceptor(@Value("${matching.auth.token:}") String token, Environment environment) {
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));
        if (!dev && (token == null || token.isBlank())) {
            throw new IllegalStateException("MATCHING_TOKEN is not set. Outside the dev profile the matching "
                    + "service refuses to start without the shared secret.");
        }
        if (!dev && DEV_PLACEHOLDER.equals(token)) {
            throw new IllegalStateException("MATCHING_TOKEN is the public development placeholder. Set a real secret.");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("matching.auth.token must not be empty.");
        }
        this.expected = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        if (call.getMethodDescriptor().getFullMethodName().startsWith(HEALTH_PREFIX) || authorized(headers)) {
            return next.startCall(call, headers);
        }
        log.warn("Rejected call to {}: missing or wrong token", call.getMethodDescriptor().getFullMethodName());
        call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid credentials"), new Metadata());
        return new ServerCall.Listener<>() { };
    }

    private boolean authorized(Metadata headers) {
        String value = headers.get(AUTHORIZATION);
        if (value == null || !value.startsWith(BEARER)) {
            return false;
        }
        // Constant-time comparison, so response time does not reveal how much of the token was right.
        return MessageDigest.isEqual(value.substring(BEARER.length()).getBytes(StandardCharsets.UTF_8), expected);
    }
}
