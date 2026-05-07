package org.example.db;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * SQLite database connection and schema initialization.
 * DB file is stored under user directory: data/library.db
 */
public final class Database {

    private static final String DB_DIR = "data";
    private static final String DB_FILE = "library.db";
    private static final DateTimeFormatter BOOK_REQUEST_CREATED_AT_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static volatile Connection connection;

    private Database() {}

    /**
     * Returns a connection to the SQLite database. Creates the data directory
     * and initializes the schema on first use.
     */
    public static Connection getConnection() throws SQLException {
        if (connection == null) {
            synchronized (Database.class) {
                if (connection == null) {
                    Path dataDir = Paths.get(DB_DIR);
                    if (!dataDir.toFile().exists()) {
                        if (!dataDir.toFile().mkdirs()) {
                            throw new SQLException("Could not create data directory: " + dataDir.toAbsolutePath());
                        }
                    }
                    String url = "jdbc:sqlite:" + dataDir.resolve(DB_FILE).toAbsolutePath();
                    Connection newConnection = DriverManager.getConnection(url);
                    try {
                        try (Statement pragmaSt = newConnection.createStatement()) {
                            pragmaSt.execute("PRAGMA foreign_keys = ON");
                        }
                        initSchema(newConnection);
                        connection = newConnection;
                    } finally {
                        if (connection != newConnection) {
                            try {
                                newConnection.close();
                            } catch (SQLException e) {
                                // Close failed; original exception or connection leak is more important
                            }
                        }
                    }
                }
            }
        }
        return connection;
    }

    private static void initSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    full_name TEXT NOT NULL,
                    role TEXT NOT NULL,
                    password_hash TEXT NOT NULL,
                    password_salt TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    bio TEXT,
                    employee_id TEXT,
                    avatar_path TEXT,
                    failed_login_attempts INTEGER DEFAULT 0,
                    locked_until TEXT
                )
                """);
            migrateUsersTable(conn);

            // pending books table
            st.execute("""
            CREATE TABLE IF NOT EXISTS pending_books (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                author_user_id INTEGER NOT NULL,
                author_full_name TEXT NOT NULL,
                genre TEXT NOT NULL,
                summary TEXT NOT NULL,
                file_name TEXT NOT NULL,
                file_path TEXT NOT NULL,
                file_size INTEGER NOT NULL,
                file_type TEXT NOT NULL,
                submitted_date TEXT NOT NULL,
                status TEXT NOT NULL,
                review_notes TEXT,
                reviewed_date TEXT,
                cover_path TEXT,
                original_book_id INTEGER,
                FOREIGN KEY (author_user_id) REFERENCES users(id)
            )
            """);

            // book submissions table (legacy flow)
            st.execute("""
                CREATE TABLE IF NOT EXISTS book_submissions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    author_user_id INTEGER NOT NULL,
                    genre TEXT NOT NULL,
                    description TEXT NOT NULL,
                    file_path TEXT NOT NULL,
                    submitted_at TEXT NOT NULL,
                    status TEXT NOT NULL,
                    decision_at TEXT,
                    FOREIGN KEY (author_user_id) REFERENCES users(id)
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS books (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    author_user_id INTEGER NOT NULL,
                    author_full_name_snapshot TEXT NOT NULL,
                    genre TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    file_path TEXT NOT NULL,
                    publish_date TEXT NOT NULL,
                    availability TEXT NOT NULL DEFAULT 'AVAILABLE',
                    cover_image_path TEXT,
                    is_visible INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (author_user_id) REFERENCES users(id)
                )
                """);

            // notifications table for in-app messages to users (authors, students, staff)
            st.execute("""
                CREATE TABLE IF NOT EXISTS notifications (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    category TEXT NOT NULL,
                    title TEXT NOT NULL,
                    body TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    read_at TEXT,
                    archived_at TEXT,
                    priority INTEGER DEFAULT 0,
                    dedupe_key TEXT,
                    FOREIGN KEY (user_id) REFERENCES users(id)
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS book_reviews (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    book_id INTEGER NOT NULL,
                    reviewer_user_id INTEGER NOT NULL,
                    rating INTEGER NOT NULL,
                    review_text TEXT,
                    created_at TEXT NOT NULL,
                    author_reply_text TEXT,
                    author_reply_at TEXT,
                    flagged_by_author_at TEXT,
                    sentiment_label TEXT,
                    sentiment_source TEXT,
                    is_anonymous INTEGER NOT NULL DEFAULT 0,
                    helpful_votes INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (book_id) REFERENCES books(id),
                    FOREIGN KEY (reviewer_user_id) REFERENCES users(id)
                )
                """);
            try {
                st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_notifications_user_dedupe ON notifications(user_id, dedupe_key);");
            } catch (SQLException ignored) {
                // ignore index creation errors
            }
            st.execute("""
                CREATE TABLE IF NOT EXISTS borrows (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    book_id INTEGER NOT NULL,
                    borrower_user_id INTEGER NOT NULL,
                    borrowed_at TEXT NOT NULL,
                    returned_at TEXT,
                    due_at TEXT,
                    FOREIGN KEY (book_id) REFERENCES books(id),
                    FOREIGN KEY (borrower_user_id) REFERENCES users(id)
                )
                """);
                // Reading progress (bookmarks / last page) for an active/closed borrow.
                // ReadingProgressDao expects an ON CONFLICT(borrow_id) upsert.
                st.execute("""
                    CREATE TABLE IF NOT EXISTS reading_progress (
                        borrow_id INTEGER PRIMARY KEY,
                        user_id INTEGER NOT NULL,
                        book_id INTEGER NOT NULL,
                        last_page INTEGER NOT NULL,
                        viewer_payload TEXT,
                        updated_at TEXT NOT NULL,
                        accumulated_read_seconds INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (user_id) REFERENCES users(id)
                    )
                    """);

                // Highlight records tied to a borrow/page.
                // ReadingHighlightDao inserts rows with an auto-generated id.
                st.execute("""
                    CREATE TABLE IF NOT EXISTS reading_highlights (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        borrow_id INTEGER NOT NULL,
                        user_id INTEGER NOT NULL,
                        page_index INTEGER NOT NULL,
                        highlight_text TEXT NOT NULL,
                        highlight_rects_json TEXT,
                        created_at TEXT NOT NULL,
                        FOREIGN KEY (user_id) REFERENCES users(id)
                    )
                    """);
                migrateReadingHighlightsTable(conn);
                migrateReadingProgressReadSeconds(conn);
            st.execute("""
                CREATE TABLE IF NOT EXISTS publish_drafts (
                    author_user_id INTEGER PRIMARY KEY,
                    title TEXT,
                    genre TEXT,
                    summary TEXT,
                    file_path TEXT,
                    cover_path TEXT,
                    updated_at TEXT NOT NULL,
                    FOREIGN KEY (author_user_id) REFERENCES users(id)
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS book_requests (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    requested_by_user_id INTEGER NOT NULL,
                    requested_by_name TEXT NOT NULL,
                    title TEXT NOT NULL,
                    author_name TEXT NOT NULL,
                    description TEXT,
                    genre TEXT,
                    status TEXT NOT NULL,
                    approval_notes TEXT,
                    downloaded_file_path TEXT,
                    generated_summary TEXT,
                    is_urgent INTEGER NOT NULL DEFAULT 0,
                    urgent_reason TEXT,
                    created_at TEXT NOT NULL,
                    processed_at TEXT,
                    FOREIGN KEY (requested_by_user_id) REFERENCES users(id)
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS book_change_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    book_id INTEGER NOT NULL,
                    librarian_user_id INTEGER NOT NULL,
                    librarian_name TEXT NOT NULL,
                    change_type TEXT NOT NULL,
                    field_name TEXT,
                    old_value TEXT,
                    new_value TEXT,
                    change_date TEXT NOT NULL,
                    description TEXT,
                    FOREIGN KEY (book_id) REFERENCES books(id),
                    FOREIGN KEY (librarian_user_id) REFERENCES users(id)
                )
                """);
            migratePublishDraftsTable(conn);
            migrateBorrowsTable(conn);
            migratePendingBooksTable(conn);
            migrateBooksTable(conn);
            migrateBookReviewsTable(conn);
            migrateBookReviewsStudentColumns(conn);
            migrateBookReviewsUniqueReviewer(conn);
            migrateReviewHelpfulMarksTable(conn);
            migrateBookRequestsTable(conn);
            seedDemoStalePendingBookRequest(conn);
        }
    }

    /**
     * Inserts a single demo PENDING book request backdated by several days so the librarian
     * Manage Book Requests screen shows MEDIUM (age-based) priority without manual SQL.
     * Skips insert if such a pending row already exists or if no student user exists.
     */
    private static void seedDemoStalePendingBookRequest(Connection conn) throws SQLException {
        final String demoTitle = "romeo and juliet";
        final String demoAuthor = "william shakespeare";
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM book_requests WHERE LOWER(TRIM(title)) = ? AND LOWER(TRIM(author_name)) = ? AND status = 'PENDING'")) {
            ps.setString(1, demoTitle);
            ps.setString(2, demoAuthor);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        }
        long studentId = -1;
        String studentName = "";
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, full_name FROM users WHERE role = ? ORDER BY id LIMIT 1")) {
            ps.setString(1, "STUDENT");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    studentId = rs.getLong(1);
                    studentName = rs.getString("full_name");
                    if (studentName == null) {
                        studentName = "";
                    }
                }
            }
        }
        if (studentId <= 0 || studentName.isBlank()) {
            return;
        }
        String createdAt = LocalDateTime.now().minusDays(7).format(BOOK_REQUEST_CREATED_AT_FMT);
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO book_requests (
                    requested_by_user_id, requested_by_name, title, author_name, description,
                    genre, status, created_at, is_urgent, urgent_reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setLong(1, studentId);
            ps.setString(2, studentName);
            ps.setString(3, "Romeo and Juliet");
            ps.setString(4, "William Shakespeare");
            ps.setString(5, "Demo request seeded for librarian priority-queue demo.");
            ps.setString(6, "fiction");
            ps.setString(7, "PENDING");
            ps.setString(8, createdAt);
            ps.setInt(9, 0);
            ps.setString(10, null);
            ps.executeUpdate();
        }
    }

    /** One mark per user per review so "helpful" cannot be spammed from the catalog. */
    private static void migrateReviewHelpfulMarksTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS review_helpful_marks (
                    review_id INTEGER NOT NULL,
                    user_id INTEGER NOT NULL,
                    created_at TEXT NOT NULL,
                    PRIMARY KEY (review_id, user_id),
                    FOREIGN KEY (review_id) REFERENCES book_reviews(id) ON DELETE CASCADE,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                )
                """);
        }
    }

    private static void migrateReadingProgressReadSeconds(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE reading_progress ADD COLUMN accumulated_read_seconds INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            if (!isDuplicateColumnError(e)) throw e;
        }
    }

    private static void migrateBookReviewsStudentColumns(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE book_reviews ADD COLUMN is_anonymous INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            if (!isDuplicateColumnError(e)) throw e;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE book_reviews ADD COLUMN helpful_votes INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            if (!isDuplicateColumnError(e)) throw e;
        }
    }

    private static void migrateBookReviewsUniqueReviewer(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_book_reviews_book_reviewer ON book_reviews(book_id, reviewer_user_id)");
        } catch (SQLException e) {
            // Older databases may contain duplicate (book_id, reviewer_user_id) rows; skip index in that case.
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase(Locale.ROOT).contains("unique")) {
                return;
            }
            throw e;
        }
    }

    private static void migrateBookReviewsTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE book_reviews ADD COLUMN author_reply_text TEXT");
        } catch (SQLException e) {
            if (!isDuplicateColumnError(e)) throw e;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE book_reviews ADD COLUMN author_reply_at TEXT");
        } catch (SQLException e) {
            if (!isDuplicateColumnError(e)) throw e;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE book_reviews ADD COLUMN flagged_by_author_at TEXT");
        } catch (SQLException e) {
            if (!isDuplicateColumnError(e)) throw e;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE book_reviews ADD COLUMN sentiment_label TEXT");
        } catch (SQLException e) {
            if (!isDuplicateColumnError(e)) throw e;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE book_reviews ADD COLUMN sentiment_source TEXT");
        } catch (SQLException e) {
            if (!isDuplicateColumnError(e)) throw e;
        }
    }

    /** SQLite/JDBC duplicate-column wording varies; ignore during additive migrations. */
    private static boolean isDuplicateColumnError(SQLException e) {
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        String lower = msg.toLowerCase(Locale.ROOT);
        return lower.contains("duplicate") && lower.contains("column");
    }

    private static void migrateReadingHighlightsTable(Connection conn) throws SQLException {
        // Older DBs may have reading_highlights without the geometry column.
        // Geometry is stored as JSON array of normalized rects.
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE reading_highlights ADD COLUMN highlight_rects_json TEXT");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null) return;
            // Ignore duplicate-column failures.
            if (msg.toLowerCase().contains("duplicate")) return;
        }
    }

    private static void migrateBorrowsTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE borrows ADD COLUMN due_at TEXT");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("duplicate column")) throw e;
        }
    }

    private static void migratePendingBooksTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE pending_books ADD COLUMN rejection_reason TEXT");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("duplicate column")) throw e;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE pending_books ADD COLUMN cover_path TEXT");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("duplicate column")) throw e;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE pending_books ADD COLUMN original_book_id INTEGER");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("duplicate column")) throw e;
        }
    }

    private static void migratePublishDraftsTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE publish_drafts ADD COLUMN cover_path TEXT");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("duplicate column")) throw e;
        }
    }

    private static void migrateBooksTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE books ADD COLUMN cover_image_path TEXT");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("duplicate column")) throw e;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE books ADD COLUMN is_visible INTEGER NOT NULL DEFAULT 1");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("duplicate column")) throw e;
        }
    }

    private static void migrateUsersTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE users ADD COLUMN failed_login_attempts INTEGER DEFAULT 0");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("duplicate column")) throw e;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE users ADD COLUMN locked_until TEXT");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("duplicate column")) throw e;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE users ADD COLUMN bio TEXT");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("duplicate column")) throw e;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE users ADD COLUMN employee_id TEXT");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("duplicate column")) throw e;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE users ADD COLUMN avatar_path TEXT");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("duplicate column")) throw e;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE users ADD COLUMN is_active INTEGER DEFAULT 1");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("duplicate column")) throw e;
        }
        // Backfill: pre-existing rows get NULL from ALTER TABLE; treat them as active.
        try (Statement st = conn.createStatement()) {
            st.execute("UPDATE users SET is_active = 1 WHERE is_active IS NULL");
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE users ADD COLUMN last_login TEXT");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("duplicate column")) throw e;
        }
    }

    private static void migrateBookRequestsTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE book_requests ADD COLUMN is_urgent INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            if (!isDuplicateColumnError(e)) throw e;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE book_requests ADD COLUMN urgent_reason TEXT");
        } catch (SQLException e) {
            if (!isDuplicateColumnError(e)) throw e;
        }
    }

    /**
     * Returns the absolute path to the database file (same path used by getConnection()).
     * Used by ResetDatabase so it deletes the correct file.
     */
    public static java.nio.file.Path getDatabasePath() {
        return Paths.get(DB_DIR).resolve(DB_FILE).toAbsolutePath();
    }

    /** For tests or shutdown: close the connection. */
    public static void close() throws SQLException {
        synchronized (Database.class) {
            if (connection != null) {
                connection.close();
                connection = null;
            }
        }
    }
}
