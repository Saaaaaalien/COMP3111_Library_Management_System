package org.example.db;

import org.example.domain.Role;
import org.example.domain.User;

import java.sql.*;
import java.util.Optional;

/**
 * Data access for users table.
 */
public final class UserDao {

    private UserDao() {}

    /**
     * Inserts a new user. Returns the generated id.
     */
    public static long insert(String username, String fullName, Role role,
                             String passwordHash, String passwordSalt, String createdAt,
                             String bio, String employeeId) throws SQLException {
        String sql = """
            INSERT INTO users (username, full_name, role, password_hash, password_salt, created_at, bio, employee_id, avatar_path)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, fullName);
            ps.setString(3, role.name());
            ps.setString(4, passwordHash);
            ps.setString(5, passwordSalt);
            ps.setString(6, createdAt);
            ps.setString(7, bio);
            ps.setString(8, employeeId);
            ps.setNull(9, java.sql.Types.VARCHAR);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Insert user failed, no id returned");
    }

    /**
     * Finds a user by username.
     */
    public static Optional<User> findByUsername(String username) throws SQLException {
        String sql = "SELECT id, username, full_name, role, password_hash, password_salt, created_at, bio, employee_id, avatar_path, failed_login_attempts, locked_until FROM users WHERE username = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User u = mapRow(rs);
                    return Optional.of(u);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a user by id.
     */
    public static Optional<User> findById(long id) throws SQLException {
        String sql = "SELECT id, username, full_name, role, password_hash, password_salt, created_at, bio, employee_id, avatar_path, failed_login_attempts, locked_until FROM users WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    private static User mapRow(ResultSet rs) throws SQLException {
        int failed = 0;
        String locked = null;
        try {
            failed = rs.getInt("failed_login_attempts");
        } catch (SQLException ignored) { }
        try {
            locked = rs.getString("locked_until");
        } catch (SQLException ignored) { }
        String avatar = null;
        try {
            avatar = rs.getString("avatar_path");
        } catch (SQLException ignored) {
        }
        return new User(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("full_name"),
            Role.valueOf(rs.getString("role")),
            rs.getString("password_hash"),
            rs.getString("password_salt"),
            rs.getString("created_at"),
            rs.getString("bio"),
            rs.getString("employee_id"),
            avatar,
            failed,
            locked
        );
    }

    public static void updateFullNameAndBio(long userId, String fullName, String bio) throws SQLException {
        String sql = "UPDATE users SET full_name = ?, bio = ? WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, bio != null ? bio : "");
            ps.setLong(3, userId);
            ps.executeUpdate();
        }
    }

    public static void updateFullName(long userId, String fullName) throws SQLException {
        String sql = "UPDATE users SET full_name = ? WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    public static void updatePassword(long userId, String passwordHash, String passwordSalt) throws SQLException {
        String sql = "UPDATE users SET password_hash = ?, password_salt = ? WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setString(2, passwordSalt);
            ps.setLong(3, userId);
            ps.executeUpdate();
        }
    }

    public static void updateAvatarPath(long userId, String avatarPath) throws SQLException {
        String sql = "UPDATE users SET avatar_path = ? WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (avatarPath != null) {
                ps.setString(1, avatarPath);
            } else {
                ps.setNull(1, java.sql.Types.VARCHAR);
            }
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    /**
     * Increments failed login attempts and optionally sets locked_until (ISO instant).
     */
    public static void recordLoginFailure(long userId, String lockedUntil) throws SQLException {
        String sql = lockedUntil != null
            ? "UPDATE users SET failed_login_attempts = failed_login_attempts + 1, locked_until = ? WHERE id = ?"
            : "UPDATE users SET failed_login_attempts = failed_login_attempts + 1 WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (lockedUntil != null) {
                ps.setString(1, lockedUntil);
                ps.setLong(2, userId);
            } else {
                ps.setLong(1, userId);
            }
            ps.executeUpdate();
        }
    }

    /**
     * Clears lockout when it has expired so the user gets a fresh set of attempts.
     */
    public static void clearExpiredLockout(long userId) throws SQLException {
        String sql = "UPDATE users SET failed_login_attempts = 0, locked_until = NULL WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    /**
     * Resets failed_login_attempts and locked_until on successful login.
     * Delegates to clearExpiredLockout for shared behavior.
     */
    public static void recordLoginSuccess(long userId) throws SQLException {
        clearExpiredLockout(userId);
    }
}
