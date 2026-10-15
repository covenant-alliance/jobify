package com.mcverse.jobify.common.exception;

/** The refresh token is unknown, expired, revoked, already used, or its account is gone or disabled: answer 401. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Your session has expired or is not valid. Please sign in again.");
    }
}
