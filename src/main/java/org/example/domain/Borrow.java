package org.example.domain;

/**
 * Borrow record: a user borrowing a book.
 */
public class Borrow {

    private final long id;
    private final long bookId;
    private final long borrowerUserId;
    private final String borrowedAt;
    private final String returnedAt;

    public Borrow(long id, long bookId, long borrowerUserId, String borrowedAt, String returnedAt) {
        this.id = id;
        this.bookId = bookId;
        this.borrowerUserId = borrowerUserId;
        this.borrowedAt = borrowedAt;
        this.returnedAt = returnedAt;
    }

    public long getId() { return id; }
    public long getBookId() { return bookId; }
    public long getBorrowerUserId() { return borrowerUserId; }
    public String getBorrowedAt() { return borrowedAt; }
    public String getReturnedAt() { return returnedAt; }
}
