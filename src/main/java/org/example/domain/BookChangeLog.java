package org.example.domain;

import java.time.LocalDateTime;

/**
 * Represents a change log entry for a book, tracking modifications to book details.
 * Used for version history and audit trails.
 */
public class BookChangeLog {
    private final long id;
    private final long bookId;
    private final long librarianUserId;
    private final String librarianName;
    private final String changeType; // e.g., "EDIT", "BULK_EDIT", "DELETE", "RESTORE"
    private final String fieldName; // which field was changed (e.g., "title", "genre", "availability")
    private final String oldValue;
    private final String newValue;
    private final LocalDateTime changeDate;
    private final String description; // optional additional context

    public BookChangeLog(long id, long bookId, long librarianUserId, String librarianName,
                         String changeType, String fieldName, String oldValue, String newValue,
                         LocalDateTime changeDate, String description) {
        this.id = id;
        this.bookId = bookId;
        this.librarianUserId = librarianUserId;
        this.librarianName = librarianName;
        this.changeType = changeType;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changeDate = changeDate;
        this.description = description;
    }

    public long getId() { return id; }
    public long getBookId() { return bookId; }
    public long getLibrarianUserId() { return librarianUserId; }
    public String getLibrarianName() { return librarianName; }
    public String getChangeType() { return changeType; }
    public String getFieldName() { return fieldName; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public LocalDateTime getChangeDate() { return changeDate; }
    public String getDescription() { return description; }
}
