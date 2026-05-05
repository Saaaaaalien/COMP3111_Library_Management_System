package org.example.db;

import java.sql.*;
import java.util.Optional;

/**
 * Bookmark / last page per active borrow.
 */
public final class ReadingProgressDao {

    private ReadingProgressDao() {}

    public static Optional<Integer> getLastPage(long borrowId) throws SQLException {
        //noinspection SqlNoDataSourceInspection
        String sql = "SELECT last_page FROM reading_progress WHERE borrow_id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, borrowId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getInt("last_page"));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Bookmark / progress lookup scoped to both borrow and user.
     * This prevents mismatched borrowId from reading another user's progress.
     */
    public static Optional<Integer> getLastPage(long borrowId, long userId) throws SQLException {
        //noinspection SqlNoDataSourceInspection
        String sql = "SELECT last_page FROM reading_progress WHERE borrow_id = ? AND user_id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, borrowId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getInt("last_page"));
                }
            }
        }
        return Optional.empty();
    }

    public static void upsert(long borrowId, long userId, long bookId, int lastPage,
                           String viewerPayload, String updatedAt) throws SQLException {
        upsert(borrowId, userId, bookId, lastPage, viewerPayload, updatedAt, 0);
    }

    /**
     * Persists last page / payload and adds {@code deltaReadSeconds} to accumulated reading time for this borrow.
     */
    public static void upsert(long borrowId, long userId, long bookId, int lastPage,
                           String viewerPayload, String updatedAt, int deltaReadSeconds) throws SQLException {
        int delta = Math.max(0, deltaReadSeconds);
        //noinspection SqlNoDataSourceInspection
        String sql = """
            INSERT INTO reading_progress (borrow_id, user_id, book_id, last_page, viewer_payload, updated_at, accumulated_read_seconds)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(borrow_id) DO UPDATE SET
                user_id = excluded.user_id,
                book_id = excluded.book_id,
                last_page = excluded.last_page,
                viewer_payload = excluded.viewer_payload,
                updated_at = excluded.updated_at,
                accumulated_read_seconds = COALESCE(reading_progress.accumulated_read_seconds, 0) + excluded.accumulated_read_seconds
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, borrowId);
            ps.setLong(2, userId);
            ps.setLong(3, bookId);
            ps.setInt(4, lastPage);
            if (viewerPayload != null) {
                ps.setString(5, viewerPayload);
            } else {
                ps.setNull(5, Types.VARCHAR);
            }
            ps.setString(6, updatedAt);
            ps.setInt(7, delta);
            ps.executeUpdate();
        }
    }

    public static int getAccumulatedReadSeconds(long borrowId) throws SQLException {
        String sql = "SELECT accumulated_read_seconds FROM reading_progress WHERE borrow_id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, borrowId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Adds open-reader time without changing {@code last_page}. Creates a row if none exists yet.
     */
    public static void addReadSeconds(long borrowId, long userId, long bookId, int lastPage,
                                      int deltaSeconds, String updatedAt) throws SQLException {
        int d = Math.max(0, deltaSeconds);
        if (d == 0) {
            return;
        }
        String upd = """
            UPDATE reading_progress
            SET accumulated_read_seconds = COALESCE(accumulated_read_seconds, 0) + ?,
                updated_at = ?
            WHERE borrow_id = ?
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(upd)) {
            ps.setInt(1, d);
            ps.setString(2, updatedAt);
            ps.setLong(3, borrowId);
            int n = ps.executeUpdate();
            if (n > 0) {
                return;
            }
        }
        upsert(borrowId, userId, bookId, lastPage, null, updatedAt, d);
    }

    public static void deleteForBorrow(long borrowId) throws SQLException {
        String sql = "DELETE FROM reading_progress WHERE borrow_id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, borrowId);
            ps.executeUpdate();
        }
    }
}
