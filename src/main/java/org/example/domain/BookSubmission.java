package org.example.domain;

/**
 * Book submission from an author, awaiting librarian approval.
 */
public class BookSubmission {

    private final long id;
    private final String title;
    private final long authorUserId;
    private final String genre;
    private final String description;
    private final String filePath;
    private final String submittedAt;
    private final SubmissionStatus status;
    private final String decisionAt;

    public BookSubmission(long id, String title, long authorUserId, String genre,
                          String description, String filePath, String submittedAt,
                          SubmissionStatus status, String decisionAt) {
        this.id = id;
        this.title = title;
        this.authorUserId = authorUserId;
        this.genre = genre;
        this.description = description;
        this.filePath = filePath;
        this.submittedAt = submittedAt;
        this.status = status;
        this.decisionAt = decisionAt;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public long getAuthorUserId() { return authorUserId; }
    public String getGenre() { return genre; }
    public String getDescription() { return description; }
    public String getFilePath() { return filePath; }
    public String getSubmittedAt() { return submittedAt; }
    public SubmissionStatus getStatus() { return status; }
    public String getDecisionAt() { return decisionAt; }
}
