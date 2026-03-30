package org.example.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * User highlights / notes tied to a PDF page for a borrow.
 * Text is captured from selection on the extracted-text pane (image-based PDF UX fallback).
 */
public final class ReadingHighlightDao {

    public record HighlightRow(long id, int pageIndex, String highlightText, String createdAt) {}

    private ReadingHighlightDao() {}

    public static long insert(long borrowId, long userId, int pageIndex, String highlightText,
                             String createdAt) throws SQLException {
        String sql = """
            INSERT INTO reading_highlights (borrow_id, user_id, page_index, highlight_text, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, borrowId);
            ps.setLong(2, userId);
            ps.setInt(3, pageIndex);
            ps.setString(4, highlightText);
            ps.setString(5, createdAt);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Insert highlight failed");
    }

    public static List<HighlightRow> findByBorrow(long borrowId) throws SQLException {
        String sql = """
            SELECT id, page_index, highlight_text, created_at
            FROM reading_highlights WHERE borrow_id = ? ORDER BY page_index, id
            """;
        List<HighlightRow> list = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, borrowId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new HighlightRow(
                        rs.getLong("id"),
                        rs.getInt("page_index"),
                        rs.getString("highlight_text"),
                        rs.getString("created_at")
                    ));
                }
            }
        }
        return list;
    }

    public static void delete(long id, long borrowId, long userId) throws SQLException {
        String sql = "DELETE FROM reading_highlights WHERE id = ? AND borrow_id = ? AND user_id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, borrowId);
            ps.setLong(3, userId);
            ps.executeUpdate();
        }
    }

    public static void deleteAllForBorrow(long borrowId) throws SQLException {
        String sql = "DELETE FROM reading_highlights WHERE borrow_id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, borrowId);
            ps.executeUpdate();
        }
    }
}
