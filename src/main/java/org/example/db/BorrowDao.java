package org.example.db;

import org.example.domain.Borrow;

import java.sql.*;
import java.util.Optional;

/**
 * Data access for borrows table.
 */
public final class BorrowDao {

    private BorrowDao() {}

    /**
     * Inserts a new borrow. Returns the generated id.
     */
    public static long insert(long bookId, long borrowerUserId, String borrowedAt) throws SQLException {
        String sql = "INSERT INTO borrows (book_id, borrower_user_id, borrowed_at) VALUES (?, ?, ?)";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, bookId);
            ps.setLong(2, borrowerUserId);
            ps.setString(3, borrowedAt);
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
            SELECT id, book_id, borrower_user_id, borrowed_at, returned_at
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

    private static Borrow mapRow(ResultSet rs) throws SQLException {
        return new Borrow(
            rs.getLong("id"),
            rs.getLong("book_id"),
            rs.getLong("borrower_user_id"),
            rs.getString("borrowed_at"),
            rs.getString("returned_at")
        );
    }
}
