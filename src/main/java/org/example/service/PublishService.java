package org.example.services;

import org.example.domain.User;
import org.example.domain.PendingBook;
import org.example.db.PendingDao;
import org.example.util.Validators;
import org.example.util.ValidationException;

import java.io.File;

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

            // Create book object
            PendingBook book = new PendingBook();
            book.setTitle(title);
            book.setAuthorId(author.getId());
            book.setAuthorName(author.getFullName());
            book.setGenre(genre);
            book.setDescription(description);
            book.setFileName(bookFile.getName());
            book.setFileSize(bookFile.length());
            book.setFileType(extension);
            book.setStatus("PENDING");

            // Save to database
            if (PendingDao.saveBookRequest(book, bookFile)) {
                return new PublishResult(true,
                        "Book submitted successfully! Waiting for librarian approval.");
            } else {
                return new PublishResult(false,
                        "Failed to submit book. Please try again.");
            }

        } catch (ValidationException e) {
            return new PublishResult(false, e.getMessage());
        }
    }

    /**
     * Get all books pending approval
     */
    public static List<PeningBook> getPendingBooks() {
        return PeningDao.getPendingBooks();
    }

    /**
     * Get books by author
     */
    public static List<PendingBook> getBooksByAuthor(int authorId) {
        return PendingDao.getBooksByAuthor(authorId);
    }

    /**
     * Approve or reject a book
     */
    public static boolean reviewBook(int bookId, boolean approve, String reviewNotes) {
        String status = approve ? "APPROVED" : "REJECTED";
        return PendingDao.updateBookStatus(bookId, status, reviewNotes);
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
}