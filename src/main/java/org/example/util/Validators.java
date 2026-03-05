package org.example.util;

/**
 * Centralized input validation for registration and forms.
 */
public final class Validators {

    public static final int USERNAME_MIN_LENGTH = 3;
    public static final int USERNAME_MAX_LENGTH = 50;
    public static final int PASSWORD_MIN_LENGTH = 8;
    /** Username: letters, digits, underscore only. */
    private static final String USERNAME_PATTERN = "^[a-zA-Z0-9_]+$";

    private Validators() {}

    /**
     * Validates that the value is non-null and not blank.
     *
     * @param value      the string to check
     * @param fieldLabel label for error message (e.g. "Username")
     * @throws ValidationException if invalid
     */
    public static void validateRequired(String value, String fieldLabel) throws ValidationException {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldLabel + " is required.");
        }
    }

    /**
     * Validates username: length and allowed characters.
     *
     * @throws ValidationException if invalid
     */
    public static void validateUsername(String username) throws ValidationException {
        validateRequired(username, "Username");
        if (username.length() < USERNAME_MIN_LENGTH) {
            throw new ValidationException("Username must be at least " + USERNAME_MIN_LENGTH + " characters.");
        }
        if (username.length() > USERNAME_MAX_LENGTH) {
            throw new ValidationException("Username must be at most " + USERNAME_MAX_LENGTH + " characters.");
        }
        if (!username.matches(USERNAME_PATTERN)) {
            throw new ValidationException("Username may only contain letters, numbers, and underscores.");
        }
    }

    /**
     * Validates password: minimum length.
     *
     * @throws ValidationException if invalid
     */
    public static void validatePassword(String password) throws ValidationException {
        validateRequired(password, "Password");
        if (password.length() < PASSWORD_MIN_LENGTH) {
            throw new ValidationException("Password must be at least " + PASSWORD_MIN_LENGTH + " characters.");
        }
    }

    /**
     * Validates full name: required and non-blank.
     *
     * @throws ValidationException if invalid
     */
    public static void validateFullName(String fullName) throws ValidationException {
        validateRequired(fullName, "Full name");
    }

    /**
     * Trims and returns null as empty string for optional fields (bio, employee ID).
     */
    public static String trimOptional(String value) {
        return value == null ? "" : value.trim();
    }
}
