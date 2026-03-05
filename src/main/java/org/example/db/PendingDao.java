package org.example.db;

import org.example.domain.PendingBook;
import org.example.domain.SubmissionStatus;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public final class PendingDao {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private PendingDao() {}

    /**
     * Creates the pending_books table if it doesn't exist
     */
    public static void createTable() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS pending_books (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                author_user_id INTEGER NOT NULL,
                author_full_name TEXT NOT NULL,
                genre TEXT NOT NULL,
                summary TEXT NOT NULL,
                file_name TEXT NOT NULL,
                file_path TEXT NOT NULL,
                file_size INTEGER NOT NULL,
                file_type TEXT NOT NULL,
                submitted_date TEXT NOT NULL,
                status TEXT NOT NULL,
                review_notes TEXT,
                reviewed_date TEXT
            )
            """;

        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * Inserts a new pending book request
     */
    public static long insert(PendingBook book) throws SQLException {
        String sql = """
            INSERT INTO pending_books (
                title, author_user_id, author_full_name, genre, summary,
                file_name, file_path, file_size, file_type, submitted_date, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, book.getTitle());
            ps.setLong(2, book.getAuthorUserId());
            ps.setString(3, book.getAuthorFullName());
            ps.setString(4, book.getGenre());
            ps.setString(5, book.getSummary());
            ps.setString(6, book.getFileName());
            ps.setString(7, book.getFilePath());
            ps.setLong(8, book.getFileSize());
            ps.setString(9, book.getFileType());
            ps.setString(10, LocalDateTime.now().format(DATE_FORMATTER));
            ps.setString(11, "PENDING");

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    book.setId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Insert pending book failed, no ID returned");
    }

    /**
     * Find all pending books (for librarian view)
     */
    public static List<PendingBook> findAllPending() throws SQLException {
        String sql = "SELECT * FROM pending_books WHERE status = 'PENDING' ORDER BY submitted_date DESC";
        return findBooksBySql(sql);
    }

    /**
     * Find a specific pending book by ID
     */
    public static Optional<PendingBook> findById(long id) throws SQLException {
        String sql = "SELECT * FROM pending_books WHERE id = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

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
     * Helper method to execute SQL queries and map results
     */
    private static List<PendingBook> findBooksBySql(String sql) throws SQLException {
        List<PendingBook> books = new ArrayList<>();

        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                books.add(mapRow(rs));
            }
        }
        return books;
    }

    /**
     * Map ResultSet row to PendingBook object
     */
    private static PendingBook mapRow(ResultSet rs) throws SQLException {
        PendingBook book = new PendingBook();
        book.setId(rs.getLong("id"));
        book.setTitle(rs.getString("title"));
        book.setAuthorUserId(rs.getLong("author_user_id"));
        book.setAuthorFullName(rs.getString("author_full_name"));
        book.setGenre(rs.getString("genre"));
        book.setSummary(rs.getString("summary"));
        book.setFileName(rs.getString("file_name"));
        book.setFilePath(rs.getString("file_path"));
        book.setFileSize(rs.getLong("file_size"));
        book.setFileType(rs.getString("file_type"));
        book.setStatus(rs.getString("status"));

        String submittedDate = rs.getString("submitted_date");
        if (submittedDate != null) {
            book.setSubmittedDate(LocalDateTime.parse(submittedDate, DATE_FORMATTER));
        }

        String reviewedDate = rs.getString("reviewed_date");
        if (reviewedDate != null) {
            book.setReviewedDate(LocalDateTime.parse(reviewedDate, DATE_FORMATTER));
        }

        book.setReviewNotes(rs.getString("review_notes"));

        return book;
    }
}