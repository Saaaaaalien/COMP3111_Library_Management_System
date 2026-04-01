package org.example.db;

import org.example.domain.Borrow;
import org.example.domain.BorrowWithBook;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access for borrows table.
 */
public final class BorrowDao {

    private BorrowDao() {}

    /**
     * Inserts a new borrow. Returns the generated id.
     * @param dueAt optional due date (ISO-8601 instant); may be null for legacy.
     */
    public static long insert(long bookId, long borrowerUserId, String borrowedAt, String dueAt) throws SQLException {
        String sql = "INSERT INTO borrows (book_id, borrower_user_id, borrowed_at, due_at) VALUES (?, ?, ?, ?)";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, bookId);
            ps.setLong(2, borrowerUserId);
            ps.setString(3, borrowedAt);
            ps.setString(4, dueAt);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Insert borrow failed, no id returned");
    }

    /**
     * Finds the active (not returned) borrow for a book, if any.
     */
    public static Optional<Borrow> findActiveByBookId(long bookId) throws SQLException {
        String sql = """
            SELECT id, book_id, borrower_user_id, borrowed_at, returned_at, due_at
            FROM borrows WHERE book_id = ? AND returned_at IS NULL
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a borrow by id.
     */
    public static Optional<Borrow> findById(long id) throws SQLException {
        String sql = """
            SELECT id, book_id, borrower_user_id, borrowed_at, returned_at, due_at
            FROM borrows WHERE id = ?
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
     * Counts how many books the user currently has borrowed (not yet returned).
     */
    public static int countActiveByBorrowerUserId(long borrowerUserId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM borrows WHERE borrower_user_id = ? AND returned_at IS NULL";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, borrowerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Finds all borrows for a user (active and returned), with book title and author.
     * Ordered by borrowed_at descending (most recent first).
     */
    public static List<BorrowWithBook> findAllByBorrowerUserId(long borrowerUserId) throws SQLException {
        String sql = """
            SELECT b.id AS borrow_id, b.book_id, b.borrowed_at, b.returned_at, b.due_at,
                   COALESCE(k.title, '[Removed book]') AS title,
                   COALESCE(k.author_full_name_snapshot, 'Unknown author') AS author,
                   k.file_path AS book_file_path
            FROM borrows b
            LEFT JOIN books k ON k.id = b.book_id
            WHERE b.borrower_user_id = ?
            ORDER BY b.borrowed_at DESC
            """;
        List<BorrowWithBook> list = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, borrowerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new BorrowWithBook(
                        rs.getLong("borrow_id"),
                        rs.getLong("book_id"),
                        rs.getString("title"),
                        rs.getString("author"),
                        rs.getString("borrowed_at"),
                        rs.getString("returned_at"),
                        rs.getString("due_at"),
                        rs.getString("book_file_path")
                    ));
                }
            }
        }
        return list;
    }

    /**
     * Sets returned_at for a borrow (marks as returned).
     */
    public static void updateReturnedAt(long id, String returnedAt) throws SQLException {
        String sql = "UPDATE borrows SET returned_at = ? WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, returnedAt);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /**
     * Active borrows for a book (for removal notifications).
     */
    public static List<long[]> findActiveBorrowerPairsForBook(long bookId) throws SQLException {
        String sql = """
            SELECT id, borrower_user_id FROM borrows
            WHERE book_id = ? AND (returned_at IS NULL OR returned_at = '')
            """;
        List<long[]> list = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new long[] { rs.getLong("id"), rs.getLong("borrower_user_id") });
                }
            }
        }
        return list;
    }

    /**
     * Active borrows whose due_at is strictly before {@code nowIso} (ISO-8601 instant strings compare lexicographically).
     */
    public static List<Long> findOverdueActiveBorrowIds(String nowIso) throws SQLException {
        String sql = """
            SELECT id FROM borrows
            WHERE (returned_at IS NULL OR returned_at = '')
            AND due_at IS NOT NULL AND due_at != ''
            AND due_at < ?
            """;
        List<Long> ids = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nowIso);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong("id"));
                }
            }
        }
        return ids;
    }

    /**
     * Active borrows with a due date (for reminder notifications).
     */
    public static List<ActiveBorrowDueRow> findAllActiveWithDue() throws SQLException {
        String sql = """
            SELECT b.id AS borrow_id, b.borrower_user_id, b.book_id, b.due_at, k.title AS title
            FROM borrows b
            JOIN books k ON k.id = b.book_id
            WHERE (b.returned_at IS NULL OR b.returned_at = '')
            AND b.due_at IS NOT NULL AND b.due_at != ''
            """;
        List<ActiveBorrowDueRow> list = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new ActiveBorrowDueRow(
                    rs.getLong("borrow_id"),
                    rs.getLong("borrower_user_id"),
                    rs.getLong("book_id"),
                    rs.getString("due_at"),
                    rs.getString("title")
                ));
            }
        }
        return list;
    }

    public record ActiveBorrowDueRow(long borrowId, long borrowerUserId, long bookId, String dueAt, String bookTitle) {}

    public static int countActiveBorrowsForBook(long bookId) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM borrows
            WHERE book_id = ? AND (returned_at IS NULL OR returned_at = '')
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    private static Borrow mapRow(ResultSet rs) throws SQLException {
        return new Borrow(
            rs.getLong("id"),
            rs.getLong("book_id"),
            rs.getLong("borrower_user_id"),
            rs.getString("borrowed_at"),
            rs.getString("returned_at"),
            rs.getString("due_at")
        );
    }
}
