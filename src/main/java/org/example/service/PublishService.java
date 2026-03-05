package org.example.service;

import org.example.domain.PendingBook;
import org.example.domain.User;
import org.example.util.Validators;
import org.example.util.ValidationException;
import org.example.db.PendingDao;
import org.example.db.Database;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;

/**
 * Service for validating and submitting book publish requests (author flow).
 */
public final class PublishService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String UPLOAD_DIR = System.getProperty("user.home") +
            File.separator + "library_uploads" +
            File.separator + "pending";

    static {
        // Create upload directory if it doesn't exist
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public static class PublishResult {
        private final boolean success;
        private final String message;
        private final Long bookId;

        public PublishResult(boolean success, String message) {
            this(success, message, null);
        }

        public PublishResult(boolean success, String message, Long bookId) {
            this.success = success;
            this.message = message;
            this.bookId = bookId;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Long getBookId() { return bookId; }
    }

    /**
     * Validates and submits a book for publication
     * @return PublishResult with success status and message
     */
    public static PublishResult submitBook(User author, String title, String genre,
                                           String description, File bookFile) {

        // Validate all inputs
        try {
            // Step 1: Validate required fields
            if (title == null || title.trim().isEmpty()) {
                return new PublishResult(false, "Book title is required");
            }

            if (genre == null || genre.trim().isEmpty()) {
                return new PublishResult(false, "Genre is required");
            }

            if (description == null || description.trim().isEmpty()) {
                return new PublishResult(false, "Description is required");
            }

            if (author == null) {
                return new PublishResult(false, "Author information is missing");
            }

            if (author.getId() <= 0) {
                return new PublishResult(false, "Invalid author ID");
            }

            if (bookFile == null) {
                return new PublishResult(false, "Please select a book file to upload");
            }

            // Step 2: Validate file exists and is readable
            if (!bookFile.exists()) {
                return new PublishResult(false, "Selected file does not exist");
            }

            if (!bookFile.canRead()) {
                return new PublishResult(false, "Cannot read the selected file");
            }

            // Step 3: Validate file extension
            String extension = getFileExtension(bookFile);
            if (!isValidFileType(extension)) {
                return new PublishResult(false,
                        "Invalid file type. Please upload PDF, TXT, or DOC/DOCX files. Got: " + extension);
            }

            // Step 4: Validate file size
            if (bookFile.length() > MAX_FILE_SIZE) {
                return new PublishResult(false,
                        "File size must be less than 10MB. Your file: " +
                                String.format("%.2f MB", bookFile.length() / (1024.0 * 1024.0)));
            }

            if (bookFile.length() == 0) {
                return new PublishResult(false, "File is empty");
            }

            // Step 5: Generate unique filename to avoid conflicts
            String timestamp = String.valueOf(System.currentTimeMillis());
            String safeFileName = sanitizeFilename(bookFile.getName());
            String uniqueFileName = timestamp + "_" + safeFileName;
            Path targetPath = Paths.get(UPLOAD_DIR, uniqueFileName);

            // Step 6: Copy file to upload directory
            try {
                Files.copy(bookFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

                // Verify file was copied successfully
                if (!Files.exists(targetPath)) {
                    throw new IOException("File copy failed - target file doesn't exist");
                }

                if (Files.size(targetPath) != bookFile.length()) {
                    throw new IOException("File copy verification failed - size mismatch");
                }
            } catch (IOException e) {
                e.printStackTrace();
                return new PublishResult(false,
                        "Failed to upload file: " + e.getMessage());
            }

            // Step 7: Create PendingBook object
            PendingBook pendingBook;
            try {
                pendingBook = new PendingBook(
                        title.trim(),
                        author.getId(),
                        author.getFullName(),
                        genre.trim(),
                        description.trim(),
                        uniqueFileName,
                        targetPath.toString(),
                        bookFile.length(),
                        extension
                );
            } catch (Exception e) {
                // Clean up the file if object creation fails
                try {
                    Files.deleteIfExists(targetPath);
                } catch (IOException ex) {
                    // ignore
                }
                return new PublishResult(false,
                        "Error creating book record: " + e.getMessage());
            }

            // Step 8: Save to database using PendingDao
            try {
                long bookId = PendingDao.insert(pendingBook);
                return new PublishResult(true,
                        "Book submitted successfully! Waiting for librarian approval.",
                        bookId);
            } catch (SQLException e) {
                e.printStackTrace();
                // Try to clean up the uploaded file if database insert fails
                try {
                    Files.deleteIfExists(targetPath);
                    System.out.println("Cleaned up orphaned file: " + targetPath);
                } catch (IOException ex) {
                    System.err.println("Failed to delete orphaned file: " + targetPath);
                }

                // Provide more specific error message
                String errorMsg = "Database error";
                if (e.getMessage() != null) {
                    if (e.getMessage().contains("no such table")) {
                        errorMsg = "Database not initialized properly. Please restart the application.";
                    } else if (e.getMessage().contains("FOREIGN KEY")) {
                        errorMsg = "Invalid author reference. Please try logging in again.";
                    } else if (e.getMessage().contains("constraint")) {
                        errorMsg = "Data validation error in database.";
                    } else {
                        errorMsg = "Database error: " + e.getMessage();
                    }
                }
                return new PublishResult(false, errorMsg);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return new PublishResult(false, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Overloaded method with default genre and empty description
     */
    public static PublishResult submitBook(User author, String title, File bookFile) {
        return submitBook(author, title, "Fiction", "", bookFile);
    }

    /**
     * Overloaded method with default genre
     */
    public static PublishResult submitBook(User author, String title, String description, File bookFile) {
        return submitBook(author, title, "Fiction", description, bookFile);
    }

    private static String getFileExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            return name.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    private static boolean isValidFileType(String extension) {
        return extension.equals("pdf") ||
                extension.equals("txt") ||
                extension.equals("doc") ||
                extension.equals("docx");
    }

    private static String sanitizeFilename(String filename) {
        // Remove any path information and replace unsafe characters
        String safeName = new File(filename).getName();
        return safeName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}