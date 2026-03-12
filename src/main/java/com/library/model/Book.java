package com.library.model;

import java.io.Serializable;
import java.time.LocalDateTime;

//Task 2.3 Book registration
public class Book implements Serializable{
    private static final long serialVersionUID = 1L;

    //Task 2: publish books and authors details
    private String id;
    private String title;
    private String authorUsername;
    private String authorFullName;
    private String genre;
    private String description;
    private String filePath;
    //Task 3: pending books
    private String status; // PENDING, APPROVED, REJECTED
    private LocalDateTime dateOfSubmission;
    //Task 1: borrow record
    private String borrowedBy;

    public Book(String title, String authorUsername, String authorFullName,
                String genre, String description, String filePath) {
        this.id = generateId();
        this.title = title;
        this.authorUsername = authorUsername;
        this.authorFullName = authorFullName;
        this.genre = genre;
        this.description = description;
        this.filePath = filePath;
        this.dateOfSubmission = LocalDateTime.now();
        this.status = "PENDING";
        this.borrowedBy = " ";
    }
    //generate book id according to book title and submission time
    private String generateId() {
        return getTitle() + System.currentTimeMillis();
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthorUsername() { return authorUsername; }
    public String getAuthorFullName() { return authorFullName; }
    public String getGenre() { return genre; }
    public String getDescription() { return description; }
    public String getFilePath() { return filePath; }
    public LocalDateTime getSubmittedDate() { return dateOfSubmission; }
    public String getStatus() { return status; }

    //Task 3: change status when approve/reject
    public void setStatus(String status) { this.status = status; }
    public String getBorrowedBy(){return borrowedBy;}

    //Task 1: change when people borrow/return books
    public void setBorrowedBy(String borrowedBy){this.borrowedBy = borrowedBy;}
}

