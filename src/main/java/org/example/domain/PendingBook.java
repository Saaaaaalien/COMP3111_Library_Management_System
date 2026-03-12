package org.example.domain;

import java.time.LocalDateTime;

public class PendingBook {
    private long id;
    private String title;
    private long authorUserId;
    private String authorFullName;
    private String genre;
    private String summary;
    private String fileName;
    private String filePath;
    private long fileSize;
    private String fileType;
    private LocalDateTime submittedDate;
    private String status; // PENDING, APPROVED, REJECTED
    private String reviewNotes;
    private LocalDateTime reviewedDate;
    private String rejectionReason;

    public PendingBook() {}

    public PendingBook(String title, long authorUserId, String authorFullName,
                       String genre, String summary, String fileName,
                       String filePath, long fileSize, String fileType) {
        this.title = title;
        this.authorUserId = authorUserId;
        this.authorFullName = authorFullName;
        this.genre = genre;
        this.summary = summary;
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.fileType = fileType;
        this.submittedDate = LocalDateTime.now();
        this.status = "PENDING";
    }

    // Getters and setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public long getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(long authorUserId) { this.authorUserId = authorUserId; }

    public String getAuthorFullName() { return authorFullName; }
    public void setAuthorFullName(String authorFullName) { this.authorFullName = authorFullName; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public LocalDateTime getSubmittedDate() { return submittedDate; }
    public void setSubmittedDate(LocalDateTime submittedDate) { this.submittedDate = submittedDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }

    public LocalDateTime getReviewedDate() { return reviewedDate; }
    public void setReviewedDate(LocalDateTime reviewedDate) { this.reviewedDate = reviewedDate; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getFormattedFileSize() {
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
    }
}