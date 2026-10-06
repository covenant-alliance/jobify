package com.mcverse.jobify.common;

import com.mcverse.jobify.common.exception.GlobalExceptionHandler;
import com.mcverse.jobify.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The front end shows these messages as they are, so each one is part of the contract: readable, with the right
 * status, and never leaking internal details.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static void assertError(ApiResponse<Void> response, int status, String message) {
        assertFalse(response.success());
        assertEquals(status, response.status());
        assertEquals(message, response.message());
        assertNull(response.data());
    }

    @Test
    void accessDeniedIs403WithAPlainMessage() {
        assertError(handler.handleAccessDenied(new AccessDeniedException("internal detail")), 403,
                "You do not have permission to do that.");
    }

    @Test
    void aMissingParameterNamesIt() {
        assertError(handler.handleMissingParameter(new MissingServletRequestParameterException("available", "Boolean")),
                400, "The parameter 'available' is required.");
    }

    @Test
    void aDataConflictIs409AndDoesNotLeakTheSql() {
        ApiResponse<Void> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("duplicate key value violates unique constraint uk_x"));
        assertError(response, 409, "That change conflicts with existing data. Please refresh and try again.");
    }

    @Test
    void anOversizedUploadIs422() {
        assertError(handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(5_000_000)), 422,
                "File is too large.");
    }

    @Test
    void anUnexpectedErrorIs500AndNeverShowsItsMessage() {
        ApiResponse<Void> response = handler.handleGeneric(new IllegalStateException("password=hunter2 at Db.java:42"));
        assertError(response, 500, "An unexpected error occurred");
    }

    @Test
    void aValidationFailureWithoutFieldErrorsStillSaysSomething() throws Exception {
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("accessDeniedIs403WithAPlainMessage"), -1);
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(parameter, new BeanPropertyBindingResult(new Object(), "body"));
        assertError(handler.handleValidation(exception), 400, "The request is not valid.");
    }
}
