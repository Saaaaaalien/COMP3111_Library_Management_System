package org.example.db;

import org.example.domain.Role;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Inserts minimal rows for FK-backed DAO tests against {@code data/library.db}
 * and cleans up drafts/submissions tied to the test author.
 */
public final class DbTestFixtures {

    private DbTestFixtures() {}

    /**
     * Inserts an AUTHOR user with a unique username; returns generated id.
     */
    public static long insertTestAuthorUser() throws SQLException {
        String suffix = String.valueOf(System.nanoTime());
        return UserDao.insert(
                "lab7_test_author_" + suffix,
                "Lab7 Test Author",
                Role.AUTHOR,
                "hash",
                "salt",
                "2026-01-01",
                null,
                null
        );
    }

    public static void deletePublishDraftsForAuthor(long authorUserId) throws SQLException {
        String sql = "DELETE FROM publish_drafts WHERE author_user_id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, authorUserId);
            ps.executeUpdate();
        }
    }

    public static void deleteBookSubmissionsForAuthor(long authorUserId) throws SQLException {
        String sql = "DELETE FROM book_submissions WHERE author_user_id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, authorUserId);
            ps.executeUpdate();
        }
    }

    public static void deleteUserById(long userId) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    /**
     * Removes drafts and submissions referencing the author, then the author row.
     */
    public static void cleanupAuthorAndRelatedRows(long authorUserId) throws SQLException {
        deletePublishDraftsForAuthor(authorUserId);
        deleteBookSubmissionsForAuthor(authorUserId);
        deleteUserById(authorUserId);
    }
}
