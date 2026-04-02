package org.example.service;

import org.example.db.BookDao;
import org.example.db.BorrowDao;
import org.example.db.PendingDao;
import org.example.domain.Book;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Removes a catalog book: notifies active borrowers, returns loans, and hides the catalog entry
 * while keeping borrow history for student/staff and librarian records.
 */
public final class BookRemovalService {

    private BookRemovalService() {}

    public static void removePublishedBook(long bookId) throws SQLException {
        Optional<Book> bookOpt = BookDao.findById(bookId);
        if (bookOpt.isEmpty()) {
            return;
        }
        Book book = bookOpt.get();
        String title = book.getTitle();
        try {
            NotificationService.notifyAuthorBookRemovedByLibrarian(book.getAuthorUserId(), title);
        } catch (SQLException ignored) {
            // non-fatal
        }
        List<long[]> pairs = new ArrayList<>(BorrowDao.findActiveBorrowerPairsForBook(bookId));
        for (long[] pair : pairs) {
            long borrowId = pair[0];
            long borrowerId = pair[1];
            NotificationService.notifyBorrowerBookRemovedFromCatalog(borrowerId, title);
            BorrowService.returnBorrowAsSystem(borrowId);
        }
        // Prevent librarian from approving/rejecting stale edit submissions for a book
        // that is no longer part of the catalog.
        PendingDao.deleteByOriginalBookId(bookId);
        // Also delete unlinked approved/rejected submissions (legacy rows with original_book_id = 0/NULL)
        // so a librarian cannot "approve again" and recreate the catalog entry.
        PendingDao.deleteUnlinkedApprovedOrRejectedForAuthorTitle(book.getAuthorUserId(), title);
        // Soft-remove so that borrow history rows remain joinable / displayable.
        BookDao.removeFromCatalogButKeepHistory(bookId);
    }
}
