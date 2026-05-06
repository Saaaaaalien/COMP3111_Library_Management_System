package org.example.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.example.domain.BookChangeLog;

/**
 * Data access for book_change_log table (version history).
 */
public final class BookChangeLogDao {

    private BookChangeLogDao() {}

    /**
     * Records a change to a book's details for audit trail and version history.
     */
    public static void recordChange(long bookId, long librarianUserId, String librarianName,
                                    String changeType, String fieldName, String oldValue, 
                                    String newValue, String description) throws SQLException {
        String sql = """
            INSERT INTO book_change_log (book_id, librarian_user_id, librarian_name, change_type, 
                                          field_name, old_value, new_value, change_date, description)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            ps.setLong(2, librarianUserId);
            ps.setString(3, librarianName);
            ps.setString(4, changeType);
            ps.setString(5, fieldName);
            ps.setString(6, oldValue);
            ps.setString(7, newValue);
            // Store timestamp as string in consistent format: yyyy-MM-dd HH:mm:ss.SSSSSS
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"));
            ps.setString(8, timestamp);
            ps.setString(9, description);
            ps.executeUpdate();
        }
    }

    /**
     * Retrieves all change logs for a specific book, ordered by most recent first.
     */
    public static List<BookChangeLog> findByBookId(long bookId) throws SQLException {
        String sql = """
            SELECT id, book_id, librarian_user_id, librarian_name, change_type, field_name, 
                   old_value, new_value, change_date, description
            FROM book_change_log
            WHERE book_id = ?
            ORDER BY change_date DESC
            """;
        List<BookChangeLog> list = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Retrieves all change logs of a specific type (e.g., "BULK_EDIT", "DELETE").
     */
    public static List<BookChangeLog> findByChangeType(String changeType) throws SQLException {
        String sql = """
            SELECT id, book_id, librarian_user_id, librarian_name, change_type, field_name, 
                   old_value, new_value, change_date, description
            FROM book_change_log
            WHERE change_type = ?
            ORDER BY change_date DESC
            """;
        List<BookChangeLog> list = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, changeType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Retrieves all changes made by a specific librarian.
     */
    public static List<BookChangeLog> findByLibrarian(long librarianUserId) throws SQLException {
        String sql = """
            SELECT id, book_id, librarian_user_id, librarian_name, change_type, field_name, 
                   old_value, new_value, change_date, description
            FROM book_change_log
            WHERE librarian_user_id = ?
            ORDER BY change_date DESC
            """;
        List<BookChangeLog> list = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, librarianUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Deletes all change logs for a specific book (useful for cleanup).
     */
    public static void deleteByBookId(long bookId) throws SQLException {
        String sql = "DELETE FROM book_change_log WHERE book_id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            ps.executeUpdate();
        }
    }

    private static BookChangeLog mapRow(ResultSet rs) throws SQLException {
        // Read change_date as String and parse manually since it's stored as TEXT
        LocalDateTime changeDate;
        String dateStr = rs.getString("change_date");
        if (dateStr != null && !dateStr.trim().isEmpty()) {
            try {
                // Try parsing standard SQL format (yyyy-MM-dd HH:mm:ss.nnnnnnnnn)
                // SQLite's Timestamp.valueOf produces format like: 2025-01-15 14:30:45.123456
                changeDate = LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"));
            } catch (Exception e1) {
                try {
                    // Fallback: try without fractional seconds
                    changeDate = LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                } catch (Exception e2) {
                    // If still fails, use current time as fallback
                    changeDate = LocalDateTime.now();
                }
            }
        } else {
            changeDate = LocalDateTime.now();
        }
        
        return new BookChangeLog(
            rs.getLong("id"),
            rs.getLong("book_id"),
            rs.getLong("librarian_user_id"),
            rs.getString("librarian_name"),
            rs.getString("change_type"),
            rs.getString("field_name"),
            rs.getString("old_value"),
            rs.getString("new_value"),
            changeDate,
            rs.getString("description")
        );
    }
}
