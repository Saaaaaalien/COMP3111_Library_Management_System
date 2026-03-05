package org.example.db;

import org.example.domain.BookSubmission;
import org.example.domain.SubmissionStatus;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access for book_submissions table.
 */
public final class BookSubmissionDao {

    private BookSubmissionDao() {}

    /**
     * Inserts a new submission. Returns the generated id.
     */
    public static long insert(String title, long authorUserId, String genre, String description,
                             String filePath, String submittedAt) throws SQLException {
        String sql = """
            INSERT INTO book_submissions (title, author_user_id, genre, description, file_path, submitted_at, status)
            VALUES (?, ?, ?, ?, ?, ?, 'PENDING')
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setLong(2, authorUserId);
            ps.setString(3, genre);
            ps.setString(4, description);
            ps.setString(5, filePath);
            ps.setString(6, submittedAt);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Insert book_submission failed, no id returned");
    }

    /**
     * Finds all submissions with status PENDING.
     */
    public static List<BookSubmission> findAllPending() throws SQLException {
        String sql = """
            SELECT id, title, author_user_id, genre, description, file_path, submitted_at, status, decision_at
            FROM book_submissions WHERE status = 'PENDING' ORDER BY submitted_at ASC
            """;
        List<BookSubmission> list = new ArrayList<>();
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
     * Finds a submission by id.
     */
    public static Optional<BookSubmission> findById(long id) throws SQLException {
        String sql = """
            SELECT id, title, author_user_id, genre, description, file_path, submitted_at, status, decision_at
            FROM book_submissions WHERE id = ?
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
     * Updates status and decision_at for a submission.
     */
    public static void updateStatus(long id, SubmissionStatus status, String decisionAt) throws SQLException {
        String sql = "UPDATE book_submissions SET status = ?, decision_at = ? WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, decisionAt);
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    private static BookSubmission mapRow(ResultSet rs) throws SQLException {
        return new BookSubmission(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getLong("author_user_id"),
            rs.getString("genre"),
            rs.getString("description"),
            rs.getString("file_path"),
            rs.getString("submitted_at"),
            SubmissionStatus.valueOf(rs.getString("status")),
            rs.getString("decision_at")
        );
    }
}
