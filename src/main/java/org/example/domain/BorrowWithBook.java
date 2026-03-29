package org.example.domain;

/**
 * A borrow record with book title and author for display (e.g. "My Borrowed Books").
 */
public class BorrowWithBook {

    private final long borrowId;
    private final long bookId;
    private final String title;
    private final String author;
    private final String borrowedAt;
    private final String returnedAt;
    private final String dueAt;
    /** Path to book file on disk (for reader); may be null for legacy rows. */
    private final String filePath;

    public BorrowWithBook(long borrowId, long bookId, String title, String author,
                          String borrowedAt, String returnedAt, String dueAt, String filePath) {
        this.borrowId = borrowId;
        this.bookId = bookId;
        this.title = title;
        this.author = author;
        this.borrowedAt = borrowedAt;
        this.returnedAt = returnedAt;
        this.dueAt = dueAt;
        this.filePath = filePath;
    }

    public long getBorrowId() { return borrowId; }
    public long getBookId() { return bookId; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getBorrowedAt() { return borrowedAt; }
    public String getReturnedAt() { return returnedAt; }
    public String getDueAt() { return dueAt; }
    public String getFilePath() { return filePath; }

    /** True if this borrow is still active (not yet returned). */
    public boolean isActive() {
        return returnedAt == null || returnedAt.isEmpty();
    }
}
