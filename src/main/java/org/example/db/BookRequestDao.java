package org.example.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.domain.BookRequest;
import org.example.domain.BookRequest.RequestStatus;

/**
 * Data access for book_requests (student/staff requests for new books).
 * <p>
 * Uses {@link Database#getConnection()}, which returns the application's single shared
 * connection. That connection must not be closed here; only statements and result sets
 * are closed.
 */
public final class BookRequestDao {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private BookRequestDao() {}

    /**
     * Creates the book_requests table if it doesn't exist
     */
    public static void createTable() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS book_requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                requested_by_user_id INTEGER NOT NULL,
                requested_by_name TEXT NOT NULL,
                title TEXT NOT NULL,
                author_name TEXT NOT NULL,
                description TEXT,
                genre TEXT,
                status TEXT NOT NULL,
                approval_notes TEXT,
                downloaded_file_path TEXT,
                generated_summary TEXT,
                created_at TEXT NOT NULL,
                processed_at TEXT,
                FOREIGN KEY (requested_by_user_id) REFERENCES users(id)
            )
            """;

        Connection conn = Database.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
     * Inserts a new book request
     */
    public static long insert(BookRequest request) throws SQLException {
        String sql = """
            INSERT INTO book_requests (
                requested_by_user_id, requested_by_name, title, author_name, description,
                genre, status, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, request.getRequestedByUserId());
            ps.setString(2, request.getRequestedByName());
            ps.setString(3, request.getTitle());
            ps.setString(4, request.getAuthorName());
            ps.setString(5, request.getDescription());
            ps.setString(6, request.getGenre());
            ps.setString(7, request.getStatus().name());
            ps.setString(8, LocalDateTime.now().format(DATE_FORMATTER));

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Insert book request failed, no ID returned");
    }

    /**
     * Finds all pending book requests for librarian to review
     */
    public static List<BookRequest> findAllPending() throws SQLException {
        String sql = "SELECT * FROM book_requests WHERE status = ? ORDER BY created_at DESC";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, RequestStatus.PENDING.name());
            return mapResultsToRequests(ps.executeQuery());
        }
    }

    /**
     * Finds all book requests (regardless of status)
     */
    public static List<BookRequest> findAll() throws SQLException {
        String sql = "SELECT * FROM book_requests ORDER BY created_at DESC";
        Connection conn = Database.getConnection();
        try (Statement stmt = conn.createStatement()) {
            return mapResultsToRequests(stmt.executeQuery(sql));
        }
    }

    /**
     * Finds a specific book request by ID
     */
    public static Optional<BookRequest> findById(long id) throws SQLException {
        String sql = "SELECT * FROM book_requests WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToRequest(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds requests by status
     */
    public static List<BookRequest> findByStatus(RequestStatus status) throws SQLException {
        String sql = "SELECT * FROM book_requests WHERE status = ? ORDER BY created_at DESC";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            return mapResultsToRequests(ps.executeQuery());
        }
    }

    /**
     * Finds requests by requester user ID
     */
    public static List<BookRequest> findByRequestedByUserId(long userId) throws SQLException {
        String sql = "SELECT * FROM book_requests WHERE requested_by_user_id = ? ORDER BY created_at DESC";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            return mapResultsToRequests(ps.executeQuery());
        }
    }

    /**
     * Searches for book requests by title, author, or genre
     */
    public static List<BookRequest> search(String query) throws SQLException {
        String sql = """
            SELECT * FROM book_requests 
            WHERE title LIKE ? OR author_name LIKE ? OR genre LIKE ? 
            ORDER BY created_at DESC
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + query + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            return mapResultsToRequests(ps.executeQuery());
        }
    }

    /**
     * Updates a book request (typically to change status or add approval notes)
     */
    public static void update(BookRequest request) throws SQLException {
        String sql = """
            UPDATE book_requests 
            SET status = ?, approval_notes = ?, downloaded_file_path = ?, 
                generated_summary = ?, processed_at = ?
            WHERE id = ?
            """;

        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, request.getStatus().name());
            ps.setString(2, request.getApprovalNotes());
            ps.setString(3, request.getDownloadedFilePath());
            ps.setString(4, request.getGeneratedSummary());
            
            if (request.getProcessedAt() != null) {
                ps.setString(5, request.getProcessedAt());
            } else {
                ps.setNull(5, java.sql.Types.VARCHAR);
            }
            
            ps.setLong(6, request.getId());
            ps.executeUpdate();
        }
    }

    /**
     * Approves a request
     */
    public static void approve(long requestId, String notes) throws SQLException {
        String sql = """
            UPDATE book_requests 
            SET status = ?, approval_notes = ?, processed_at = ?
            WHERE id = ?
            """;

        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, RequestStatus.APPROVED.name());
            ps.setString(2, notes);
            ps.setString(3, LocalDateTime.now().format(DATE_FORMATTER));
            ps.setLong(4, requestId);
            ps.executeUpdate();
        }
    }

    /**
     * Rejects a request
     */
    public static void reject(long requestId, String reason) throws SQLException {
        String sql = """
            UPDATE book_requests 
            SET status = ?, approval_notes = ?, processed_at = ?
            WHERE id = ?
            """;

        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, RequestStatus.REJECTED.name());
            ps.setString(2, reason);
            ps.setString(3, LocalDateTime.now().format(DATE_FORMATTER));
            ps.setLong(4, requestId);
            ps.executeUpdate();
        }
    }

    /**
     * Marks a request as processed
     */
    public static void markAsProcessed(long requestId, String downloadPath, String summary) throws SQLException {
        String sql = """
            UPDATE book_requests 
            SET status = ?, downloaded_file_path = ?, generated_summary = ?, processed_at = ?
            WHERE id = ?
            """;

        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, RequestStatus.PROCESSED.name());
            ps.setString(2, downloadPath);
            ps.setString(3, summary);
            ps.setString(4, LocalDateTime.now().format(DATE_FORMATTER));
            ps.setLong(5, requestId);
            ps.executeUpdate();
        }
    }

    /**
     * Deletes a book request
     */
    public static void delete(long requestId) throws SQLException {
        String sql = "DELETE FROM book_requests WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, requestId);
            ps.executeUpdate();
        }
    }

    private static List<BookRequest> mapResultsToRequests(ResultSet rs) throws SQLException {
        List<BookRequest> requests = new ArrayList<>();
        while (rs.next()) {
            requests.add(mapRowToRequest(rs));
        }
        rs.close();
        return requests;
    }

    private static BookRequest mapRowToRequest(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        long userId = rs.getLong("requested_by_user_id");
        String userName = rs.getString("requested_by_name");
        String title = rs.getString("title");
        String author = rs.getString("author_name");
        String description = rs.getString("description");
        String genre = rs.getString("genre");
        String statusStr = rs.getString("status");
        String notes = rs.getString("approval_notes");
        String filePath = rs.getString("downloaded_file_path");
        String summary = rs.getString("generated_summary");
        String createdAt = rs.getString("created_at");
        String processedAt = rs.getString("processed_at");

        RequestStatus status = RequestStatus.valueOf(statusStr);
        return new BookRequest(id, userId, userName, title, author, description, genre,
                status, notes, filePath, summary, createdAt, processedAt);
    }
}
