package org.example.service;

/**
 * Thrown when login fails (invalid credentials).
 */
public class AuthException extends Exception {

    public AuthException(String message) {
        super(message);
    }
}
