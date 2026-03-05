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
            INSERT INTO users (username, full_name, role, password_hash, password_salt, created_at, bio, employee_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
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
        String sql = "SELECT id, username, full_name, role, password_hash, password_salt, created_at, bio, employee_id FROM users WHERE username = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a user by id.
     */
    public static Optional<User> findById(long id) throws SQLException {
        String sql = "SELECT id, username, full_name, role, password_hash, password_salt, created_at, bio, employee_id FROM users WHERE id = ?";
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
        return new User(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("full_name"),
            Role.valueOf(rs.getString("role")),
            rs.getString("password_hash"),
            rs.getString("password_salt"),
            rs.getString("created_at"),
            rs.getString("bio"),
            rs.getString("employee_id")
        );
    }
}
