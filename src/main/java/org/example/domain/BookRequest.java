package org.example.domain;

/**
 * BookRequest entity: represents a student/staff request for a new book.
 */
public class BookRequest {

    public enum RequestStatus {
        PENDING,
        DOWNLOADED,  // book file retrieved; awaiting librarian approval to publish
        APPROVED,
        REJECTED,
        PROCESSED    // legacy alias for DOWNLOADED (pre-existing rows)
    }

    private final long id;
    private final long requestedByUserId;
    private final String requestedByName;
    private final String title;
    private final String authorName;
    private final String description;
    private final String genre;
    private RequestStatus status;
    private String approvalNotes;
    private String downloadedFilePath;
    private String generatedSummary;
    private final String createdAt;
    private String processedAt;
    private boolean urgent;
    private String urgentReason;

    public BookRequest(long id, long requestedByUserId, String requestedByName,
                      String title, String authorName, String description, String genre,
                      RequestStatus status, String approvalNotes, String downloadedFilePath,
                      String generatedSummary, String createdAt, String processedAt,
                      boolean urgent, String urgentReason) {
        this.id = id;
        this.requestedByUserId = requestedByUserId;
        this.requestedByName = requestedByName;
        this.title = title;
        this.authorName = authorName;
        this.description = description;
        this.genre = genre;
        this.status = status;
        this.approvalNotes = approvalNotes;
        this.downloadedFilePath = downloadedFilePath;
        this.generatedSummary = generatedSummary;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
        this.urgent = urgent;
        this.urgentReason = urgentReason;
    }

    // Getters
    public long getId() { return id; }
    public long getRequestedByUserId() { return requestedByUserId; }
    public String getRequestedByName() { return requestedByName; }
    public String getTitle() { return title; }
    public String getAuthorName() { return authorName; }
    public String getDescription() { return description; }
    public String getGenre() { return genre; }
    public RequestStatus getStatus() { return status; }
    public String getApprovalNotes() { return approvalNotes; }
    public String getDownloadedFilePath() { return downloadedFilePath; }
    public String getGeneratedSummary() { return generatedSummary; }
    public String getCreatedAt() { return createdAt; }
    public String getProcessedAt() { return processedAt; }
    public boolean isUrgent() { return urgent; }
    public String getUrgentReason() { return urgentReason; }

    // Setters
    public void setStatus(RequestStatus status) { this.status = status; }
    public void setApprovalNotes(String approvalNotes) { this.approvalNotes = approvalNotes; }
    public void setDownloadedFilePath(String downloadedFilePath) { this.downloadedFilePath = downloadedFilePath; }
    public void setGeneratedSummary(String generatedSummary) { this.generatedSummary = generatedSummary; }
    public void setUrgent(boolean urgent) { this.urgent = urgent; }
    public void setUrgentReason(String urgentReason) { this.urgentReason = urgentReason; }
}
