package org.example.service;

import org.example.db.BookDao;
import org.example.db.BorrowDao;
import org.example.db.ReadingHighlightDao;
import org.example.db.ReadingProgressDao;
import org.example.domain.Availability;
import org.example.domain.Book;
import org.example.domain.Borrow;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

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
            Borrow b = created.orElseThrow(() -> new SQLException("Borrow inserted but could not be read back"));
            try {
                NotificationService.notifyBorrowSuccess(borrowerUserId, book.getTitle(), b.getDueAt());
            } catch (SQLException ignored) {
            }
            try {
                NotificationService.notifyLibrariansBorrowActivity(b.getId(), borrowerUserId, book.getTitle(), b.getDueAt());
            } catch (SQLException ignored) {
            }
            return b;
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
            // Clear any progress/highlights best-effort. Availability update must stay committed.
            try {
                clearReadingForBorrow(borrowId);
                conn.commit();
            } catch (SQLException ignored) {
                try {
                    conn.rollback();
                } catch (SQLException ignored2) {
                    // ignore
                }
            }
            try {
                BookDao.findById(borrow.getBookId())
                        .ifPresent(book -> {
                            try {
                                NotificationService.notifyReturnSuccess(borrowerUserId, book.getTitle(), false);
                                NotificationService.notifyLibrariansReturnActivity(borrow.getId(), borrowerUserId, book.getTitle(), false);
                            } catch (SQLException ignored) {
                            }
                        });
            } catch (SQLException ignored) {
            }
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
     * Marks a borrow returned and frees the book without borrower check (auto-return on due date / admin).
     */
    public static void returnBorrowAsSystem(long borrowId) throws SQLException {
        Connection conn = org.example.db.Database.getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            var borrowOpt = BorrowDao.findById(borrowId);
            if (borrowOpt.isEmpty()) {
                conn.commit();
                return;
            }
            Borrow borrow = borrowOpt.get();
            if (borrow.getReturnedAt() != null && !borrow.getReturnedAt().isEmpty()) {
                conn.commit();
                return;
            }
            BorrowDao.updateReturnedAt(borrowId, Instant.now().toString());
            BookDao.updateAvailability(borrow.getBookId(), Availability.AVAILABLE);
            conn.commit();
            // Clear any progress/highlights best-effort. Availability update must stay committed.
            try {
                clearReadingForBorrow(borrowId);
                conn.commit();
            } catch (SQLException ignored) {
                try {
                    conn.rollback();
                } catch (SQLException ignored2) {
                    // ignore
                }
            }
            try {
                BookDao.findById(borrow.getBookId())
                        .ifPresent(book -> {
                            try {
                                NotificationService.notifyReturnSuccess(borrow.getBorrowerUserId(), book.getTitle(), true);
                                NotificationService.notifyLibrariansReturnActivity(borrow.getId(), borrow.getBorrowerUserId(), book.getTitle(), true);
                            } catch (SQLException ignored) {
                            }
                        });
            } catch (SQLException ignored) {
            }
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

    /**
     * Auto-returns all active borrows past {@code due_at}.
     *
     * @return number of borrows closed
     */
    public static int processDueReturns() throws SQLException {
        String now = Instant.now().toString();
        List<Long> ids = new ArrayList<>(BorrowDao.findOverdueActiveBorrowIds(now));
        int count = 0;
        for (Long id : ids) {
            try {
                returnBorrowAsSystem(id);
                count++;
            } catch (SQLException ignored) {
            }
        }
        return count;
    }

    /**
     * Borrows several available books in one transaction (fails entirely if any item fails).
     */
    public static List<Borrow> borrowMany(List<Long> bookIds, long borrowerUserId) throws SQLException, BorrowException {
        if (bookIds == null || bookIds.isEmpty()) {
            throw new BorrowException("No books selected.");
        }
        Connection conn = org.example.db.Database.getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        List<Borrow> created = new ArrayList<>();
        try {
            conn.setAutoCommit(false);
            int activeCount = BorrowDao.countActiveByBorrowerUserId(borrowerUserId);
            if (activeCount + bookIds.size() > MAX_ACTIVE_BORROWS) {
                throw new BorrowException("Borrowing these books would exceed the limit of "
                        + MAX_ACTIVE_BORROWS + " active borrows.");
            }
            Instant batchStart = Instant.now();
            for (Long bookId : bookIds) {
                var bookOpt = BookDao.findById(bookId);
                if (bookOpt.isEmpty()) {
                    throw new BorrowException("Book not found (id " + bookId + ").");
                }
                Book book = bookOpt.get();
                if (book.getAvailability() != Availability.AVAILABLE) {
                    throw new BorrowException("One or more books are no longer available: " + book.getTitle());
                }
                BookDao.updateAvailability(bookId, Availability.BORROWED);
                String borrowedAt = batchStart.toString();
                String dueAt = batchStart.plus(BORROW_DURATION_DAYS, ChronoUnit.DAYS).toString();
                long borrowId = BorrowDao.insert(bookId, borrowerUserId, borrowedAt, dueAt);
                created.add(BorrowDao.findById(borrowId).orElseThrow(() -> new SQLException("Borrow missing after insert")));
            }
            conn.commit();
            for (Borrow b : created) {
                try {
                    BookDao.findById(b.getBookId()).ifPresent(book -> {
                        try {
                            NotificationService.notifyBorrowSuccess(borrowerUserId, book.getTitle(), b.getDueAt());
                            NotificationService.notifyLibrariansBorrowActivity(b.getId(), borrowerUserId, book.getTitle(), b.getDueAt());
                        } catch (SQLException ignored) {
                        }
                    });
                } catch (SQLException ignored) {
                }
            }
            return created;
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

    private static void clearReadingForBorrow(long borrowId) throws SQLException {
        try {
            ReadingHighlightDao.deleteAllForBorrow(borrowId);
        } catch (SQLException e) {
            if (!isIgnorableReadingCleanupError(e)) {
                throw e;
            }
        }
        try {
            ReadingProgressDao.deleteForBorrow(borrowId);
        } catch (SQLException e) {
            if (!isIgnorableReadingCleanupError(e)) {
                throw e;
            }
        }
    }

    private static boolean isIgnorableReadingCleanupError(SQLException e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        String m = msg.toLowerCase();
        return m.contains("no such table")
                || m.contains("no such column")
                || m.contains("has no column");
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
