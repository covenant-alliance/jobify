package com.mcverse.jobify.auth.security;

import com.mcverse.jobify.common.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Rules for choosing a password, on top of the length limits on the request objects (8 to 72 characters).
 * Failures are reported as {@link IllegalArgumentException}, which the API answers with 400 and the message.
 */
@Component
public class PasswordPolicy {

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "password1", "password12", "password123", "passw0rd", "12345678", "123456789", "1234567890",
            "qwertyui", "qwertyuiop", "qwerty123", "iloveyou", "11111111", "00000000", "abc12345", "letmein1",
            "welcome1", "welcome123", "admin123", "administrator", "jobify123", "changeme");

    /** @throws IllegalArgumentException with a readable message if the password is not acceptable */
    public void validate(String username, String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be empty.");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }
        if (username != null && password.equalsIgnoreCase(username.trim())) {
            throw new IllegalArgumentException("Password must not be the same as your username.");
        }
        if (COMMON_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("That password is too common. Please choose a less predictable one.");
        }
    }

    /** A new password must differ from the current one. */
    public void validateChange(String username, String currentPassword, String newPassword) {
        validate(username, newPassword);
        if (newPassword.equals(currentPassword)) {
            throw new BusinessRuleException("The new password must be different from the current one.");
        }
    }
}
