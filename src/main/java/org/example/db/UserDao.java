package org.example.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.domain.Role;
import org.example.domain.User;

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
        String sql = "SELECT id, username, full_name, role, password_hash, password_salt, created_at, bio, employee_id, avatar_path, failed_login_attempts, locked_until, is_active FROM users WHERE username = ?";
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
        String sql = "SELECT id, username, full_name, role, password_hash, password_salt, created_at, bio, employee_id, avatar_path, failed_login_attempts, locked_until, is_active FROM users WHERE id = ?";
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
        boolean active = true;
        try {
            int isActiveVal = rs.getInt("is_active");
            // wasNull() returns true when the column was NULL; treat NULL as active (true)
            if (!rs.wasNull()) {
                active = isActiveVal != 0;
            }
        } catch (SQLException ignored) { }
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
            locked,
            active
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
     * Lightweight row mapper used by admin listing queries (findAll, findAllByRole, search).
     * Omits password_hash and password_salt — the Manage Users UI never needs credentials.
     */
    private static User mapRowAdmin(ResultSet rs) throws SQLException {
        String avatar = null;
        try { avatar = rs.getString("avatar_path"); } catch (SQLException ignored) { }
        int failed = 0;
        try { failed = rs.getInt("failed_login_attempts"); } catch (SQLException ignored) { }
        String locked = null;
        try { locked = rs.getString("locked_until"); } catch (SQLException ignored) { }
        boolean active = true;
        try {
            int isActiveVal = rs.getInt("is_active");
            if (!rs.wasNull()) active = isActiveVal != 0;
        } catch (SQLException ignored) { }
        return new User(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("full_name"),
            Role.valueOf(rs.getString("role")),
            null,  // password_hash — not selected, not needed for admin listings
            null,  // password_salt — not selected, not needed for admin listings
            rs.getString("created_at"),
            rs.getString("bio"),
            rs.getString("employee_id"),
            avatar,
            failed,
            locked,
            active
        );
    }

    /**
     * Returns all users ordered by role, then username.
     * Does not fetch password credentials (not needed for admin listings).
     */
    public static List<User> findAll() throws SQLException {
        String sql = "SELECT id, username, full_name, role, created_at, bio, employee_id, avatar_path, failed_login_attempts, locked_until, is_active FROM users ORDER BY role, username";
        Connection conn = Database.getConnection();
        List<User> users = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(mapRowAdmin(rs));
            }
        }
        return users;
    }

    /**
     * Returns all users matching a role filter, ordered by username.
     * Does not fetch password credentials (not needed for admin listings).
     */
    public static List<User> findAllByRole(Role role) throws SQLException {
        String sql = "SELECT id, username, full_name, role, created_at, bio, employee_id, avatar_path, failed_login_attempts, locked_until, is_active FROM users WHERE role = ? ORDER BY username";
        Connection conn = Database.getConnection();
        List<User> users = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRowAdmin(rs));
                }
            }
        }
        return users;
    }

    /**
     * Searches users by username or full_name (case-insensitive LIKE), optionally filtered by role.
     * Pass null for role to search all roles.
     * Does not fetch password credentials (not needed for admin listings).
     */
    public static List<User> search(String term, Role role) throws SQLException {
        String likeTerm = "%" + term + "%";
        String sql = role == null
            ? "SELECT id, username, full_name, role, created_at, bio, employee_id, avatar_path, failed_login_attempts, locked_until, is_active FROM users WHERE (username LIKE ? OR full_name LIKE ?) ORDER BY role, username"
            : "SELECT id, username, full_name, role, created_at, bio, employee_id, avatar_path, failed_login_attempts, locked_until, is_active FROM users WHERE (username LIKE ? OR full_name LIKE ?) AND role = ? ORDER BY username";
        Connection conn = Database.getConnection();
        List<User> users = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, likeTerm);
            ps.setString(2, likeTerm);
            if (role != null) {
                ps.setString(3, role.name());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRowAdmin(rs));
                }
            }
        }
        return users;
    }

    /**
     * Sets is_active for a user. Pass false to deactivate, true to reactivate.
     */
    public static void setActive(long userId, boolean active) throws SQLException {
        String sql = "UPDATE users SET is_active = ? WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    /**
     * Updates editable profile fields: full name, employee id, and bio.
     */
    public static void updateProfile(long userId, String fullName, String employeeId, String bio) throws SQLException {
        String sql = "UPDATE users SET full_name = ?, employee_id = ?, bio = ? WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, employeeId != null ? employeeId : "");
            ps.setString(3, bio != null ? bio : "");
            ps.setLong(4, userId);
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
