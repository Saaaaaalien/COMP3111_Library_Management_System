package org.example.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated statistics for an author's published books.
 */
public final class AuthorStatsDao {

    private AuthorStatsDao() {}

    public static AuthorStatsSnapshot load(long authorUserId) throws SQLException {
        int publishedBooks = countPublishedBooks(authorUserId);
        int totalBorrows = countTotalBorrows(authorUserId);
        int activeBorrows = countActiveBorrows(authorUserId);
        int totalReads = countTotalReads(authorUserId);
        int distinctReaders = countDistinctReaders(authorUserId);

        // Rating and review persistence is not implemented in this schema yet.
        double averageRating = 0.0;
        int reviewCount = 0;

        List<BookBorrowStat> topBorrowedBooks = loadTopBorrowedBooks(authorUserId);
        List<GenreStat> genreDistribution = loadGenreDistribution(authorUserId);
        List<BorrowStatusStat> borrowStatusDistribution = loadBorrowStatusDistribution(authorUserId);

        return new AuthorStatsSnapshot(
                publishedBooks,
                totalReads,
                totalBorrows,
                activeBorrows,
                distinctReaders,
                averageRating,
                reviewCount,
                topBorrowedBooks,
                genreDistribution,
                borrowStatusDistribution
        );
    }

    private static int countPublishedBooks(long authorUserId) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM books
                WHERE author_user_id = ? AND is_visible = 1
                """;
        return querySingleInt(sql, authorUserId);
    }

    private static int countTotalBorrows(long authorUserId) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM borrows br
                JOIN books b ON b.id = br.book_id
                WHERE b.author_user_id = ? AND b.is_visible = 1
                """;
        return querySingleInt(sql, authorUserId);
    }

    private static int countActiveBorrows(long authorUserId) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM borrows br
                JOIN books b ON b.id = br.book_id
                WHERE b.author_user_id = ? AND b.is_visible = 1
                  AND (br.returned_at IS NULL OR br.returned_at = '')
                """;
        return querySingleInt(sql, authorUserId);
    }

    private static int countDistinctReaders(long authorUserId) throws SQLException {
        String sql = """
                SELECT COUNT(DISTINCT br.borrower_user_id)
                FROM borrows br
                JOIN books b ON b.id = br.book_id
                WHERE b.author_user_id = ? AND b.is_visible = 1
                """;
        return querySingleInt(sql, authorUserId);
    }

    private static int countTotalReads(long authorUserId) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM reading_progress rp
                JOIN books b ON b.id = rp.book_id
                WHERE b.author_user_id = ? AND b.is_visible = 1
                """;
        try {
            return querySingleInt(sql, authorUserId);
        } catch (SQLException ex) {
            if (isNoSuchTable(ex)) {
                return 0;
            }
            throw ex;
        }
    }

    private static List<BookBorrowStat> loadTopBorrowedBooks(long authorUserId) throws SQLException {
        String sql = """
                SELECT b.title AS title, COUNT(br.id) AS borrow_count
                FROM books b
                LEFT JOIN borrows br ON br.book_id = b.id
                WHERE b.author_user_id = ? AND b.is_visible = 1
                GROUP BY b.id, b.title
                ORDER BY borrow_count DESC, b.title COLLATE NOCASE ASC
                LIMIT 6
                """;
        List<BookBorrowStat> rows = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, authorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new BookBorrowStat(
                            rs.getString("title"),
                            rs.getInt("borrow_count")
                    ));
                }
            }
        }
        return rows;
    }

    private static List<GenreStat> loadGenreDistribution(long authorUserId) throws SQLException {
        String sql = """
                SELECT COALESCE(NULLIF(TRIM(genre), ''), 'Uncategorized') AS genre_name, COUNT(*) AS total
                FROM books
                WHERE author_user_id = ? AND is_visible = 1
                GROUP BY COALESCE(NULLIF(TRIM(genre), ''), 'Uncategorized')
                ORDER BY total DESC, genre_name COLLATE NOCASE ASC
                """;
        List<GenreStat> rows = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, authorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new GenreStat(
                            rs.getString("genre_name"),
                            rs.getInt("total")
                    ));
                }
            }
        }
        return rows;
    }

    private static List<BorrowStatusStat> loadBorrowStatusDistribution(long authorUserId) throws SQLException {
        String sql = """
                SELECT
                  CASE
                    WHEN br.returned_at IS NULL OR br.returned_at = '' THEN 'Active'
                    ELSE 'Returned'
                  END AS status_name,
                  COUNT(*) AS total
                FROM borrows br
                JOIN books b ON b.id = br.book_id
                WHERE b.author_user_id = ? AND b.is_visible = 1
                GROUP BY status_name
                ORDER BY total DESC
                """;
        List<BorrowStatusStat> rows = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, authorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new BorrowStatusStat(
                            rs.getString("status_name"),
                            rs.getInt("total")
                    ));
                }
            }
        }
        return rows;
    }

    private static int querySingleInt(String sql, long authorUserId) throws SQLException {
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, authorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    private static boolean isNoSuchTable(SQLException e) {
        String msg = e.getMessage();
        return msg != null && msg.toLowerCase().contains("no such table");
    }

    public record BookBorrowStat(String title, int borrowCount) {}

    public record GenreStat(String genre, int count) {}

    public record BorrowStatusStat(String status, int count) {}

    public record AuthorStatsSnapshot(
            int publishedBooks,
            int totalReads,
            int totalBorrows,
            int activeBorrows,
            int distinctReaders,
            double averageRating,
            int reviewCount,
            List<BookBorrowStat> topBorrowedBooks,
            List<GenreStat> genreDistribution,
            List<BorrowStatusStat> borrowStatusDistribution
    ) {}
}
