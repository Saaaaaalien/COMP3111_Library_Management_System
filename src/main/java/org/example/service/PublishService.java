package org.example.service;

import org.example.domain.User;
import org.example.util.Validators;
import org.example.util.ValidationException;

import java.io.File;

/**
 * Service for validating and submitting book publish requests (author flow).
 * Phase 2/3 placeholder: persistence wiring to PendingBook/PendingDao will be added later.
 */
public final class PublishService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    public static class PublishResult {
        private final boolean success;
        private final String message;

        public PublishResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    /**
     * Validates and submits a book for publication
     * @return PublishResult with success status and message
     */
    public static PublishResult submitBook(User author, String title, String genre,
                                           String description, File bookFile) {

        // Validate all inputs
        try {
            Validators.validateRequired(title, "Book title");
            Validators.validateRequired(genre, "Genre");
            Validators.validateRequired(description, "Description");

            if (bookFile == null) {
                return new PublishResult(false, "Please select a book file to upload");
            }

            // Validate file extension
            String extension = getFileExtension(bookFile);
            if (!isValidFileType(extension)) {
                return new PublishResult(false,
                        "Invalid file type. Please upload PDF, TXT, or DOC/DOCX files.");
            }

            // Validate file size
            if (bookFile.length() > MAX_FILE_SIZE) {
                return new PublishResult(false,
                        "File size must be less than 10MB");
            }

            // TODO: Wire up real PendingBook + PendingDao in Phase 2/3
            // For now we accept the submission logically without persistence.
            return new PublishResult(true,
                    "Book submitted successfully! (Storage for author submissions will be implemented in Phase 2/3.)");
        } catch (ValidationException e) {
            return new PublishResult(false, e.getMessage());
        }
    }

    // Phase 2/3: methods for pending lists and reviews will be added here.

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
}