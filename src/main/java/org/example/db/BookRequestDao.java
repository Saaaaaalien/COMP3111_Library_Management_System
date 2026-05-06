package org.example.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
     * Counts prior requests from this user with the same title and author (any status), for duplicate awareness.
     */
    public static int countSameTitleAuthorForUser(long userId, String title, String authorName) throws SQLException {
        String t = title == null ? "" : title.trim().toLowerCase();
        String a = authorName == null ? "" : authorName.trim().toLowerCase();
        if (t.isEmpty() && a.isEmpty()) {
            return 0;
        }
        String sql = """
            SELECT COUNT(*) FROM book_requests
            WHERE requested_by_user_id = ?
              AND LOWER(TRIM(title)) = ?
              AND LOWER(TRIM(author_name)) = ?
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, t);
            ps.setString(3, a);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public static boolean hasSimilarPendingRequest(long userId, String title, String authorName) throws SQLException {
        String t = title == null ? "" : title.trim().toLowerCase();
        String a = authorName == null ? "" : authorName.trim().toLowerCase();
        if (t.isEmpty() && a.isEmpty()) {
            return false;
        }
        String sql = """
            SELECT COUNT(*) FROM book_requests
            WHERE requested_by_user_id = ?
              AND status = ?
              AND LOWER(TRIM(title)) = ?
              AND LOWER(TRIM(author_name)) = ?
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, RequestStatus.PENDING.name());
            ps.setString(3, t);
            ps.setString(4, a);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
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
     * Marks a request as downloaded (book file retrieved; awaiting librarian approval to publish).
     * The request remains visible in the active list so the librarian can then approve it.
     */
    public static void markAsDownloaded(long requestId, String downloadPath, String summary) throws SQLException {
        String sql = """
            UPDATE book_requests
            SET status = ?, downloaded_file_path = ?, generated_summary = ?, processed_at = ?
            WHERE id = ?
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, RequestStatus.DOWNLOADED.name());
            ps.setString(2, downloadPath);
            ps.setString(3, summary);
            ps.setString(4, LocalDateTime.now().format(DATE_FORMATTER));
            ps.setLong(5, requestId);
            ps.executeUpdate();
        }
    }

    /**
     * Records that an alternative book (different title/author from what was requested) was
     * downloaded.  Updates title, author_name, file path, summary, and sets status to DOWNLOADED.
     */
    public static void markAsDownloadedAlternative(long requestId,
                                                    String altTitle, String altAuthor,
                                                    String downloadPath, String summary) throws SQLException {
        String sql = """
            UPDATE book_requests
            SET status = ?, title = ?, author_name = ?,
                downloaded_file_path = ?, generated_summary = ?,
                approval_notes = ?, processed_at = ?
            WHERE id = ?
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, RequestStatus.DOWNLOADED.name());
            ps.setString(2, altTitle);
            ps.setString(3, altAuthor);
            ps.setString(4, downloadPath);
            ps.setString(5, summary);
            ps.setString(6, "Alternative downloaded: \"" + altTitle + "\" by " + altAuthor);
            ps.setString(7, LocalDateTime.now().format(DATE_FORMATTER));
            ps.setLong(8, requestId);
            ps.executeUpdate();
        }
    }

    /**
     * Legacy alias kept for backward compatibility.
     * @deprecated Use {@link #markAsDownloaded} instead.
     */
    @Deprecated
    public static void markAsProcessed(long requestId, String downloadPath, String summary) throws SQLException {
        markAsDownloaded(requestId, downloadPath, summary);
    }

    /**
     * Returns all requests that are still active (PENDING or DOWNLOADED), plus any legacy
     * PROCESSED rows, ordered newest-first.  APPROVED and REJECTED requests are excluded
     * because they no longer require librarian action.
     */
    public static List<BookRequest> findActivePending() throws SQLException {
        String sql = """
            SELECT * FROM book_requests
            WHERE status IN (?, ?, ?)
            ORDER BY created_at DESC
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, RequestStatus.PENDING.name());
            ps.setString(2, RequestStatus.DOWNLOADED.name());
            ps.setString(3, RequestStatus.PROCESSED.name());
            return mapResultsToRequests(ps.executeQuery());
        }
    }

    /**
     * Returns every book request, all statuses, ordered newest-first.
     */
    public static List<BookRequest> findAll() throws SQLException {
        String sql = "SELECT * FROM book_requests ORDER BY created_at DESC";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return mapResultsToRequests(rs);
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
