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
     * Find all books by a specific author (including approved/rejected)
     */
    public static List<PendingBook> findByAuthor(long authorUserId) throws SQLException {
        String sql = "SELECT * FROM pending_books WHERE author_user_id = ? ORDER BY submitted_date DESC";

        List<PendingBook> books = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, authorUserId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    books.add(mapRow(rs));
                }
            }
        }
        return books;
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
     * Approve a pending book (moves it to books table and deletes from pending)
     */
    public static boolean approveBook(long pendingBookId) throws SQLException {
        // Start transaction
        Connection conn = Database.getConnection();
        conn.setAutoCommit(false);

        try {
            // Get the pending book
            Optional<PendingBook> optBook = findById(pendingBookId);
            if (!optBook.isPresent()) {
                return false;
            }

            PendingBook pending = optBook.get();

            // Insert into books table (published books)
            String insertSql = """
                INSERT INTO books (
                    title, author_user_id, author_full_name_snapshot, genre, 
                    summary, file_path, publish_date, availability
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'AVAILABLE')
                """;

            try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                insertPs.setString(1, pending.getTitle());
                insertPs.setLong(2, pending.getAuthorUserId());
                insertPs.setString(3, pending.getAuthorFullName());
                insertPs.setString(4, pending.getGenre());
                insertPs.setString(5, pending.getSummary());
                insertPs.setString(6, pending.getFilePath());
                insertPs.setString(7, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
                insertPs.executeUpdate();
            }

            // Update pending book status
            String updateSql = "UPDATE pending_books SET status = 'APPROVED', reviewed_date = ? WHERE id = ?";
            try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                updatePs.setString(1, LocalDateTime.now().format(DATE_FORMATTER));
                updatePs.setLong(2, pendingBookId);
                updatePs.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    /**
     * Reject a pending book
     */
    public static boolean rejectBook(long pendingBookId, String reviewNotes) throws SQLException {
        String sql = "UPDATE pending_books SET status = 'REJECTED', review_notes = ?, reviewed_date = ? WHERE id = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, reviewNotes);
            ps.setString(2, LocalDateTime.now().format(DATE_FORMATTER));
            ps.setLong(3, pendingBookId);

            int affected = ps.executeUpdate();
            return affected > 0;
        }
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