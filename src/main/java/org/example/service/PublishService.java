package org.example.service;

import org.example.domain.PendingBook;
import org.example.domain.User;
import org.example.db.PendingDao;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for validating and submitting book publish requests (author flow).
 */
public final class PublishService {

    private static final Logger LOG = Logger.getLogger(PublishService.class.getName());

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String UPLOAD_DIR = System.getProperty("user.home") +
            File.separator + "library_uploads" +
            File.separator + "pending";

    static {
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not create upload directory: " + UPLOAD_DIR, e);
        }
    }

    public record PublishResult(boolean success, String message, Long bookId) {
        public PublishResult(boolean success, String message) {
            this(success, message, null);
        }
    }

    /**
     * @param coverFile optional JPG/PNG cover (max ~2MB), copied under {@code data/covers/}
     */
    public static PublishResult submitBook(User author, String title, String genre,
                                           String description, File bookFile, File coverFile) {

        // Validate all inputs
        try {
            // Validate required fields
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

            // Validate file exists and is readable
            if (!bookFile.exists()) {
                return new PublishResult(false, "Selected file does not exist");
            }

            if (!bookFile.canRead()) {
                return new PublishResult(false, "Cannot read the selected file");
            }

            // Validate file extension
            String extension = getFileExtension(bookFile);
            if (!isValidFileType(extension)) {
                return new PublishResult(false,
                        "Invalid file type. Please upload PDF, TXT, or DOC/DOCX files. Got: " + extension);
            }

            //  Validate file size
            if (bookFile.length() > MAX_FILE_SIZE) {
                return new PublishResult(false,
                        "File size must be less than 10MB. Your file: " +
                                String.format("%.2f MB", bookFile.length() / (1024.0 * 1024.0)));
            }

            if (bookFile.length() == 0) {
                return new PublishResult(false, "File is empty");
            }

            // Generate unique filename to avoid conflicts
            String timestamp = String.valueOf(System.currentTimeMillis());
            String safeFileName = sanitizeFilename(bookFile.getName());
            String uniqueFileName = timestamp + "_" + safeFileName;
            Path targetPath = Paths.get(UPLOAD_DIR, uniqueFileName);

            // Copy file to upload directory
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
                LOG.log(Level.WARNING, "Failed to upload file", e);
                return new PublishResult(false,
                        "Failed to upload file: " + e.getMessage());
            }

            // Create PendingBook object
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
                if (coverFile != null && coverFile.exists() && coverFile.canRead()) {
                    String coverExtension = getFileExtension(coverFile);
                    if (coverExtension.equals("jpg") || coverExtension.equals("jpeg") || coverExtension.equals("png")) {
                        if (coverFile.length() <= 2L * 1024 * 1024) {
                            Path coversDirectory = Paths.get("data", "covers");
                            Files.createDirectories(coversDirectory);
                            String coverFileName = author.getId() + "_" + System.currentTimeMillis() + "." + coverExtension;
                            Path coverDestPath = coversDirectory.resolve(coverFileName);
                            Files.copy(coverFile.toPath(), coverDestPath, StandardCopyOption.REPLACE_EXISTING);
                            pendingBook.setCoverPath(coverDestPath.toAbsolutePath().toString());
                        }
                    }
                }
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

            // Save to database using PendingDao
            try {
                long bookId = PendingDao.insert(pendingBook);
                try {
                    org.example.db.PublishDraftDao.deleteForAuthor(author.getId());
                } catch (SQLException ignored) {
                }
                return new PublishResult(true,
                        "Book submitted successfully! Waiting for librarian approval.",
                        bookId);
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Pending book insert failed", e);
                try {
                    Files.deleteIfExists(targetPath);
                    LOG.fine(() -> "Cleaned up orphaned file: " + targetPath);
                } catch (IOException ex) {
                    LOG.log(Level.WARNING, "Failed to delete orphaned file: " + targetPath, ex);
                }
                return new PublishResult(false, userFacingSqlMessage(e));
            }

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Unexpected error in submitBook", e);
            return new PublishResult(false, "Unexpected error: " + e.getMessage());
        }
    }

    private static String userFacingSqlMessage(SQLException e) {
        String errorMsg = "Database error";
        String msg = e.getMessage();
        if (msg != null) {
            if (msg.contains("no such table")) {
                errorMsg = "Database not initialized properly. Please restart the application.";
            } else if (msg.contains("FOREIGN KEY")) {
                errorMsg = "Invalid author reference. Please try logging in again.";
            } else if (msg.contains("constraint")) {
                errorMsg = "Data validation error in database.";
            } else {
                errorMsg = "Database error: " + msg;
            }
        }
        return errorMsg;
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