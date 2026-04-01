package org.example.service;

import org.example.db.BookDao;
import org.example.db.BorrowDao;
import org.example.domain.Book;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Removes a catalog book: notifies active borrowers, returns loans, deletes the row.
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
        BookDao.deleteById(bookId);
    }
}
