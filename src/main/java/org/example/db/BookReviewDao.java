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
            String flaggedByAuthorAt,
            String sentimentLabel,
            String sentimentSource
    ) {
        public boolean isFlagged() {
            return flaggedByAuthorAt != null && !flaggedByAuthorAt.isBlank();
        }

        public boolean hasReply() {
            return authorReplyText != null && !authorReplyText.isBlank();
        }

        /** True when {@code sentiment_label} is null or blank (not yet classified for analytics breakdown). */
        public boolean isSentimentUnclassified() {
            return sentimentLabel == null || sentimentLabel.isBlank();
        }
    }

    /**
     * Aggregate feedback for an author's non-flagged reviews on their books.
     *
     * @param authorUserId books.author_user_id
     */
    public record FeedbackAnalytics(
            int totalReviews,
            double averageRating,
            int star1Count,
            int star2Count,
            int star3Count,
            int star4Count,
            int star5Count,
            int sentimentPositiveCount,
            int sentimentNeutralCount,
            int sentimentNegativeCount,
            int sentimentUnclassifiedCount
    ) {}

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
                   r.flagged_by_author_at,
                   r.sentiment_label,
                   r.sentiment_source
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
     * Updates persisted sentiment for a review the author may see (same authorization as {@link #replyToReview}).
     *
     * @param label   {@code positive}, {@code neutral}, or {@code negative}
     * @param source  {@code ai} or {@code heuristic}
     */
    public static boolean updateSentimentForAuthor(
            long reviewId, long authorUserId, String label, String source) throws SQLException {
        String sql = """
            UPDATE book_reviews
            SET sentiment_label = ?, sentiment_source = ?
            WHERE id = ?
              AND (flagged_by_author_at IS NULL OR flagged_by_author_at = '')
              AND book_id IN (SELECT id FROM books WHERE author_user_id = ?)
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, label);
            ps.setString(2, source);
            ps.setLong(3, reviewId);
            ps.setLong(4, authorUserId);
            return ps.executeUpdate() > 0;
        }
    }

    public static FeedbackAnalytics loadFeedbackAnalytics(long authorUserId) throws SQLException {
        String sql = """
            SELECT
                COUNT(*) AS total_reviews,
                AVG(r.rating) AS avg_rating,
                SUM(CASE WHEN r.rating = 1 THEN 1 ELSE 0 END) AS star1,
                SUM(CASE WHEN r.rating = 2 THEN 1 ELSE 0 END) AS star2,
                SUM(CASE WHEN r.rating = 3 THEN 1 ELSE 0 END) AS star3,
                SUM(CASE WHEN r.rating = 4 THEN 1 ELSE 0 END) AS star4,
                SUM(CASE WHEN r.rating = 5 THEN 1 ELSE 0 END) AS star5,
                SUM(CASE WHEN r.sentiment_label = 'positive' THEN 1 ELSE 0 END) AS pos_cnt,
                SUM(CASE WHEN r.sentiment_label = 'neutral' THEN 1 ELSE 0 END) AS neu_cnt,
                SUM(CASE WHEN r.sentiment_label = 'negative' THEN 1 ELSE 0 END) AS neg_cnt,
                SUM(CASE WHEN r.sentiment_label IS NULL OR r.sentiment_label = '' THEN 1 ELSE 0 END) AS unclassified_cnt
            FROM book_reviews r
            JOIN books b ON b.id = r.book_id
            WHERE b.author_user_id = ?
              AND (r.flagged_by_author_at IS NULL OR r.flagged_by_author_at = '')
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, authorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new FeedbackAnalytics(0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
                }
                int total = rs.getInt("total_reviews");
                double avg = rs.getObject("avg_rating") == null ? 0.0 : rs.getDouble("avg_rating");
                return new FeedbackAnalytics(
                        total,
                        avg,
                        rs.getInt("star1"),
                        rs.getInt("star2"),
                        rs.getInt("star3"),
                        rs.getInt("star4"),
                        rs.getInt("star5"),
                        rs.getInt("pos_cnt"),
                        rs.getInt("neu_cnt"),
                        rs.getInt("neg_cnt"),
                        rs.getInt("unclassified_cnt")
                );
            }
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
                   r.flagged_by_author_at,
                   r.sentiment_label,
                   r.sentiment_source
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
                rs.getString("flagged_by_author_at"),
                rs.getString("sentiment_label"),
                rs.getString("sentiment_source")
        );
    }
}
