package com.mcverse.jobify.common.logging;

import jakarta.servlet.http.HttpServletRequest;

/** The address of the caller, shared by the rate limit and the logs so both name the same client. */
public final class ClientAddress {

    private ClientAddress() {}

    /**
     * @param trustForwardedFor read the first {@code X-Forwarded-For} entry. Only true behind a proxy that
     *                          overwrites the header, otherwise a client can invent its address.
     */
    public static String of(HttpServletRequest request, boolean trustForwardedFor) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
