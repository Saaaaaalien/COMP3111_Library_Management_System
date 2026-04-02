package org.example.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * User highlights / notes tied to a PDF page for a borrow.
 * Text is captured from selection on the extracted-text pane (image-based PDF UX fallback).
 */
public final class ReadingHighlightDao {

    public record HighlightRow(long id,
                                 int pageIndex,
                                 String highlightText,
                                 String highlightRectsJson,
                                 String createdAt) {}

    private ReadingHighlightDao() {}

    public static long insert(long borrowId,
                               long userId,
                               int pageIndex,
                               String highlightText,
                               String highlightRectsJson,
                               String createdAt) throws SQLException {
        // New schema: includes highlight_rects_json
        String sqlNew = """
            INSERT INTO reading_highlights (borrow_id, user_id, page_index, highlight_text, highlight_rects_json, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sqlNew, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, borrowId);
            ps.setLong(2, userId);
            ps.setInt(3, pageIndex);
            ps.setString(4, highlightText);
            if (highlightRectsJson != null && !highlightRectsJson.isBlank()) {
                ps.setString(5, highlightRectsJson);
            } else {
                ps.setNull(5, Types.VARCHAR);
            }
            ps.setString(6, createdAt);
            int updated = ps.executeUpdate();
            if (updated <= 0) {
                throw new SQLException("Insert highlight affected 0 rows");
            }
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            // Some JDBC/driver combos (including SQLite) may not return generated keys.
            // If the INSERT succeeded (updated > 0), the rowid is still available.
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            // Fallback for older DBs without highlight_rects_json column.
            // This keeps highlights from silently disappearing.
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("highlight_rects_json")) {
                String sqlOld = """
                    INSERT INTO reading_highlights (borrow_id, user_id, page_index, highlight_text, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """;
                try (PreparedStatement ps = conn.prepareStatement(sqlOld, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, borrowId);
                    ps.setLong(2, userId);
                    ps.setInt(3, pageIndex);
                    ps.setString(4, highlightText);
                    ps.setString(5, createdAt);
                    int updated = ps.executeUpdate();
                    if (updated <= 0) {
                        throw new SQLException("Insert highlight affected 0 rows");
                    }
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            return rs.getLong(1);
                        }
                    }
                    try (Statement st = conn.createStatement();
                         ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                        if (rs.next()) {
                            return rs.getLong(1);
                        }
                    }
                }
            }
            throw e;
        }
        // If we reach here, the INSERT likely succeeded but the driver didn't provide
        // a reliable id back. Treat it as success to avoid misleading failures.
        return -1;
    }

    /**
     * Backwards-compatible insert for older callers (no stored geometry).
     */
    public static long insert(long borrowId, long userId, int pageIndex, String highlightText, String createdAt) throws SQLException {
        return insert(borrowId, userId, pageIndex, highlightText, null, createdAt);
    }

    public static List<HighlightRow> findByBorrow(long borrowId) throws SQLException {
        String sql = """
            SELECT id, page_index, highlight_text, highlight_rects_json, created_at
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
                            rs.getString("highlight_rects_json"),
                            rs.getString("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("highlight_rects_json")) {
                // Older DB: fallback to query without highlight_rects_json.
                String sqlOld = """
                    SELECT id, page_index, highlight_text, created_at
                    FROM reading_highlights WHERE borrow_id = ? ORDER BY page_index, id
                    """;
                try (PreparedStatement ps = conn.prepareStatement(sqlOld)) {
                    ps.setLong(1, borrowId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            list.add(new HighlightRow(
                                    rs.getLong("id"),
                                    rs.getInt("page_index"),
                                    rs.getString("highlight_text"),
                                    null,
                                    rs.getString("created_at")
                            ));
                        }
                    }
                }
                return list;
            }
            throw e;
        }
        return list;
    }

    /**
     * Highlights lookup scoped to both borrow and user.
     * This prevents a mismatched borrowId from exposing another user's highlights.
     */
    public static List<HighlightRow> findByBorrow(long borrowId, long userId) throws SQLException {
        String sql = """
            SELECT id, page_index, highlight_text, highlight_rects_json, created_at
            FROM reading_highlights
            WHERE borrow_id = ? AND user_id = ?
            ORDER BY page_index, id
            """;
        List<HighlightRow> list = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, borrowId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new HighlightRow(
                            rs.getLong("id"),
                            rs.getInt("page_index"),
                            rs.getString("highlight_text"),
                            rs.getString("highlight_rects_json"),
                            rs.getString("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("highlight_rects_json")) {
                String sqlOld = """
                    SELECT id, page_index, highlight_text, created_at
                    FROM reading_highlights
                    WHERE borrow_id = ? AND user_id = ?
                    ORDER BY page_index, id
                    """;
                try (PreparedStatement ps = conn.prepareStatement(sqlOld)) {
                    ps.setLong(1, borrowId);
                    ps.setLong(2, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            list.add(new HighlightRow(
                                    rs.getLong("id"),
                                    rs.getInt("page_index"),
                                    rs.getString("highlight_text"),
                                    null,
                                    rs.getString("created_at")
                            ));
                        }
                    }
                }
                return list;
            }
            throw e;
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
