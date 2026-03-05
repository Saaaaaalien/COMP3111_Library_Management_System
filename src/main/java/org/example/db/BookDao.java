package org.example.db;

import org.example.domain.Availability;
import org.example.domain.Book;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access for books table (published books).
 */
public final class BookDao {

    private BookDao() {}

    /**
     * Inserts a new published book. Returns the generated id.
     */
    public static long insert(String title, long authorUserId, String authorFullNameSnapshot,
                             String genre, String summary, String filePath, String publishDate) throws SQLException {
        String sql = """
            INSERT INTO books (title, author_user_id, author_full_name_snapshot, genre, summary, file_path, publish_date, availability)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'AVAILABLE')
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setLong(2, authorUserId);
            ps.setString(3, authorFullNameSnapshot);
            ps.setString(4, genre);
            ps.setString(5, summary);
            ps.setString(6, filePath);
            ps.setString(7, publishDate);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Insert book failed, no id returned");
    }

    /**
     * Finds all books with availability = AVAILABLE.
     */
    public static List<Book> findAllAvailable() throws SQLException {
        String sql = """
            SELECT id, title, author_user_id, author_full_name_snapshot, genre, summary, file_path, publish_date, availability
            FROM books WHERE availability = 'AVAILABLE' ORDER BY publish_date DESC
            """;
        List<Book> list = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /**
     * Finds a book by id.
     */
    public static Optional<Book> findById(long id) throws SQLException {
        String sql = """
            SELECT id, title, author_user_id, author_full_name_snapshot, genre, summary, file_path, publish_date, availability
            FROM books WHERE id = ?
            """;
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

    /**
     * Sets availability for a book (e.g. AVAILABLE or BORROWED).
     */
    public static void updateAvailability(long id, Availability availability) throws SQLException {
        String sql = "UPDATE books SET availability = ? WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, availability.name());
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private static Book mapRow(ResultSet rs) throws SQLException {
        return new Book(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getLong("author_user_id"),
            rs.getString("author_full_name_snapshot"),
            rs.getString("genre"),
            rs.getString("summary"),
            rs.getString("file_path"),
            rs.getString("publish_date"),
            Availability.valueOf(rs.getString("availability"))
        );
    }
}
