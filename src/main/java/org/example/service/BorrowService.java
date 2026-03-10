package org.example.service;

import org.example.db.BookDao;
import org.example.db.BorrowDao;
import org.example.domain.Availability;
import org.example.domain.Book;
import org.example.domain.Borrow;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Borrow a book: checks availability and records the borrow in a transaction.
 * <p>
 * Uses a single database connection with manual commit/rollback so that updating
 * book availability and inserting the borrow record succeed or fail together.
 * Enforces a maximum number of active borrows per user.
 */
public final class BorrowService {

    /** Maximum number of books a user may have borrowed at one time. */
    public static final int MAX_ACTIVE_BORROWS = 5;

    /** Default borrow duration in days (used for due date). */
    public static final int BORROW_DURATION_DAYS = 14;

    private BorrowService() {}

    /**
     * Borrows a book for the given user. Fails if the book is not found, not available, or user is at borrow limit.
     * On success, the book's availability is set to {@link org.example.domain.Availability#BORROWED}
     * and a borrow record is inserted with a due date {@value #BORROW_DURATION_DAYS} days from now.
     *
     * @param bookId        the book to borrow
     * @param borrowerUserId the user id of the borrower (Student/Staff)
     * @return the created borrow record (includes due date)
     * @throws SQLException    if a database error occurs
     * @throws BorrowException if the book is not found, not available, or user at borrow limit
     */
    public static Borrow borrow(long bookId, long borrowerUserId) throws SQLException, BorrowException {
        Connection conn = org.example.db.Database.getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            int activeCount = BorrowDao.countActiveByBorrowerUserId(borrowerUserId);
            if (activeCount >= MAX_ACTIVE_BORROWS) {
                throw new BorrowException("You have reached the maximum number of active borrows ("
                        + MAX_ACTIVE_BORROWS + "). Please return a book before borrowing another.");
            }
            var bookOpt = BookDao.findById(bookId);
            if (bookOpt.isEmpty()) {
                throw new BorrowException("Book not found.");
            }
            Book book = bookOpt.get();
            if (book.getAvailability() != Availability.AVAILABLE) {
                throw new BorrowException("This book is no longer available.");
            }
            BookDao.updateAvailability(bookId, Availability.BORROWED);
            Instant now = Instant.now();
            String borrowedAt = now.toString();
            String dueAt = now.plus(BORROW_DURATION_DAYS, ChronoUnit.DAYS).toString();
            long borrowId = BorrowDao.insert(bookId, borrowerUserId, borrowedAt, dueAt);
            conn.commit();
            var created = BorrowDao.findById(borrowId);
            return created.orElseThrow(() -> new SQLException("Borrow inserted but could not be read back"));
        } catch (BorrowException | SQLException e) {
            rollback(conn);
            throw e;
        } finally {
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException ignored) {
            }
        }
    }

    /**
     * Returns a book (marks the borrow as returned and sets book availability to AVAILABLE).
     * Fails if the borrow does not exist, does not belong to the user, or is already returned.
     *
     * @throws SQLException    if database error
     * @throws BorrowException if borrow not found, not owned by user, or already returned
     */
    public static void returnBook(long borrowId, long borrowerUserId) throws SQLException, BorrowException {
        Connection conn = org.example.db.Database.getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            var borrowOpt = BorrowDao.findById(borrowId);
            if (borrowOpt.isEmpty()) {
                throw new BorrowException("Borrow record not found.");
            }
            Borrow borrow = borrowOpt.get();
            if (borrow.getBorrowerUserId() != borrowerUserId) {
                throw new BorrowException("You cannot return this book.");
            }
            if (borrow.getReturnedAt() != null && !borrow.getReturnedAt().isEmpty()) {
                throw new BorrowException("This book has already been returned.");
            }
            BorrowDao.updateReturnedAt(borrowId, Instant.now().toString());
            BookDao.updateAvailability(borrow.getBookId(), Availability.AVAILABLE);
            conn.commit();
        } catch (BorrowException | SQLException e) {
            rollback(conn);
            throw e;
        } finally {
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException ignored) {
            }
        }
    }

    /** Rolls back the current transaction on the given connection; ignores rollback errors. */
    private static void rollback(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
        }
    }

    /** Exception thrown when a borrow cannot be completed (e.g. book not found or not available). */
    public static class BorrowException extends Exception {
        public BorrowException(String message) {
            super(message);
        }
    }
}
