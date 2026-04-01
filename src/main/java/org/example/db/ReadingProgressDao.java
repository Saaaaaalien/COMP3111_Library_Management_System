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

    public static void upsert(long borrowId, long userId, long bookId, int lastPage,
                           String viewerPayload, String updatedAt) throws SQLException {
        //noinspection SqlNoDataSourceInspection
        String sql = """
            INSERT INTO reading_progress (borrow_id, user_id, book_id, last_page, viewer_payload, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(borrow_id) DO UPDATE SET
                last_page = excluded.last_page,
                viewer_payload = excluded.viewer_payload,
                updated_at = excluded.updated_at
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
            ps.executeUpdate();
        }
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
