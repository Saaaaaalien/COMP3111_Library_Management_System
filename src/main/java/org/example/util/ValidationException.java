package org.example.util;

/**
 * Thrown when user input fails validation (e.g. weak password, duplicate username).
 */
public class ValidationException extends Exception {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
