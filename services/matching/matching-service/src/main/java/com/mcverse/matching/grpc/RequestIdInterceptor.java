package com.mcverse.matching.grpc;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

/**
 * Puts the caller's {@code x-request-id} into the logging context for every line logged while a call is handled, so
 * one user action can be followed in Jobify's log and in this one with a single search. It has to wrap the token check
 * so that a rejected call is logged with its id too. Spring Grpc applies global interceptors in bean order (an @Order
 * annotation made no difference when tried) and this class sorts before TokenAuthInterceptor; the test
 * MatchingServerTest.aRejectedCallIsLoggedWithItsRequestIdToo fails if that ever changes. An id that is not plain
 * characters is ignored.
 */
@Component
@GlobalServerInterceptor
public class RequestIdInterceptor implements ServerInterceptor {

    public static final String MDC_KEY = "requestId";
    private static final Metadata.Key<String> REQUEST_ID =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String id = headers.get(REQUEST_ID);
        if (id == null || !SAFE.matcher(id).matches()) {
            return next.startCall(call, headers);
        }
        // calls arrive on one thread and are handled on another, so the id is set around each step, not once
        MDC.put(MDC_KEY, id);
        try {
            return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(next.startCall(call, headers)) {
                @Override public void onMessage(ReqT message) { within(() -> super.onMessage(message)); }
                @Override public void onHalfClose() { within(super::onHalfClose); }
                @Override public void onCancel() { within(super::onCancel); }
                @Override public void onComplete() { within(super::onComplete); }
                @Override public void onReady() { within(super::onReady); }

                private void within(Runnable step) {
                    MDC.put(MDC_KEY, id);
                    try {
                        step.run();
                    } finally {
                        MDC.remove(MDC_KEY);
                    }
                }
            };
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
