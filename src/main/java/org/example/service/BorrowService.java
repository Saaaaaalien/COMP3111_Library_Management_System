package org.example.service;

import org.example.db.BookDao;
import org.example.db.BorrowDao;
import org.example.domain.Availability;
import org.example.domain.Book;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Borrow a book: checks availability and records the borrow in a transaction.
 */
public final class BorrowService {

    private BorrowService() {}

    /**
     * Borrows a book for the user. Fails if the book is not available.
     *
     * @throws SQLException        if database error
     * @throws BorrowException     if book not found or not available
     */
    public static void borrow(long bookId, long borrowerUserId) throws SQLException, BorrowException {
        Connection conn = org.example.db.Database.getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            var bookOpt = BookDao.findById(bookId);
            if (bookOpt.isEmpty()) {
                throw new BorrowException("Book not found.");
            }
            Book book = bookOpt.get();
            if (book.getAvailability() != Availability.AVAILABLE) {
                throw new BorrowException("This book is no longer available.");
            }
            BookDao.updateAvailability(bookId, Availability.BORROWED);
            BorrowDao.insert(bookId, borrowerUserId, Instant.now().toString());
            conn.commit();
        } catch (BorrowException e) {
            rollback(conn);
            throw e;
        } catch (SQLException e) {
            rollback(conn);
            throw e;
        } finally {
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException ignored) {
            }
        }
    }

    private static void rollback(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
        }
    }

    public static class BorrowException extends Exception {
        public BorrowException(String message) {
            super(message);
        }
    }
}
