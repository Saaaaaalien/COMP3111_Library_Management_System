package org.example.domain;

/**
 * User entity: student, staff, author, or librarian.
 */
public class User {

    private final long id;
    private final String username;
    private final String fullName;
    private final Role role;
    private final String passwordHash;
    private final String passwordSalt;
    private final String createdAt;
    private final String bio;
    private final String employeeId;
    private final String avatarPath;
    private final int failedLoginAttempts;
    private final String lockedUntil;
    private final boolean active;

    public User(long id, String username, String fullName, Role role,
                String passwordHash, String passwordSalt, String createdAt,
                String bio, String employeeId, int failedLoginAttempts, String lockedUntil) {
        this(id, username, fullName, role, passwordHash, passwordSalt, createdAt, bio, employeeId, null, failedLoginAttempts, lockedUntil);
    }

    public User(long id, String username, String fullName, Role role,
                String passwordHash, String passwordSalt, String createdAt,
                String bio, String employeeId, String avatarPath, int failedLoginAttempts, String lockedUntil) {
        this(id, username, fullName, role, passwordHash, passwordSalt, createdAt, bio, employeeId, avatarPath, failedLoginAttempts, lockedUntil, true);
    }

    public User(long id, String username, String fullName, Role role,
                String passwordHash, String passwordSalt, String createdAt,
                String bio, String employeeId, String avatarPath, int failedLoginAttempts, String lockedUntil,
                boolean active) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.createdAt = createdAt;
        this.bio = bio;
        this.employeeId = employeeId;
        this.avatarPath = avatarPath;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.active = active;
    }

    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public String getPasswordHash() { return passwordHash; }
    public String getPasswordSalt() { return passwordSalt; }
    public String getCreatedAt() { return createdAt; }
    public String getBio() { return bio; }
    public String getEmployeeId() { return employeeId; }
    public String getAvatarPath() { return avatarPath; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public String getLockedUntil() { return lockedUntil; }
    public boolean isActive() { return active; }
}
