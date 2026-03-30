package org.example.service;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.example.db.UserDao;
import org.example.domain.Role;
import org.example.domain.User;
import org.example.security.PasswordHasher;
import org.example.util.ValidationException;
import org.example.util.Validators;

/**
 * Registration and login using hashed passwords and validation.
 * <p>
 * Used by Task 1 for Student/Staff: {@link #registerStudentStaff} and {@link #login}.
 * Passwords are hashed with a per-user salt; failed login attempts can lock an account temporarily.
 */
public final class AuthService {

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    private AuthService() {}

    /**
     * Registers a student or staff user.
     *
     * @return the new user's id
     * @throws ValidationException if validation fails or username already exists
     */
    public static long registerStudentStaff(String username, String firstName, String lastName, String password, Role role)
            throws ValidationException, SQLException {
        List<String> errors = new ArrayList<>();

        String trimmedUsername = username == null ? null : username.trim();
        if (trimmedUsername == null) {
            errors.add("Username is required.");
        } else {
            try {
                Validators.validateUsername(trimmedUsername);
            } catch (ValidationException ex) {
                errors.add(ex.getMessage());
            }
            // Only check uniqueness when we have a non-empty username.
            if (!trimmedUsername.isEmpty()) {
                try {
                    ensureUsernameAvailable(trimmedUsername);
                } catch (ValidationException ex) {
                    errors.add(ex.getMessage());
                }
            }
        }

        try {
            Validators.validateFirstName(firstName);
        } catch (ValidationException ex) {
            errors.add(ex.getMessage());
        }
        try {
            Validators.validateLastName(lastName);
        } catch (ValidationException ex) {
            errors.add(ex.getMessage());
        }

        try {
            Validators.validatePasswordStrength(password);
        } catch (ValidationException ex) {
            errors.add(ex.getMessage());
        }

        if (role != Role.STUDENT && role != Role.STAFF) {
            errors.add("Role must be Student or Staff.");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(String.join("\n", errors));
        }

        String fullName = firstName.trim() + " " + lastName.trim();
        return insertUser(trimmedUsername, fullName, password, role, null, null);
    }

    /**
     * Registers an author.
     * Role is always AUTHOR. First/last name and password rules match Student/Staff.
     *
     * @throws ValidationException if validation fails or username already exists
     */
    public static void registerAuthor(String username, String firstName, String lastName, String password, String bio)
            throws ValidationException, SQLException {
        List<String> errors = new ArrayList<>();

        String trimmedUsername = username == null ? null : username.trim();
        if (trimmedUsername == null) {
            errors.add("Username is required.");
        } else {
            try {
                Validators.validateUsername(trimmedUsername);
            } catch (ValidationException ex) {
                errors.add(ex.getMessage());
            }
            if (!trimmedUsername.isEmpty()) {
                try {
                    ensureUsernameAvailable(trimmedUsername);
                } catch (ValidationException ex) {
                    errors.add(ex.getMessage());
                }
            }
        }

        boolean firstBlank = firstName == null || firstName.isBlank();
        boolean lastBlank = lastName == null || lastName.isBlank();
        if (firstBlank || lastBlank) {
            errors.add("Full name is required (First name and Last name).");
        } else {
            // If both are non-blank, still validate allowed/required constraints.
            try {
                Validators.validateFirstName(firstName);
            } catch (ValidationException ex) {
                errors.add(ex.getMessage());
            }
            try {
                Validators.validateLastName(lastName);
            } catch (ValidationException ex) {
                errors.add(ex.getMessage());
            }
        }

        try {
            Validators.validatePasswordStrength(password);
        } catch (ValidationException ex) {
            errors.add(ex.getMessage());
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(String.join("\n", errors));
        }

        String fullName = firstName.trim() + " " + lastName.trim();
        insertUser(trimmedUsername, fullName, password, Role.AUTHOR, bio, null);
    }

    /**
     * Registers a librarian.
     * First/last name and password rules match Student/Staff.
     *
     * @param employeeId optional; may be null or empty
     * @return the new user's id
     * @throws ValidationException if validation fails or username already exists
     */
    public static long registerLibrarian(String username, String firstName, String lastName, String password, String employeeId)
            throws ValidationException, SQLException {
        List<String> errors = new ArrayList<>();

        String trimmedUsername = username == null ? null : username.trim();
        if (trimmedUsername == null) {
            errors.add("Username is required.");
        } else {
            try {
                Validators.validateUsername(trimmedUsername);
            } catch (ValidationException ex) {
                errors.add(ex.getMessage());
            }
            if (!trimmedUsername.isEmpty()) {
                try {
                    ensureUsernameAvailable(trimmedUsername);
                } catch (ValidationException ex) {
                    errors.add(ex.getMessage());
                }
            }
        }

        try {
            Validators.validateFirstName(firstName);
        } catch (ValidationException ex) {
            errors.add(ex.getMessage());
        }
        try {
            Validators.validateLastName(lastName);
        } catch (ValidationException ex) {
            errors.add(ex.getMessage());
        }

        try {
            Validators.validatePasswordStrength(password);
        } catch (ValidationException ex) {
            errors.add(ex.getMessage());
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(String.join("\n", errors));
        }

        String fullName = firstName.trim() + " " + lastName.trim();
        String trimmedEmployeeId = Validators.trimOptional(employeeId);
        return insertUser(trimmedUsername, fullName, password, Role.LIBRARIAN, null, trimmedEmployeeId);
    }

    /**
     * Authenticates by username and password.
     *
     * @return the authenticated user
     * @throws AuthException if credentials are invalid (same message for wrong password or unknown user)
     */
    public static User login(String username, String password) throws AuthException, SQLException {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new AuthException("Invalid username or password. Please check your username and password.");
        }
        var userOpt = UserDao.findByUsername(username.trim());
        if (userOpt.isEmpty()) {
            throw new AuthException("Invalid username or password. Please check your username and password.");
        }
        User user = userOpt.get();
        String lockedUntil = user.getLockedUntil();
        if (lockedUntil != null && !lockedUntil.isEmpty()) {
            try {
                Instant lockEnd = Instant.parse(lockedUntil);
                if (Instant.now().isBefore(lockEnd)) {
                    throw new AuthException("Account is temporarily locked due to too many failed attempts. Try again after " + LOCKOUT_MINUTES + " minutes.");
                }
                UserDao.clearExpiredLockout(user.getId());
                user = UserDao.findByUsername(username.trim()).orElse(user);
            } catch (AuthException e) {
                throw e;
            } catch (Exception ignored) {
                // Invalid timestamp format; treat as not locked so user can still try to log in
            }
        }
        if (!PasswordHasher.verify(password, user.getPasswordSalt(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            String newLockedUntil = (attempts >= MAX_LOGIN_ATTEMPTS)
                ? Instant.now().plus(LOCKOUT_MINUTES, ChronoUnit.MINUTES).toString()
                : null;
            UserDao.recordLoginFailure(user.getId(), newLockedUntil);
            throw new AuthException("Invalid username or password. Please check your username and password.");
        }
        UserDao.recordLoginSuccess(user.getId());
        return user;
    }

    /**
     * Returns an error message directing the user to the correct login portal based on their role.
     * Used when a user attempts to log in on a portal that does not match their account type
     * (e.g. a student trying to log in on the Librarian screen).
     *
     * @param user the authenticated user whose role does not match the current portal
     * @return a message such as "This username is registered as a student. Please use the Student/Staff login."
     */
    public static String getWrongPortalMessage(User user) {
        if (user == null) {
            return "Invalid account type.";
        }
        return switch (user.getRole()) {
            case STUDENT -> "This username is registered as a student. Please use the Student/Staff login.";
            case STAFF -> "This username is registered as staff. Please use the Student/Staff login.";
            case AUTHOR -> "This username is registered as an author. Please use the Author login.";
            case LIBRARIAN -> "This username is registered as a librarian. Please use the Librarian login.";
        };
    }

    /** Throws ValidationException if the username is already taken. */
    private static void ensureUsernameAvailable(String username) throws ValidationException, SQLException {
        if (UserDao.findByUsername(username).isPresent()) {
            throw new ValidationException("Username is already taken.");
        }
    }

    private static long insertUser(String username, String fullName, String password, Role role,
                                   String bio, String employeeId) throws SQLException {
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash(password, salt);
        String createdAt = Instant.now().toString();
        return UserDao.insert(username, fullName, role, hash, salt, createdAt, bio, employeeId);
    }
}
