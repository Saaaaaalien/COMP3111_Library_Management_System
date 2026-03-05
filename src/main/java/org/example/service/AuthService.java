package org.example.service;

import org.example.db.UserDao;
import org.example.domain.Role;
import org.example.domain.User;
import org.example.security.PasswordHasher;
import org.example.util.ValidationException;
import org.example.util.Validators;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Registration and login using hashed passwords and validation.
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
        if (username == null) {
            throw new ValidationException("Username is required.");
        }
        validateStudentStaffRegistration(username, firstName, lastName, password, role);
        String fullName = firstName.trim() + " " + lastName.trim();
        return insertUser(username.trim(), fullName, password, role, null, null);
    }

    /**
     * Registers an author.
     *
     * Role is always AUTHOR. First/last name and password rules match Student/Staff.
     *
     * @return the new user's id
     * @throws ValidationException if validation fails or username already exists
     */
    public static long registerAuthor(String username, String firstName, String lastName, String password, String bio)
            throws ValidationException, SQLException {
        if (username == null) {
            throw new ValidationException("Username is required.");
        }
        Validators.validateUsername(username.trim());
        Validators.validateFirstName(firstName);
        Validators.validateLastName(lastName);
        Validators.validatePasswordStrength(password);
        ensureUsernameAvailable(username.trim());
        String fullName = firstName.trim() + " " + lastName.trim();
        return insertUser(username.trim(), fullName, password, Role.AUTHOR, bio, null);
    }

    /**
     * Registers a librarian.
     *
     * @param employeeId optional; may be null or empty
     * @return the new user's id
     * @throws ValidationException if validation fails or username already exists
     */
    public static long registerLibrarian(String username, String fullName, String password, String employeeId)
            throws ValidationException, SQLException {
        if (username == null) {
            throw new ValidationException("Username is required.");
        }
        Validators.validateUsername(username.trim());
        Validators.validateFullName(fullName);
        Validators.validatePassword(password);
        ensureUsernameAvailable(username.trim());
        String trimmedEmployeeId = Validators.trimOptional(employeeId);
        return insertUser(username.trim(), fullName.trim(), password, Role.LIBRARIAN, null, trimmedEmployeeId);
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

    private static void validateStudentStaffRegistration(String username, String firstName, String lastName, String password, Role role)
            throws ValidationException, SQLException {
        if (username == null) {
            throw new ValidationException("Username is required.");
        }
        Validators.validateUsername(username.trim());
        Validators.validateFirstName(firstName);
        Validators.validateLastName(lastName);
        Validators.validatePasswordStrength(password);
        if (role != Role.STUDENT && role != Role.STAFF) {
            throw new ValidationException("Role must be Student or Staff.");
        }
        ensureUsernameAvailable(username.trim());
    }

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
