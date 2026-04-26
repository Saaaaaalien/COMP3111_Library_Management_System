package org.example.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access for author-side review handling.
 */
public final class BookReviewDao {

    private BookReviewDao() {}

    public record AuthorVisibleReview(
            long id,
            long bookId,
            String bookTitle,
            long reviewerUserId,
            String reviewerName,
            int rating,
            String reviewText,
            String createdAt,
            String authorReplyText,
            String authorReplyAt,
            String flaggedByAuthorAt
    ) {
        public boolean isFlagged() {
            return flaggedByAuthorAt != null && !flaggedByAuthorAt.isBlank();
        }

        public boolean hasReply() {
            return authorReplyText != null && !authorReplyText.isBlank();
        }
    }

    public static List<AuthorVisibleReview> findVisibleForAuthor(long authorUserId) throws SQLException {
        String sql = """
            SELECT r.id,
                   r.book_id,
                   b.title AS book_title,
                   r.reviewer_user_id,
                   COALESCE(u.full_name, u.username, 'Unknown reviewer') AS reviewer_name,
                   r.rating,
                   r.review_text,
                   r.created_at,
                   r.author_reply_text,
                   r.author_reply_at,
                   r.flagged_by_author_at
            FROM book_reviews r
            JOIN books b ON b.id = r.book_id
            LEFT JOIN users u ON u.id = r.reviewer_user_id
            WHERE b.author_user_id = ?
              AND (r.flagged_by_author_at IS NULL OR r.flagged_by_author_at = '')
            ORDER BY r.created_at DESC
            """;
        List<AuthorVisibleReview> out = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, authorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapAuthorVisible(rs));
                }
            }
        }
        return out;
    }

    public static boolean replyToReview(long reviewId, long authorUserId, String replyText, String replyAt) throws SQLException {
        String sql = """
            UPDATE book_reviews
            SET author_reply_text = ?, author_reply_at = ?
            WHERE id = ?
              AND (flagged_by_author_at IS NULL OR flagged_by_author_at = '')
              AND book_id IN (SELECT id FROM books WHERE author_user_id = ?)
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, replyText);
            ps.setString(2, replyAt);
            ps.setLong(3, reviewId);
            ps.setLong(4, authorUserId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Flags a review as hidden for the owning author.
     *
     * @return true when this call newly flags the row; false if already flagged or inaccessible.
     */
    public static boolean flagByAuthor(long reviewId, long authorUserId, String flaggedAt) throws SQLException {
        String sql = """
            UPDATE book_reviews
            SET flagged_by_author_at = ?
            WHERE id = ?
              AND (flagged_by_author_at IS NULL OR flagged_by_author_at = '')
              AND book_id IN (SELECT id FROM books WHERE author_user_id = ?)
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, flaggedAt);
            ps.setLong(2, reviewId);
            ps.setLong(3, authorUserId);
            return ps.executeUpdate() > 0;
        }
    }

    public static Optional<AuthorVisibleReview> findByIdForAuthor(long reviewId, long authorUserId) throws SQLException {
        String sql = """
            SELECT r.id,
                   r.book_id,
                   b.title AS book_title,
                   r.reviewer_user_id,
                   COALESCE(u.full_name, u.username, 'Unknown reviewer') AS reviewer_name,
                   r.rating,
                   r.review_text,
                   r.created_at,
                   r.author_reply_text,
                   r.author_reply_at,
                   r.flagged_by_author_at
            FROM book_reviews r
            JOIN books b ON b.id = r.book_id
            LEFT JOIN users u ON u.id = r.reviewer_user_id
            WHERE r.id = ?
              AND b.author_user_id = ?
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, reviewId);
            ps.setLong(2, authorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapAuthorVisible(rs));
                }
            }
        }
        return Optional.empty();
    }

    private static AuthorVisibleReview mapAuthorVisible(ResultSet rs) throws SQLException {
        return new AuthorVisibleReview(
                rs.getLong("id"),
                rs.getLong("book_id"),
                rs.getString("book_title"),
                rs.getLong("reviewer_user_id"),
                rs.getString("reviewer_name"),
                rs.getInt("rating"),
                rs.getString("review_text"),
                rs.getString("created_at"),
                rs.getString("author_reply_text"),
                rs.getString("author_reply_at"),
                rs.getString("flagged_by_author_at")
        );
    }
}
