package org.example.db;

import org.example.domain.Availability;
import org.example.domain.Book;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        return insert(title, authorUserId, authorFullNameSnapshot, genre, summary, filePath, publishDate, null);
    }

    public static long insert(String title, long authorUserId, String authorFullNameSnapshot,
                             String genre, String summary, String filePath, String publishDate,
                             String coverImagePath) throws SQLException {
        String sql = """
            INSERT INTO books (title, author_user_id, author_full_name_snapshot, genre, summary, file_path, publish_date, availability, cover_image_path)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'AVAILABLE', ?)
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
            if (coverImagePath != null) {
                ps.setString(8, coverImagePath);
            } else {
                ps.setNull(8, Types.VARCHAR);
            }
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
            SELECT id, title, author_user_id, author_full_name_snapshot, genre, summary, file_path, publish_date, availability, cover_image_path
            FROM books WHERE availability = 'AVAILABLE' AND is_visible = 1 ORDER BY publish_date DESC
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
            SELECT id, title, author_user_id, author_full_name_snapshot, genre, summary, file_path, publish_date, availability, cover_image_path
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

    /**
     * All catalog books (librarian maintenance).
     */
    public static List<Book> findAll() throws SQLException {
        String sql = """
            SELECT id, title, author_user_id, author_full_name_snapshot, genre, summary, file_path, publish_date, availability, cover_image_path
            FROM books ORDER BY title COLLATE NOCASE
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
     * Available books most often borrowed (popularity), for recommendations.
     */
    public static List<Book> findRecommendedAvailable(int limit) throws SQLException {
        String sql = """
            SELECT b.id, b.title, b.author_user_id, b.author_full_name_snapshot, b.genre, b.summary,
                   b.file_path, b.publish_date, b.availability, b.cover_image_path
            FROM books b
            INNER JOIN (
                SELECT book_id, COUNT(*) AS cnt FROM borrows GROUP BY book_id
            ) pop ON pop.book_id = b.id
            WHERE b.availability = 'AVAILABLE' AND b.is_visible = 1
            ORDER BY pop.cnt DESC
            LIMIT ?
            """;
        List<Book> list = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public static List<Book> findByAuthorUserId(long authorUserId) throws SQLException {
        String sql = """
            SELECT id, title, author_user_id, author_full_name_snapshot, genre, summary, file_path, publish_date, availability, cover_image_path
            FROM books WHERE author_user_id = ? ORDER BY publish_date DESC
            """;
        List<Book> list = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, authorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public static Set<Long> findHiddenBookIdsByAuthor(long authorUserId) throws SQLException {
        String sql = "SELECT id FROM books WHERE author_user_id = ? AND is_visible = 0";
        Set<Long> ids = new HashSet<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, authorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong("id"));
                }
            }
        }
        return ids;
    }

    public static void updateAuthorMetadata(long bookId, long authorUserId, String authorFullName,
                                           String title, String genre, String summary) throws SQLException {
        String sql = """
            UPDATE books SET title = ?, genre = ?, summary = ?, author_full_name_snapshot = ?
            WHERE id = ? AND author_user_id = ?
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, genre);
            ps.setString(3, summary);
            ps.setString(4, authorFullName);
            ps.setLong(5, bookId);
            ps.setLong(6, authorUserId);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Book not found or not owned by author.");
            }
        }
    }

    public static void updatePublishedFields(long id, String title, String genre, String summary,
                                            String filePath, String coverImagePath) throws SQLException {
        String sql = """
            UPDATE books SET title = ?, genre = ?, summary = ?, file_path = ?, cover_image_path = ?
            WHERE id = ?
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, genre);
            ps.setString(3, summary);
            ps.setString(4, filePath);
            if (coverImagePath != null) {
                ps.setString(5, coverImagePath);
            } else {
                ps.setNull(5, Types.VARCHAR);
            }
            ps.setLong(6, id);
            ps.executeUpdate();
        }
    }

    /**
     * Updates a published book and any legacy duplicate rows that represent the same logical book.
     * Matching strategy:
     * - always update the selected id
     * - also update rows with same author_user_id + file_path as the selected id
     */
    public static void updatePublishedFieldsIncludingLinked(long id, String title, String genre, String summary,
                                                            String filePath, String coverImagePath) throws SQLException {
        String sql = """
            UPDATE books
            SET title = ?, genre = ?, summary = ?, file_path = ?, cover_image_path = ?
            WHERE id = ?
               OR (
                   author_user_id = (SELECT author_user_id FROM books WHERE id = ?)
                   AND file_path = (SELECT file_path FROM books WHERE id = ?)
               )
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, genre);
            ps.setString(3, summary);
            ps.setString(4, filePath);
            if (coverImagePath != null) {
                ps.setString(5, coverImagePath);
            } else {
                ps.setNull(5, Types.VARCHAR);
            }
            ps.setLong(6, id);
            ps.setLong(7, id);
            ps.setLong(8, id);
            int n = ps.executeUpdate();
            if (n == 0) {
                throw new SQLException("Book not found.");
            }
        }
    }

    public static void deleteById(long id) throws SQLException {
        BorrowDao.deleteAllBorrowsForBook(id);
        String sql = "DELETE FROM books WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public static void hideFromCatalog(long id) throws SQLException {
        String sql = "UPDATE books SET is_visible = 0 WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Book not found.");
            }
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
            Availability.valueOf(rs.getString("availability")),
            rs.getString("cover_image_path")
        );
    }
}
