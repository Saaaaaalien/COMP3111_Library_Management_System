package org.example.db;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite database connection and schema initialization.
 * DB file is stored under user directory: data/library.db
 */
public final class Database {

    private static final String DB_DIR = "data";
    private static final String DB_FILE = "library.db";
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
            migratePublishDraftsTable(conn);
            migrateBorrowsTable(conn);
            migratePendingBooksTable(conn);
            migrateBooksTable(conn);
            migrateBookReviewsTable(conn);
        }
    }

    private static void migrateBookReviewsTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE book_reviews ADD COLUMN author_reply_text TEXT");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("duplicate column")) throw e;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE book_reviews ADD COLUMN author_reply_at TEXT");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("duplicate column")) throw e;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE book_reviews ADD COLUMN flagged_by_author_at TEXT");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("duplicate column")) throw e;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE book_reviews ADD COLUMN sentiment_label TEXT");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("duplicate column")) throw e;
        }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE book_reviews ADD COLUMN sentiment_source TEXT");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("duplicate column")) throw e;
        }
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
