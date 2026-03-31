package org.example.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.domain.PendingBook;


/**
 * Data access for pending_books (submissions awaiting librarian approval).
 * <p>
 * Uses {@link Database#getConnection()}, which returns the application's single shared
 * connection. That connection must not be closed here; only statements and result sets
 * are closed. Closing the shared connection would break all subsequent database
 * operations (e.g. "database connection closed" when approving books).
 */
public final class PendingDao {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private PendingDao() {}

    /**
     * Creates the pending_books table if it doesn't exist
     */
    public static void createTable() throws SQLException {
        //noinspection SqlNoDataSourceInspection
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
                reviewed_date TEXT,
                cover_path TEXT
            )
            """;

        Connection conn = Database.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            try {
                stmt.executeUpdate("ALTER TABLE pending_books ADD COLUMN cover_path TEXT");
            } catch (SQLException e) {
                String msg = e.getMessage();
                if (msg == null || !msg.contains("duplicate column")) {
                    throw e;
                }
            }
        }
    }

    /**
     * Inserts a new pending book request
     */
    public static long insert(PendingBook book) throws SQLException {
        String sql = """
            INSERT INTO pending_books (
                title, author_user_id, author_full_name, genre, summary,
                file_name, file_path, file_size, file_type, submitted_date, status, cover_path
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

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
            if (book.getCoverPath() != null && !book.getCoverPath().isEmpty()) {
                ps.setString(12, book.getCoverPath());
            } else {
                ps.setNull(12, Types.VARCHAR);
            }

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
     * Find all books regardless of status (PENDING, APPROVED, REJECTED)
     */
    public static List<PendingBook> findAll() throws SQLException {
        String sql = "SELECT * FROM pending_books ORDER BY submitted_date DESC";
        return findBooksBySql(sql);
    }

    /**
     * Find a specific pending book by ID
     */
    public static Optional<PendingBook> findById(long id) throws SQLException {
        String sql = "SELECT * FROM pending_books WHERE id = ?";

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
     * Approve a pending book submission.
     * Updates pending_books status and inserts the book into the books table
     * so it appears as available for students to borrow.
     * Both operations run in a single transaction; if the catalog insert fails,
     * the status update is rolled back so the database stays consistent.
     */
    public static void approvePendingBook(long bookId, String reviewNotes) throws SQLException {
        Connection conn = Database.getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            String updateSql = """
                UPDATE pending_books
                SET status = 'APPROVED', reviewed_date = ?, review_notes = ?
                WHERE id = ?
                """;
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, LocalDateTime.now().format(DATE_FORMATTER));
                ps.setString(2, reviewNotes != null ? reviewNotes : "");
                ps.setLong(3, bookId);
                ps.executeUpdate();
            }

            // Load the row we just updated (still in same transaction) and add to catalog
            Optional<PendingBook> pending = findById(bookId);
            if (pending.isPresent()) {
                PendingBook p = pending.get();
                String publishDate = Instant.now().toString();

                // If the uploaded file is not PDF, convert it to PDF so the reader can open it later.
                String publishFilePath = p.getFilePath();
                try {
                    String type = p.getFileType() != null ? p.getFileType().toLowerCase() : "";
                    if (!"pdf".equals(type)) {
                        // Convert to PDF and update the pending row within the same transaction
                        try {
                            java.nio.file.Path converted = org.example.util.FileToPdfConverter.convertToPdf(java.nio.file.Paths.get(p.getFilePath()));
                            publishFilePath = converted.toAbsolutePath().toString();

                            String updateFileSql = "UPDATE pending_books SET file_path = ?, file_name = ?, file_size = ?, file_type = ? WHERE id = ?";
                            try (PreparedStatement ps2 = conn.prepareStatement(updateFileSql)) {
                                ps2.setString(1, publishFilePath);
                                ps2.setString(2, converted.getFileName().toString());
                                ps2.setLong(3, java.nio.file.Files.size(converted));
                                ps2.setString(4, "pdf");
                                ps2.setLong(5, bookId);
                                ps2.executeUpdate();
                            }
                        } catch (Exception e) {
                            throw new SQLException("Failed to convert uploaded file to PDF: " + e.getMessage(), e);
                        }
                    }
                } catch (SQLException e) {
                    // If conversion/update failed, rollback will happen in caller
                    throw e;
                }

                BookDao.insert(
                    p.getTitle(),
                    p.getAuthorUserId(),
                    p.getAuthorFullName(),
                    p.getGenre(),
                    p.getSummary() != null ? p.getSummary() : "",
                    publishFilePath,
                    publishDate,
                    p.getCoverPath()
                );
            }

            conn.commit();
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
            }
            throw e;
        } finally {
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException ignored) {
            }
        }
    }

    /**
     * Reject a pending book submission
     */
    public static void rejectPendingBook(long bookId, String reviewNotes, String rejectionReason) throws SQLException {
        String sql = """
            UPDATE pending_books
            SET status = 'REJECTED', reviewed_date = ?, review_notes = ?, rejection_reason = ?
            WHERE id = ?
            """;

        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, LocalDateTime.now().format(DATE_FORMATTER));
            ps.setString(2, reviewNotes != null ? reviewNotes : "");
            ps.setString(3, rejectionReason != null ? rejectionReason : "");
            ps.setLong(4, bookId);

            ps.executeUpdate();
        }
    }

    /**
     * Search pending books by title, author, genre, or submitted date
     */
    public static List<PendingBook> searchBooks(String searchTerm) throws SQLException {
        String sql = """
            SELECT * FROM pending_books 
            WHERE (title LIKE ? OR author_full_name LIKE ? OR genre LIKE ?)
            ORDER BY submitted_date DESC
            """;

        List<PendingBook> books = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String likePattern = "%" + searchTerm + "%";
            ps.setString(1, likePattern);
            ps.setString(2, likePattern);
            ps.setString(3, likePattern);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    books.add(mapRow(rs));
                }
            }
        }
        return books;
    }

    /**
     * Filter books by status (PENDING, APPROVED, REJECTED)
     */
    public static List<PendingBook> filterByStatus(String status) throws SQLException {
        String sql = "SELECT * FROM pending_books WHERE status = ? ORDER BY submitted_date DESC";

        List<PendingBook> books = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    books.add(mapRow(rs));
                }
            }
        }
        return books;
    }

    /**
     * Search and filter combined
     */
    public static List<PendingBook> searchAndFilter(String searchTerm, String status) throws SQLException {
        String sql;
        if (searchTerm == null || searchTerm.isEmpty()) {
            if (status == null || status.isEmpty()) {
                return findAllPending();
            }
            return filterByStatus(status);
        } else {
            if (status == null || status.isEmpty()) {
                return searchBooks(searchTerm);
            }
        }

        sql = """
            SELECT * FROM pending_books 
            WHERE (title LIKE ? OR author_full_name LIKE ? OR genre LIKE ?)
            AND status = ?
            ORDER BY submitted_date DESC
            """;

        List<PendingBook> books = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String likePattern = "%" + searchTerm + "%";
            ps.setString(1, likePattern);
            ps.setString(2, likePattern);
            ps.setString(3, likePattern);
            ps.setString(4, status);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    books.add(mapRow(rs));
                }
            }
        }
        return books;
    }


    /**
     * Helper method to execute SQL queries and map results
     */
    private static List<PendingBook> findBooksBySql(String sql) throws SQLException {
        List<PendingBook> books = new ArrayList<>();

        Connection conn = Database.getConnection();
        try (Statement stmt = conn.createStatement();
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
        book.setRejectionReason(rs.getString("rejection_reason"));

        try {
            book.setCoverPath(rs.getString("cover_path"));
        } catch (SQLException ignored) {
        }

        return book;
    }

    public static List<PendingBook> findAllByAuthorUserId(long authorUserId) throws SQLException {
        String sql = "SELECT * FROM pending_books WHERE author_user_id = ? ORDER BY submitted_date DESC";
        Connection conn = Database.getConnection();
        List<PendingBook> books = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, authorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    books.add(mapRow(rs));
                }
            }
        }
        return books;
    }

    public static void updatePendingSubmission(long id, long authorUserId, String title, String genre, String summary,
                                              String fileName, String filePath, long fileSize, String fileType,
                                              String coverPath) throws SQLException {
        String sql = """
            UPDATE pending_books SET title = ?, genre = ?, summary = ?, file_name = ?, file_path = ?,
            file_size = ?, file_type = ?, cover_path = ?
            WHERE id = ? AND author_user_id = ? AND status = 'PENDING'
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, genre);
            ps.setString(3, summary);
            ps.setString(4, fileName);
            ps.setString(5, filePath);
            ps.setLong(6, fileSize);
            ps.setString(7, fileType);
            if (coverPath != null) {
                ps.setString(8, coverPath);
            } else {
                ps.setNull(8, Types.VARCHAR);
            }
            ps.setLong(9, id);
            ps.setLong(10, authorUserId);
            int n = ps.executeUpdate();
            if (n == 0) {
                throw new SQLException("No pending row updated (wrong author or not PENDING).");
            }
        }
    }

    public static void deletePending(long id, long authorUserId) throws SQLException {
        String sql = "DELETE FROM pending_books WHERE id = ? AND author_user_id = ? AND status = 'PENDING'";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, authorUserId);
            int n = ps.executeUpdate();
            if (n == 0) {
                throw new SQLException("Could not delete (not pending or wrong author).");
            }
        }
    }
}