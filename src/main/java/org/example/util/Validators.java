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
     * Validates password strength: min 8 chars, at least one uppercase, one digit, one special character.
     * Call this in addition to validatePassword for strict validation.
     *
     * @throws ValidationException if invalid (includes all unsatisfied requirements)
     */
    public static void validatePasswordStrength(String password) throws ValidationException {
        validateRequired(password, "Password");
        StringBuilder errors = new StringBuilder();
        
        if (password.length() < PASSWORD_MIN_LENGTH) {
            errors.append("• At least " + PASSWORD_MIN_LENGTH + " characters\n");
        }
        if (!password.matches(".*[A-Z].*")) {
            errors.append("• At least one uppercase letter\n");
        }
        if (!password.matches(".*[0-9].*")) {
            errors.append("• At least one number\n");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            errors.append("• At least one special character (e.g. !@#$%^&*)\n");
        }
        
        if (!errors.isEmpty()) {
            throw new ValidationException("Password must contain:\n" + errors.toString().trim());
        }
    }

    /**
     * Returns a strength label for the password strength meter: "Weak", "Medium", or "Strong".
     */
    public static String getPasswordStrengthLabel(String password) {
        if (password == null || password.isEmpty()) return "";
        int score = 0;
        if (password.length() >= PASSWORD_MIN_LENGTH) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*[0-9].*")) score++;
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) score++;
        if (password.length() >= 12) score++;
        if (score <= 1) return "Weak";
        if (score <= 3) return "Medium";
        return "Strong";
    }

    /**
     * Validates first name: cannot be empty.
     */
    public static void validateFirstName(String firstName) throws ValidationException {
        validateRequired(firstName, "First name");
    }

    /**
     * Validates last name: cannot be empty.
     */
    public static void validateLastName(String lastName) throws ValidationException {
        validateRequired(lastName, "Last name");
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
