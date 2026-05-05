package org.example.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.example.security.CryptoUtil;

/**
 * Borrowing + reading progress rows for student/staff reading history (Task 1.8).
 */
public final class ReadingHistoryDao {

    private ReadingHistoryDao() {}

    public record ReadingHistoryRow(
            long borrowId,
            long bookId,
            String title,
            String author,
            String genre,
            String borrowedAt,
            String returnedAt,
            String dueAt,
            Integer lastPage,
            int accumulatedReadSeconds,
            boolean active,
            String filePath
    ) {}

    public static List<ReadingHistoryRow> findForBorrower(long borrowerUserId) throws SQLException {
        String sql = """
            SELECT b.id AS borrow_id,
                   b.book_id,
                   COALESCE(k.title, '[Removed book]') AS title,
                   COALESCE(k.author_full_name_snapshot, 'Unknown author') AS author,
                   COALESCE(k.genre, '') AS genre,
                   b.borrowed_at,
                   b.returned_at,
                   b.due_at,
                   rp.last_page,
                   COALESCE(rp.accumulated_read_seconds, 0) AS read_secs,
                   k.file_path AS enc_file_path
            FROM borrows b
            LEFT JOIN books k ON k.id = b.book_id
            LEFT JOIN reading_progress rp ON rp.borrow_id = b.id
            WHERE b.borrower_user_id = ?
            ORDER BY b.borrowed_at DESC
            """;
        List<ReadingHistoryRow> list = new ArrayList<>();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, borrowerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean active = rs.getString("returned_at") == null || rs.getString("returned_at").isBlank();
                    int lp = rs.getInt("last_page");
                    boolean lpWasNull = rs.wasNull();
                    String encPath = rs.getString("enc_file_path");
                    String path = CryptoUtil.decryptToString(encPath);
                    list.add(new ReadingHistoryRow(
                            rs.getLong("borrow_id"),
                            rs.getLong("book_id"),
                            rs.getString("title"),
                            rs.getString("author"),
                            rs.getString("genre"),
                            rs.getString("borrowed_at"),
                            rs.getString("returned_at"),
                            rs.getString("due_at"),
                            lpWasNull ? null : lp,
                            rs.getInt("read_secs"),
                            active,
                            path
                    ));
                }
            }
        }
        return list;
    }
}
