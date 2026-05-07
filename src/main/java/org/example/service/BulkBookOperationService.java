package org.example.service;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.db.BookChangeLogDao;
import org.example.db.BookDao;
import org.example.db.BorrowDao;
import org.example.domain.Availability;
import org.example.domain.Book;
import org.example.domain.User;

/**
 * Service for bulk book operations and managing version history.
 * Handles coordinated updates to multiple books and tracks all changes via BookChangeLogDao.
 */
public final class BulkBookOperationService {

    private BulkBookOperationService() {}

    /**
     * Bulk update availability status for multiple books.
     * Records each change in the version history log.
     */
    public static void bulkUpdateAvailability(List<Long> bookIds, Availability newAvailability, User librarian) throws SQLException {
        for (long bookId : bookIds) {
            Optional<Book> existing = BookDao.findById(bookId);
            if (existing.isPresent()) {
                Book book = existing.get();
                String oldStatus = book.getAvailability().name();
                String newStatus = newAvailability.name();

                // Keep borrow records consistent when librarians force-available a borrowed title.
                if (book.getAvailability() == Availability.BORROWED && newAvailability == Availability.AVAILABLE) {
                    Optional<org.example.domain.Borrow> activeBorrow = BorrowDao.findActiveByBookId(bookId);
                    if (activeBorrow.isPresent()) {
                        org.example.domain.Borrow borrow = activeBorrow.get();
                        BorrowDao.updateReturnedAt(borrow.getId(), Instant.now().toString());
                        NotificationService.notifyReturnByLibrarian(
                                borrow.getBorrowerUserId(),
                                book.getTitle(),
                                borrow.getId()
                        );
                    }
                }

                BookDao.updateAvailability(bookId, newAvailability);
                BookChangeLogDao.recordChange(
                    bookId,
                    librarian.getId(),
                    librarian.getFullName(),
                    "BULK_EDIT",
                    "availability",
                    oldStatus,
                    newStatus,
                    "Bulk availability update by librarian"
                );
            }
        }
    }

    /**
     * Bulk delete multiple books from the catalog.
     * Records deletion in version history and uses BookRemovalService for cleanup.
     */
    public static void bulkDeleteBooks(List<Long> bookIds, User librarian) throws SQLException {
        for (long bookId : bookIds) {
            Optional<Book> existing = BookDao.findById(bookId);
            if (existing.isPresent()) {
                Book book = existing.get();
                
                BookChangeLogDao.recordChange(
                    bookId,
                    librarian.getId(),
                    librarian.getFullName(),
                    "DELETE",
                    null,
                    book.getTitle(),
                    null,
                    "Book deleted by librarian: " + librarian.getFullName()
                );
                
                BookRemovalService.removePublishedBook(bookId);
            }
        }
    }

    /**
     * Update a single book's details and record the change in version history.
     * Only records changes if values actually differ.
     */
    public static void updateBookWithHistory(long bookId, String newTitle, String newGenre, 
                                            String newSummary, User librarian) throws SQLException {
        Optional<Book> existing = BookDao.findById(bookId);
        if (existing.isEmpty()) {
            throw new SQLException("Book not found: " + bookId);
        }
        
        Book book = existing.get();
        
        // Record title change
        if (newTitle != null && !newTitle.equals(book.getTitle())) {
            BookChangeLogDao.recordChange(
                bookId,
                librarian.getId(),
                librarian.getFullName(),
                "EDIT",
                "title",
                book.getTitle(),
                newTitle,
                null
            );
        }
        
        // Record genre change
        if (newGenre != null && !newGenre.equals(book.getGenre())) {
            BookChangeLogDao.recordChange(
                bookId,
                librarian.getId(),
                librarian.getFullName(),
                "EDIT",
                "genre",
                book.getGenre(),
                newGenre,
                null
            );
        }
        
        // Record summary change
        if (newSummary != null && !newSummary.equals(book.getSummary())) {
            BookChangeLogDao.recordChange(
                bookId,
                librarian.getId(),
                librarian.getFullName(),
                "EDIT",
                "summary",
                book.getSummary(),
                newSummary,
                null
            );
        }
        
        // Perform the actual update
        BookDao.update(bookId, newTitle, newGenre, newSummary);
    }

    /**
     * Retrieves all version history changes for a specific book.
     */
    public static List<org.example.domain.BookChangeLog> getBookChangeHistory(long bookId) throws SQLException {
        return BookChangeLogDao.findByBookId(bookId);
    }

    /**
     * Retrieves all changes made by a specific librarian.
     */
    public static List<org.example.domain.BookChangeLog> getLibrarianChangeHistory(long librarianUserId) throws SQLException {
        return BookChangeLogDao.findByLibrarian(librarianUserId);
    }

    /**
     * Filters books by genre.
     */
    public static List<Book> filterByGenre(String genre) throws SQLException {
        List<Book> allBooks = BookDao.findAll();
        List<Book> filtered = new ArrayList<>();
        for (Book book : allBooks) {
            if (book.getGenre() != null && book.getGenre().equalsIgnoreCase(genre)) {
                filtered.add(book);
            }
        }
        return filtered;
    }

    /**
     * Filters books by author name.
     */
    public static List<Book> filterByAuthor(String authorName) throws SQLException {
        List<Book> allBooks = BookDao.findAll();
        List<Book> filtered = new ArrayList<>();
        for (Book book : allBooks) {
            if (book.getAuthorFullNameSnapshot() != null && 
                book.getAuthorFullNameSnapshot().equalsIgnoreCase(authorName)) {
                filtered.add(book);
            }
        }
        return filtered;
    }

    /**
     * Filters books by availability status.
     */
    public static List<Book> filterByAvailability(Availability status) throws SQLException {
        List<Book> allBooks = BookDao.findAll();
        List<Book> filtered = new ArrayList<>();
        for (Book book : allBooks) {
            if (book.getAvailability() == status) {
                filtered.add(book);
            }
        }
        return filtered;
    }

    /**
     * Combines multiple filter conditions (genre AND author AND availability).
     */
    public static List<Book> filterBooks(String genre, String author, Availability status) throws SQLException {
        List<Book> allBooks = BookDao.findAll();
        List<Book> filtered = new ArrayList<>();
        
        for (Book book : allBooks) {
            boolean genreMatch = (genre == null || genre.isEmpty() || 
                                 (book.getGenre() != null && book.getGenre().equalsIgnoreCase(genre)));
            boolean authorMatch = (author == null || author.isEmpty() || 
                                  (book.getAuthorFullNameSnapshot() != null && 
                                   book.getAuthorFullNameSnapshot().equalsIgnoreCase(author)));
            boolean statusMatch = (status == null || book.getAvailability() == status);
            
            if (genreMatch && authorMatch && statusMatch) {
                filtered.add(book);
            }
        }
        return filtered;
    }

    /**
     * Get all unique genres from the catalog.
     */
    public static List<String> getAllGenres() throws SQLException {
        List<Book> allBooks = BookDao.findAll();
        List<String> genres = new ArrayList<>();
        for (Book book : allBooks) {
            if (book.getGenre() != null && !genres.contains(book.getGenre())) {
                genres.add(book.getGenre());
            }
        }
        genres.sort(String::compareTo);
        return genres;
    }

    /**
     * Get all unique authors from the catalog.
     */
    public static List<String> getAllAuthors() throws SQLException {
        List<Book> allBooks = BookDao.findAll();
        List<String> authors = new ArrayList<>();
        for (Book book : allBooks) {
            if (book.getAuthorFullNameSnapshot() != null && 
                !authors.contains(book.getAuthorFullNameSnapshot())) {
                authors.add(book.getAuthorFullNameSnapshot());
            }
        }
        authors.sort(String::compareTo);
        return authors;
    }
}
