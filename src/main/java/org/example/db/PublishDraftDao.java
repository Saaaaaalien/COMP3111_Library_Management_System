package org.example.db;

import java.sql.*;
import java.util.Optional;

/**
 * Auto-saved publish form per author (one row per author_user_id).
 */
public final class PublishDraftDao {

    public record Draft(String title, String genre, String summary, String filePath, String updatedAt) {}

    private PublishDraftDao() {}

    public static void upsert(long authorUserId, String title, String genre, String summary,
                             String filePath, String updatedAt) throws SQLException {
        String sql = """
            INSERT INTO publish_drafts (author_user_id, title, genre, summary, file_path, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(author_user_id) DO UPDATE SET
                title = excluded.title,
                genre = excluded.genre,
                summary = excluded.summary,
                file_path = excluded.file_path,
                updated_at = excluded.updated_at
            """;
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, authorUserId);
            ps.setString(2, title);
            ps.setString(3, genre);
            ps.setString(4, summary);
            if (filePath != null) {
                ps.setString(5, filePath);
            } else {
                ps.setNull(5, Types.VARCHAR);
            }
            ps.setString(6, updatedAt);
            ps.executeUpdate();
        }
    }

    public static Optional<Draft> findByAuthor(long authorUserId) throws SQLException {
        String sql = "SELECT title, genre, summary, file_path, updated_at FROM publish_drafts WHERE author_user_id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, authorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Draft(
                        rs.getString("title"),
                        rs.getString("genre"),
                        rs.getString("summary"),
                        rs.getString("file_path"),
                        rs.getString("updated_at")
                    ));
                }
            }
        }
        return Optional.empty();
    }

    public static void deleteForAuthor(long authorUserId) throws SQLException {
        String sql = "DELETE FROM publish_drafts WHERE author_user_id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, authorUserId);
            ps.executeUpdate();
        }
    }
}
