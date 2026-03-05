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
                        dataDir.toFile().mkdirs();
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
                    employee_id TEXT
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS book_submissions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    author_user_id INTEGER NOT NULL,
                    genre TEXT NOT NULL,
                    description TEXT NOT NULL,
                    file_path TEXT NOT NULL,
                    submitted_at TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'PENDING',
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
                    FOREIGN KEY (author_user_id) REFERENCES users(id)
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS borrows (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    book_id INTEGER NOT NULL,
                    borrower_user_id INTEGER NOT NULL,
                    borrowed_at TEXT NOT NULL,
                    returned_at TEXT,
                    FOREIGN KEY (book_id) REFERENCES books(id),
                    FOREIGN KEY (borrower_user_id) REFERENCES users(id)
                )
                """);
        }
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
