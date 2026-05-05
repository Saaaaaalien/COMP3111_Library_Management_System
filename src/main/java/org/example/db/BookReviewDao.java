package org.example.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access for <strong>author-side</strong> review handling (list, reply, flag, sentiment, analytics).
 * Student/staff flows that <em>insert</em> ratings or review text are not part of this module yet; rows may
 * exist from tests, migrations, or future reader UI.
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
     * Aggregate feedback for an author's non-flagged reviews on their books
     * (star distribution and sentiment breakdown).
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

    private static final FeedbackAnalytics EMPTY_FEEDBACK_ANALYTICS =
            new FeedbackAnalytics(0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public static List<AuthorVisibleReview> findVisibleForAuthor(long authorUserId) throws SQLException {
        String sql = """
            SELECT r.id,
                   r.book_id,
                   b.title AS book_title,
                   r.reviewer_user_id,
                   CASE WHEN COALESCE(r.is_anonymous, 0) = 1 THEN 'Anonymous reader'
                        ELSE COALESCE(u.full_name, u.username, 'Unknown reviewer') END AS reviewer_name,
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
                SUM(CASE WHEN LOWER(TRIM(COALESCE(r.sentiment_label, ''))) = 'positive' THEN 1 ELSE 0 END) AS pos_cnt,
                SUM(CASE WHEN LOWER(TRIM(COALESCE(r.sentiment_label, ''))) = 'neutral' THEN 1 ELSE 0 END) AS neu_cnt,
                SUM(CASE WHEN LOWER(TRIM(COALESCE(r.sentiment_label, ''))) = 'negative' THEN 1 ELSE 0 END) AS neg_cnt,
                SUM(CASE
                        WHEN r.sentiment_label IS NULL OR TRIM(r.sentiment_label) = '' THEN 1
                        WHEN LOWER(TRIM(r.sentiment_label)) NOT IN ('positive', 'neutral', 'negative') THEN 1
                        ELSE 0
                    END) AS unclassified_cnt
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
                    return EMPTY_FEEDBACK_ANALYTICS;
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
                   CASE WHEN COALESCE(r.is_anonymous, 0) = 1 THEN 'Anonymous reader'
                        ELSE COALESCE(u.full_name, u.username, 'Unknown reviewer') END AS reviewer_name,
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

    // ── Student / catalog (Task 1.9) ─────────────────────────────────────────

    public record BookRatingAggregate(double averageRating, int reviewCount) {
        public String averageDisplay() {
            if (reviewCount <= 0) return "—";
            return String.format(java.util.Locale.US, "%.1f", averageRating);
        }
    }

    public record CatalogReviewRow(
            long id,
            long bookId,
            long reviewerUserId,
            String displayName,
            int rating,
            String reviewText,
            String createdAt,
            int helpfulVotes,
            boolean anonymous
    ) {}

    public static BookRatingAggregate aggregateForBook(long bookId) throws SQLException {
        String sql = """
            SELECT AVG(r.rating) AS avg_r, COUNT(*) AS cnt
            FROM book_reviews r
            WHERE r.book_id = ?
              AND (r.flagged_by_author_at IS NULL OR r.flagged_by_author_at = '')
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new BookRatingAggregate(0, 0);
                }
                int cnt = rs.getInt("cnt");
                double avg = rs.getObject("avg_r") == null ? 0.0 : rs.getDouble("avg_r");
                return new BookRatingAggregate(avg, cnt);
            }
        }
    }

    public static java.util.Map<Long, BookRatingAggregate> aggregateForBookIds(java.util.Collection<Long> bookIds) throws SQLException {
        java.util.Map<Long, BookRatingAggregate> out = new java.util.HashMap<>();
        if (bookIds == null || bookIds.isEmpty()) {
            return out;
        }
        List<Long> ids = bookIds.stream().distinct().toList();
        String placeholders = ids.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
        String sql = """
            SELECT r.book_id AS bid, AVG(r.rating) AS avg_r, COUNT(*) AS cnt
            FROM book_reviews r
            WHERE (r.flagged_by_author_at IS NULL OR r.flagged_by_author_at = '')
              AND r.book_id IN (""" + placeholders + """
            )
            GROUP BY r.book_id
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (Long id : ids) {
                ps.setLong(i++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long bid = rs.getLong("bid");
                    int cnt = rs.getInt("cnt");
                    double avg = rs.getObject("avg_r") == null ? 0.0 : rs.getDouble("avg_r");
                    out.put(bid, new BookRatingAggregate(avg, cnt));
                }
            }
        }
        return out;
    }

    public static List<CatalogReviewRow> findPublicReviewsForBook(long bookId, String sort) throws SQLException {
        String order = "HELPFUL".equalsIgnoreCase(sort)
                ? "r.helpful_votes DESC, r.created_at DESC"
                : "r.created_at DESC";
        String sql = """
            SELECT r.id, r.book_id, r.reviewer_user_id,
                   CASE WHEN COALESCE(r.is_anonymous, 0) = 1 THEN 'Anonymous'
                        ELSE COALESCE(u.full_name, u.username, 'Reader') END AS dname,
                   r.rating, r.review_text, r.created_at,
                   COALESCE(r.helpful_votes, 0) AS hv,
                   CASE WHEN COALESCE(r.is_anonymous, 0) = 1 THEN 1 ELSE 0 END AS anon
            FROM book_reviews r
            LEFT JOIN users u ON u.id = r.reviewer_user_id
            WHERE r.book_id = ?
              AND (r.flagged_by_author_at IS NULL OR r.flagged_by_author_at = '')
            ORDER BY
            """ + order;
        List<CatalogReviewRow> list = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new CatalogReviewRow(
                            rs.getLong("id"),
                            rs.getLong("book_id"),
                            rs.getLong("reviewer_user_id"),
                            rs.getString("dname"),
                            rs.getInt("rating"),
                            rs.getString("review_text"),
                            rs.getString("created_at"),
                            rs.getInt("hv"),
                            rs.getInt("anon") == 1
                    ));
                }
            }
        }
        return list;
    }

    public static boolean hasUserEverBorrowedBook(long userId, long bookId) throws SQLException {
        String sql = "SELECT 1 FROM borrows WHERE borrower_user_id = ? AND book_id = ? LIMIT 1";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Current user's saved row for catalog edit (Task 1.9). */
    public record OwnReviewDraft(int rating, String reviewText, boolean anonymous) {}

    public static Optional<OwnReviewDraft> findOwnReview(long bookId, long reviewerUserId) throws SQLException {
        String sql = """
            SELECT rating, review_text, COALESCE(is_anonymous, 0) AS anon
            FROM book_reviews
            WHERE book_id = ? AND reviewer_user_id = ?
            LIMIT 1
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            ps.setLong(2, reviewerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new OwnReviewDraft(
                        rs.getInt("rating"),
                        rs.getString("review_text"),
                        rs.getInt("anon") == 1
                ));
            }
        }
    }

    public static Optional<Long> findReviewIdByBookAndReviewer(long bookId, long reviewerUserId) throws SQLException {
        String sql = "SELECT id FROM book_reviews WHERE book_id = ? AND reviewer_user_id = ? LIMIT 1";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            ps.setLong(2, reviewerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getLong(1));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Inserts or replaces the student's rating/review for a book (one row per reviewer per book).
     */
    public static long upsertStudentReview(long bookId, long reviewerUserId, int rating, String reviewText,
                                          boolean anonymous, String createdAt) throws SQLException {
        if (rating < 1 || rating > 5) {
            throw new SQLException("Rating must be 1–5");
        }
        Optional<Long> existing = findReviewIdByBookAndReviewer(bookId, reviewerUserId);
        Connection conn = Database.getConnection();
        if (existing.isPresent()) {
            String upd = """
                UPDATE book_reviews
                SET rating = ?, review_text = ?, is_anonymous = ?, created_at = ?,
                    author_reply_text = NULL, author_reply_at = NULL,
                    flagged_by_author_at = NULL, sentiment_label = NULL, sentiment_source = NULL
                WHERE id = ? AND reviewer_user_id = ?
                """;
            try (PreparedStatement ps = conn.prepareStatement(upd)) {
                ps.setInt(1, rating);
                ps.setString(2, reviewText);
                ps.setInt(3, anonymous ? 1 : 0);
                ps.setString(4, createdAt);
                ps.setLong(5, existing.get());
                ps.setLong(6, reviewerUserId);
                ps.executeUpdate();
            }
            return existing.get();
        }
        String ins = """
            INSERT INTO book_reviews (book_id, reviewer_user_id, rating, review_text, created_at,
                is_anonymous, helpful_votes)
            VALUES (?, ?, ?, ?, ?, ?, 0)
            """;
        try (PreparedStatement ps = conn.prepareStatement(ins, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, bookId);
            ps.setLong(2, reviewerUserId);
            ps.setInt(3, rating);
            ps.setString(4, reviewText);
            ps.setString(5, createdAt);
            ps.setInt(6, anonymous ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Insert review failed");
    }

    public static boolean userMarkedHelpful(long reviewId, long actingUserId) throws SQLException {
        String sql = "SELECT 1 FROM review_helpful_marks WHERE review_id = ? AND user_id = ? LIMIT 1";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, reviewId);
            ps.setLong(2, actingUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Records one helpful vote per {@code actingUserId} per review (ignored for the review's author).
     *
     * @return true if the helpful count was incremented
     */
    public static boolean incrementHelpful(long reviewId, long actingUserId) throws SQLException {
        Connection conn = Database.getConnection();
        long reviewerId;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT reviewer_user_id FROM book_reviews WHERE id = ?")) {
            ps.setLong(1, reviewId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                reviewerId = rs.getLong("reviewer_user_id");
            }
        }
        if (reviewerId == actingUserId) {
            return false;
        }
        String now = java.time.Instant.now().toString();
        String ins = """
            INSERT OR IGNORE INTO review_helpful_marks (review_id, user_id, created_at)
            VALUES (?, ?, ?)
            """;
        String upd = """
            UPDATE book_reviews
            SET helpful_votes = COALESCE(helpful_votes, 0) + 1
            WHERE id = ? AND reviewer_user_id <> ?
            """;
        boolean prevAuto = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            int inserted;
            try (PreparedStatement ps = conn.prepareStatement(ins)) {
                ps.setLong(1, reviewId);
                ps.setLong(2, actingUserId);
                ps.setString(3, now);
                inserted = ps.executeUpdate();
            }
            if (inserted <= 0) {
                conn.rollback();
                return false;
            }
            try (PreparedStatement ps = conn.prepareStatement(upd)) {
                ps.setLong(1, reviewId);
                ps.setLong(2, actingUserId);
                ps.executeUpdate();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
            }
            throw e;
        } finally {
            try {
                conn.setAutoCommit(prevAuto);
            } catch (SQLException ignored) {
            }
        }
    }
}
