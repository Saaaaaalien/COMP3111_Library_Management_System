package org.example.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.domain.AppNotification;

/**
 * Data access for notifications table.
 */
public final class NotificationDao {

    private NotificationDao() {}

    public static long insert(long userId, String category, String title, String body,
                             String createdAt, Integer priority, String dedupeKey) throws SQLException {
        String sql = """
            INSERT INTO notifications (user_id, category, title, body, created_at, priority, dedupe_key)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, category);
            ps.setString(3, title);
            ps.setString(4, body);
            ps.setString(5, createdAt);
            ps.setInt(6, priority != null ? priority : 0);
            if (dedupeKey != null) {
                ps.setString(7, dedupeKey);
            } else {
                ps.setNull(7, Types.VARCHAR);
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            if (dedupeKey != null && e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                return -1;
            }
            throw e;
        }
        throw new SQLException("Insert notification failed");
    }

    /**
     * Inserts if dedupe_key is unique for user; returns id or -1 if duplicate.
     */
    public static long insertDeduped(long userId, String category, String title, String body,
                                    String createdAt, int priority, String dedupeKey) throws SQLException {
        return insert(userId, category, title, body, createdAt, priority, dedupeKey);
    }

    /**
     * Bulk-deduped insert using INSERT OR IGNORE so no exception is thrown for
     * existing dedupe keys.  Preferred over insertDeduped() inside sync loops.
     */
    public static void insertOrIgnoreDeduped(long userId, String category, String title, String body,
                                             String createdAt, int priority, String dedupeKey) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO notifications
              (user_id, category, title, body, created_at, priority, dedupe_key)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, category);
            ps.setString(3, title);
            ps.setString(4, body);
            ps.setString(5, createdAt);
            ps.setInt(6, priority);
            if (dedupeKey != null) {
                ps.setString(7, dedupeKey);
            } else {
                ps.setNull(7, Types.VARCHAR);
            }
            ps.executeUpdate();
        }
    }

    public static List<AppNotification> findForUser(long userId, boolean includeArchived) throws SQLException {
        String sql = includeArchived
            ? """
                SELECT id, user_id, category, title, body, created_at, read_at, archived_at, priority
                FROM notifications WHERE user_id = ?
                ORDER BY CASE WHEN read_at IS NULL OR read_at = '' THEN 1 ELSE 0 END DESC,
                         CASE WHEN priority >= 8 THEN 1 ELSE 0 END DESC,
                         created_at DESC,
                         priority DESC
                """
            : """
                SELECT id, user_id, category, title, body, created_at, read_at, archived_at, priority
                FROM notifications WHERE user_id = ? AND (archived_at IS NULL OR archived_at = '')
                ORDER BY CASE WHEN read_at IS NULL OR read_at = '' THEN 1 ELSE 0 END DESC,
                         CASE WHEN priority >= 8 THEN 1 ELSE 0 END DESC,
                         created_at DESC,
                         priority DESC
                """;
        List<AppNotification> list = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public static List<AppNotification> findForUserFiltered(long userId, String categoryFilter,
                                                            String searchText, boolean includeArchived) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT id, user_id, category, title, body, created_at, read_at, archived_at, priority
            FROM notifications WHERE user_id = ?
            """);
        if (!includeArchived) {
            sql.append(" AND (archived_at IS NULL OR archived_at = '') ");
        }
        if (categoryFilter != null && !categoryFilter.isBlank() && !"ALL".equalsIgnoreCase(categoryFilter)) {
            sql.append(" AND category = ? ");
        }
        if (searchText != null && !searchText.isBlank()) {
            sql.append(" AND (title LIKE ? OR body LIKE ?) ");
        }
        sql.append("""
             ORDER BY CASE WHEN read_at IS NULL OR read_at = '' THEN 1 ELSE 0 END DESC,
                      CASE WHEN priority >= 8 THEN 1 ELSE 0 END DESC,
                      created_at DESC,
                      priority DESC
            """);
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int i = 1;
            ps.setLong(i++, userId);
            if (categoryFilter != null && !categoryFilter.isBlank() && !"ALL".equalsIgnoreCase(categoryFilter)) {
                ps.setString(i++, categoryFilter);
            }
            if (searchText != null && !searchText.isBlank()) {
                String p = "%" + searchText.trim().replace("%", "\\%") + "%";
                ps.setString(i++, p);
                ps.setString(i++, p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<AppNotification> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        }
    }

    public static int countUnread(long userId) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM notifications
            WHERE user_id = ? AND (read_at IS NULL OR read_at = '')
            AND (archived_at IS NULL OR archived_at = '')
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public static void markRead(long id, long userId, String readAt) throws SQLException {
        String sql = "UPDATE notifications SET read_at = ? WHERE id = ? AND user_id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, readAt);
            ps.setLong(2, id);
            ps.setLong(3, userId);
            ps.executeUpdate();
        }
    }

    /**
     * Marks all unread, non-archived notifications as read for a user.
     *
     * @return number of rows updated
     */
    public static int markAllRead(long userId, String readAt) throws SQLException {
        String sql = """
            UPDATE notifications
            SET read_at = ?
            WHERE user_id = ?
              AND (read_at IS NULL OR read_at = '')
              AND (archived_at IS NULL OR archived_at = '')
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, readAt);
            ps.setLong(2, userId);
            return ps.executeUpdate();
        }
    }

    public static void archive(long id, long userId, String archivedAt) throws SQLException {
        String sql = "UPDATE notifications SET archived_at = ? WHERE id = ? AND user_id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, archivedAt);
            ps.setLong(2, id);
            ps.setLong(3, userId);
            ps.executeUpdate();
        }
    }

    public static void unarchive(long id, long userId) throws SQLException {
        String sql = "UPDATE notifications SET archived_at = NULL WHERE id = ? AND user_id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    /** Permanently removes a notification row for the user (student delete). */
    public static void deleteById(long id, long userId) throws SQLException {
        String sql = "DELETE FROM notifications WHERE id = ? AND user_id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    public static Optional<AppNotification> findById(long id, long userId) throws SQLException {
        String sql = """
            SELECT id, user_id, category, title, body, created_at, read_at, archived_at, priority
            FROM notifications WHERE id = ? AND user_id = ?
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    private static AppNotification mapRow(ResultSet rs) throws SQLException {
        return new AppNotification(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("category"),
            rs.getString("title"),
            rs.getString("body"),
            rs.getString("created_at"),
            rs.getString("read_at"),
            rs.getString("archived_at"),
            rs.getInt("priority")
        );
    }
}
