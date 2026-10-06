package com.mcverse.jobify.auth;

import com.mcverse.jobify.auth.security.PasswordPolicy;
import com.mcverse.jobify.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    private String rejection(String username, String password) {
        return assertThrows(IllegalArgumentException.class, () -> policy.validate(username, password)).getMessage();
    }

    @Test
    void acceptsAnOrdinaryPassword() {
        assertDoesNotThrow(() -> policy.validate("jane_doe", "correct-horse-battery"));
    }

    @Test
    void rejectsShortOrEmptyPasswords() {
        assertEquals("Password must be at least 8 characters.", rejection("jane", "short"));
        assertEquals("Password must not be empty.", rejection("jane", "   "));
        assertEquals("Password must not be empty.", rejection("jane", null));
    }

    @Test
    void rejectsThePasswordThatEqualsTheUsernameIgnoringCase() {
        assertEquals("Password must not be the same as your username.", rejection("janedoe99", "janedoe99"));
        assertEquals("Password must not be the same as your username.", rejection("JaneDoe99", "janedoe99"));
        assertEquals("Password must not be the same as your username.", rejection("  janedoe99 ", "JANEDOE99"));
    }

    @Test
    void rejectsCommonPasswordsInAnyCase() {
        String message = "That password is too common. Please choose a less predictable one.";
        assertEquals(message, rejection("jane", "password"));
        assertEquals(message, rejection("jane", "PassWord123"));
        assertEquals(message, rejection("jane", "12345678"));
        assertEquals(message, rejection("jane", "qwertyuiop"));
    }

    @Test
    void changeMustDifferFromTheCurrentPassword() {
        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> policy.validateChange("jane", "correct-horse-1", "correct-horse-1"));
        assertEquals("The new password must be different from the current one.", e.getMessage());
        assertDoesNotThrow(() -> policy.validateChange("jane", "correct-horse-1", "correct-horse-2"));
    }

    @Test
    void changeAlsoAppliesTheGeneralRules() {
        assertThrows(IllegalArgumentException.class, () -> policy.validateChange("jane", "old-password-1", "jane"));
    }
}
