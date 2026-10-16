package com.mcverse.jobify.common.exception;

/** A service Jobify depends on for one feature is down or too slow. Answered as 503; the rest of the API is unaffected. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
