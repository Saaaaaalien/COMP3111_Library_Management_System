package org.example.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resets the database by deleting the library.db file.
 * Run this class's main method to clear all saved data (users, books, borrows, etc.).
 * On the next run of the main application, a fresh empty database will be created.
 *
 * Run from IntelliJ: right-click this file → Run 'ResetDatabase.main()'
 *
 * If reset doesn't clear data: Main and ResetDatabase may be using different working
 * directories. This class tries several locations. Set both run configs to the same
 * "Working directory" (e.g. $MODULE_WORKING_DIR$) in Run → Edit Configurations.
 */
public final class ResetDatabase {

    public static void main(String[] args) {
        boolean deleted = false;
        String tried = "";

        Path primary = Database.getDatabasePath();
        Path[] toTry = {
            primary,
            Paths.get(System.getProperty("user.dir")).resolve("data").resolve("library.db"),
            Paths.get(System.getProperty("user.dir")).getParent() != null
                ? Paths.get(System.getProperty("user.dir")).getParent().resolve("data").resolve("library.db")
                : null
        };

        for (Path p : toTry) {
            if (p == null) continue;
            tried += "\n  " + p.toAbsolutePath();
            try {
                if (Files.exists(p) && Files.isRegularFile(p)) {
                    Files.delete(p);
                    System.out.println("Database deleted: " + p.toAbsolutePath());
                    System.out.println("All saved data (users, books, borrows) has been cleared.");
                    deleted = true;
                    break;
                }
            } catch (Exception e) {
                System.err.println("Could not delete " + p.toAbsolutePath() + ": " + e.getMessage());
            }
        }

        if (!deleted) {
            System.out.println("No database file found. Checked:" + tried);
            System.out.println("Current working directory: " + System.getProperty("user.dir"));
            System.out.println("If the app still shows old data, set Main and ResetDatabase to the same Working directory in Run → Edit Configurations.");
        }
    }
}
