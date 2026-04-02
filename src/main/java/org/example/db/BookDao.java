package org.example.db;

import org.example.domain.Availability;
import org.example.domain.Book;
import org.example.security.CryptoUtil;

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
        return insert(title, authorUserId, authorFullNameSnapshot, genre, summary, filePath, publishDate, null);
    }

    public static long insert(String title, long authorUserId, String authorFullNameSnapshot,
                             String genre, String summary, String filePath, String publishDate,
                             String coverImagePath) throws SQLException {
        String sql = """
            INSERT INTO books (title, author_user_id, author_full_name_snapshot, genre, summary, file_path, publish_date, availability, cover_image_path)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'AVAILABLE', ?)
            """;
        String encryptedFilePath = CryptoUtil.encryptToString(filePath);
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setLong(2, authorUserId);
            ps.setString(3, authorFullNameSnapshot);
            ps.setString(4, genre);
            ps.setString(5, summary);
            ps.setString(6, encryptedFilePath);
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
            LEFT JOIN (
                SELECT book_id, COUNT(*) AS cnt FROM borrows GROUP BY book_id
            ) pop ON pop.book_id = b.id
            WHERE b.availability = 'AVAILABLE'
            ORDER BY COALESCE(pop.cnt, 0) DESC, b.title COLLATE NOCASE ASC
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

    /**
     * Recommendations tailored to the user:
     * - Uses the genres the user has previously borrowed (borrowing history).
     * - Falls back to global popularity if the user has no borrowing history.
     * - Excludes books the user has already borrowed.
     */
    public static List<Book> findRecommendedForUserAvailable(long borrowerUserId, int limit) throws SQLException {
        // Borrowing history genres
        List<String> userGenres = new ArrayList<>();
        String genresSql = """
            SELECT DISTINCT k.genre
            FROM borrows b
            JOIN books k ON k.id = b.book_id
            WHERE b.borrower_user_id = ?
              AND k.genre IS NOT NULL
              AND TRIM(k.genre) != ''
            """;
        try (PreparedStatement ps = Database.getConnection().prepareStatement(genresSql)) {
            ps.setLong(1, borrowerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    userGenres.add(rs.getString("genre"));
                }
            }
        }
        if (userGenres.isEmpty()) {
            return findRecommendedAvailable(limit);
        }

        // Exclude books the user has already borrowed.
        List<Long> borrowedIds = new ArrayList<>();
        String borrowedSql = "SELECT DISTINCT book_id FROM borrows WHERE borrower_user_id = ?";
        try (PreparedStatement ps = Database.getConnection().prepareStatement(borrowedSql)) {
            ps.setLong(1, borrowerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    borrowedIds.add(rs.getLong("book_id"));
                }
            }
        }

        // Keep the IN clause manageable.
        int maxGenres = 8;
        List<String> trimmedGenres = userGenres.subList(0, Math.min(maxGenres, userGenres.size()));

        StringBuilder in = new StringBuilder();
        for (int i = 0; i < trimmedGenres.size(); i++) {
            if (i > 0) in.append(',');
            in.append('?');
        }

        // Popularity within the user's preferred genres.
        String sql = """
            SELECT b.id, b.title, b.author_user_id, b.author_full_name_snapshot, b.genre, b.summary,
                   b.file_path, b.publish_date, b.availability, b.cover_image_path
            FROM books b
            LEFT JOIN (
                SELECT book_id, COUNT(*) AS cnt FROM borrows GROUP BY book_id
            ) pop ON pop.book_id = b.id
            WHERE b.availability = 'AVAILABLE'
              AND b.genre IN (%s)
              AND b.id NOT IN (
                SELECT book_id FROM borrows WHERE borrower_user_id = ?
              )
            ORDER BY COALESCE(pop.cnt, 0) DESC, b.title COLLATE NOCASE ASC
            LIMIT ?
            """.formatted(in);

        List<Book> primary = new ArrayList<>();
        try (PreparedStatement ps = Database.getConnection().prepareStatement(sql)) {
            int i = 1;
            for (String g : trimmedGenres) {
                ps.setString(i++, g);
            }
            ps.setLong(i++, borrowerUserId);
            ps.setInt(i, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    primary.add(mapRow(rs));
                }
            }
        }

        if (primary.size() >= limit) {
            return primary;
        }

        // Fill remaining slots with global popularity, excluding already suggested books.
        java.util.Set<Long> already = new java.util.HashSet<>();
        for (Book b : primary) already.add(b.getId());
        for (Long id : borrowedIds) already.add(id);

        List<Book> fill = findRecommendedAvailable(Math.max(limit * 2, limit));
        List<Book> out = new ArrayList<>(primary);
        for (Book b : fill) {
            if (out.size() >= limit) break;
            if (!already.contains(b.getId())) {
                out.add(b);
                already.add(b.getId());
            }
        }
        // Last-resort fallback: if the exclusion filters removed every candidate but
        // the library still has available books, show the most popular globally.
        if (out.isEmpty()) {
            return findRecommendedAvailable(limit);
        }
        return out;
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
        String encryptedFilePath = CryptoUtil.encryptToString(filePath);
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, genre);
            ps.setString(3, summary);
            ps.setString(4, encryptedFilePath);
            if (coverImagePath != null) {
                ps.setString(5, coverImagePath);
            } else {
                ps.setNull(5, Types.VARCHAR);
            }
            ps.setLong(6, id);
            ps.executeUpdate();
        }
    }

    public static void deleteById(long id) throws SQLException {
        String sql = "DELETE FROM books WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
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
            CryptoUtil.decryptToString(rs.getString("file_path")),
            rs.getString("publish_date"),
            Availability.valueOf(rs.getString("availability")),
            rs.getString("cover_image_path")
        );
    }
}
