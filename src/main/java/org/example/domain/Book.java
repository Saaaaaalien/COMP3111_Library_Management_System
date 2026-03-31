package org.example.domain;

/**
 * Published book (approved by librarian).
 */
public class Book {

    private final long id;
    private final String title;
    private final long authorUserId;
    private final String authorFullNameSnapshot;
    private final String genre;
    private final String summary;
    private final String filePath;
    private final String publishDate;
    private final Availability availability;
    /** Optional path to cover image (JPG/PNG). */
    private final String coverImagePath;

    public Book(long id, String title, long authorUserId, String authorFullNameSnapshot,
                String genre, String summary, String filePath, String publishDate,
                Availability availability) {
        this(id, title, authorUserId, authorFullNameSnapshot, genre, summary, filePath, publishDate, availability, null);
    }

    public Book(long id, String title, long authorUserId, String authorFullNameSnapshot,
                String genre, String summary, String filePath, String publishDate,
                Availability availability, String coverImagePath) {
        this.id = id;
        this.title = title;
        this.authorUserId = authorUserId;
        this.authorFullNameSnapshot = authorFullNameSnapshot;
        this.genre = genre;
        this.summary = summary;
        this.filePath = filePath;
        this.publishDate = publishDate;
        this.availability = availability;
        this.coverImagePath = coverImagePath;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public long getAuthorUserId() { return authorUserId; }
    public String getAuthorFullNameSnapshot() { return authorFullNameSnapshot; }
    public String getGenre() { return genre; }
    public String getSummary() { return summary; }
    public String getFilePath() { return filePath; }
    public String getPublishDate() { return publishDate; }
    public Availability getAvailability() { return availability; }
    public String getCoverImagePath() { return coverImagePath; }
}
