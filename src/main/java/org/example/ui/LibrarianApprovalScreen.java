package org.example.ui;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.example.app.Navigator;
import org.example.db.PendingDao;
import org.example.service.NotificationService;
import org.example.domain.PendingBook;
import org.example.domain.User;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Librarian approval screen: displays pending book submissions and allows
 * the librarian to approve or reject them with optional review notes.
 * Features include search, filtering, and rejection reason capture.
 */
public final class LibrarianApprovalScreen {
    private LibrarianApprovalScreen() {}

    // Mutable state for search/filter
    private static String currentSearchTerm = "";
    private static String currentStatusFilter = "";

    public static Scene create(Navigator navigator, User librarian) {
        Label title = new Label("Book Approval Dashboard");
        title.getStyleClass().add("screen-title");

        Label librarianInfoLbl = new Label("Logged in as: " + librarian.getFullName());
        librarianInfoLbl.getStyleClass().add("info-label");

        // Search and filter controls
        HBox searchFilterBox = createSearchFilterBox();

        // Main content area - wrapped in a container that can be updated
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1;");

        ScrollPane scrollPane = new ScrollPane(mainContent);
        scrollPane.setFitToWidth(true);

        // Load initial data
        loadAndDisplayBooks(mainContent);

        // Button to refresh/search
        Button refreshBtn = new Button("Search/Filter");
        refreshBtn.getStyleClass().add("primary-button");
        refreshBtn.setOnAction(e -> loadAndDisplayBooks(mainContent));

        HBox refreshBox = new HBox(10);
        refreshBox.setPadding(new Insets(10));
        refreshBox.setAlignment(Pos.CENTER_LEFT);
        refreshBox.getChildren().add(refreshBtn);

        Button logoutBtn = new Button("Logout");
        logoutBtn.getStyleClass().add("secondary-button");
        logoutBtn.setOnAction(e -> navigator.showLibrarianPortal());

        VBox headerBox = new VBox(8, title, librarianInfoLbl, searchFilterBox);
        headerBox.setPadding(new Insets(20, 20, 0, 20));
        headerBox.setStyle("-fx-border-color: #f0f0f0; -fx-border-width: 0 0 1 0;");

        VBox footerBox = new VBox();
        footerBox.setPadding(new Insets(15, 20, 15, 20));
        footerBox.setAlignment(Pos.CENTER_RIGHT);
        footerBox.getChildren().add(logoutBtn);

        BorderPane root = new BorderPane();
        root.setTop(headerBox);
        root.setCenter(new VBox(refreshBox, scrollPane));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        root.setBottom(footerBox);
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL cssResource = LibrarianApprovalScreen.class.getResource("/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }
        return scene;
    }

    /**
     * Create search and filter controls
     */
    private static HBox createSearchFilterBox() {
        HBox box = new HBox(15);
        box.setPadding(new Insets(10, 0, 0, 0));
        box.setStyle("-fx-border-color: #f5f5f5; -fx-border-width: 0 0 1 0; -fx-padding: 10;");

        // Search field
        Label searchLbl = new Label("Search (Title/Author/Genre):");
        searchLbl.setStyle("-fx-font-size: 11;");

        TextField searchField = new TextField();
        searchField.setPromptText("Type to search...");
        searchField.setPrefWidth(200);
        searchField.setStyle("-fx-padding: 5;");
        searchField.setText(currentSearchTerm);
        searchField.setOnKeyReleased(e -> currentSearchTerm = searchField.getText());

        // Status filter
        Label statusLbl = new Label("Filter by Status:");
        statusLbl.setStyle("-fx-font-size: 11;");

        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("All", "PENDING", "APPROVED", "REJECTED");
        statusCombo.setValue(currentStatusFilter.isEmpty() ? "PENDING" : currentStatusFilter);
        statusCombo.setPrefWidth(120);
        statusCombo.setOnAction(e -> {
            String selected = statusCombo.getValue();
            currentStatusFilter = "All".equals(selected) ? "" : selected;
        });

        HBox.setHgrow(searchField, Priority.ALWAYS);

        javafx.scene.control.Separator vertSeparator = new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL);
        vertSeparator.setPrefHeight(30);

        box.getChildren().addAll(
            searchLbl, searchField,
            vertSeparator,
            statusLbl, statusCombo
        );

        return box;
    }

    /**
     * Load and display books based on current search/filter state
     */
    private static void loadAndDisplayBooks(VBox mainContent) {
        mainContent.getChildren().clear();

        try {
            List<PendingBook> books;

            if (currentSearchTerm.isEmpty() && currentStatusFilter.isEmpty()) {
                // No filter: show all books
                books = PendingDao.findAll();
            } else if (currentSearchTerm.isEmpty()) {
                // Filter by status only
                books = PendingDao.filterByStatus(currentStatusFilter);
            } else if (currentStatusFilter.isEmpty()) {
                // Search only: search all books
                books = PendingDao.searchBooks(currentSearchTerm);
            } else {
                // Both search and filter
                books = PendingDao.searchAndFilter(currentSearchTerm, currentStatusFilter);
            }

            if (books.isEmpty()) {
                Label noBooksLbl = new Label("No book submissions match your search criteria.");
                noBooksLbl.setStyle("-fx-font-size: 14; -fx-text-fill: #888;");
                mainContent.getChildren().add(noBooksLbl);
            } else {
                Label countLbl = new Label("Results: " + books.size() + " submission(s)");
                countLbl.setStyle("-fx-font-size: 12; -fx-text-fill: #555;");
                mainContent.getChildren().add(countLbl);

                for (PendingBook book : books) {
                    VBox bookCard = createBookCard(mainContent, book);
                    mainContent.getChildren().add(bookCard);
                }
            }
        } catch (SQLException e) {
            Label errorLbl = new Label("Error loading books: " + e.getMessage());
            errorLbl.setStyle("-fx-text-fill: #d9534f;");
            mainContent.getChildren().add(errorLbl);
        }
    }

    /**
     * Create a card for a single pending book submission
     */
    private static VBox createBookCard(VBox mainContent, PendingBook book) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-border-color: #ddd; -fx-border-width: 1; -fx-border-radius: 5;");

        // Book details
        Label titleLbl = new Label("Title: " + book.getTitle());
        titleLbl.setStyle("-fx-font-size: 13; -fx-font-weight: bold;");

        Label authorLbl = new Label("Author: " + book.getAuthorFullName());
        authorLbl.setStyle("-fx-font-size: 11;");

        Label genreLbl = new Label("Genre: " + book.getGenre());
        genreLbl.setStyle("-fx-font-size: 11;");

        Label submittedLbl = new Label("Submitted: " + formatDate(book.getSubmittedDate()));
        submittedLbl.setStyle("-fx-font-size: 11; -fx-text-fill: #666;");

        Label statusLbl = new Label("Status: " + book.getStatus());
        String statusColor = "PENDING".equals(book.getStatus()) ? "#ff9800" :
                            "APPROVED".equals(book.getStatus()) ? "#4caf50" :
                            "REJECTED".equals(book.getStatus()) ? "#f44336" : "#999";
        statusLbl.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: " + statusColor + ";");


        VBox detailsBox = new VBox(6, titleLbl, authorLbl, genreLbl, submittedLbl, statusLbl);

        // Summary/Description
        Label summaryTitleLbl = new Label("Summary:");
        summaryTitleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");

        TextArea summaryArea = new TextArea(book.getSummary());
        summaryArea.setWrapText(true);
        summaryArea.setEditable(false);
        summaryArea.setPrefRowCount(4);
        summaryArea.setStyle("-fx-font-size: 10; -fx-padding: 5;");

        // Show review notes and rejection reason if already reviewed
        VBox reviewBox = new VBox(8);
        if (!"PENDING".equals(book.getStatus())) {
            reviewBox.setStyle("-fx-border-color: #f5f5f5; -fx-border-width: 1; -fx-padding: 10; -fx-border-radius: 3;");

            if (book.getReviewNotes() != null && !book.getReviewNotes().isEmpty()) {
                Label reviewNotesLabel = new Label("Review Notes:");
                reviewNotesLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 10;");
                TextArea reviewNotesDisplay = new TextArea(book.getReviewNotes());
                reviewNotesDisplay.setWrapText(true);
                reviewNotesDisplay.setEditable(false);
                reviewNotesDisplay.setPrefRowCount(2);
                reviewNotesDisplay.setStyle("-fx-font-size: 9; -fx-padding: 3;");
                reviewBox.getChildren().addAll(reviewNotesLabel, reviewNotesDisplay);
            }

            if ("REJECTED".equals(book.getStatus()) && book.getRejectionReason() != null && !book.getRejectionReason().isEmpty()) {
                Label rejectionReasonLabel = new Label("Rejection Reason:");
                rejectionReasonLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 10; -fx-text-fill: #f44336;");
                TextArea rejectionReasonDisplay = new TextArea(book.getRejectionReason());
                rejectionReasonDisplay.setWrapText(true);
                rejectionReasonDisplay.setEditable(false);
                rejectionReasonDisplay.setPrefRowCount(2);
                rejectionReasonDisplay.setStyle("-fx-font-size: 9; -fx-padding: 3; -fx-text-fill: #f44336;");
                reviewBox.getChildren().addAll(rejectionReasonLabel, rejectionReasonDisplay);
            }
        }

        // Show input controls only for pending submissions
        VBox inputBox = new VBox();
        if ("PENDING".equals(book.getStatus())) {
            // Review notes input
            Label reviewNotesLbl = new Label("Review Notes (optional):");
            reviewNotesLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");

            TextArea reviewNotesArea = new TextArea();
            reviewNotesArea.setWrapText(true);
            reviewNotesArea.setPrefRowCount(2);
            reviewNotesArea.setPromptText("Add any comments or feedback for the author...");
            reviewNotesArea.setStyle("-fx-font-size: 10; -fx-padding: 5;");

            // Approve and Reject buttons
            Button approveBtn = new Button("Approve");
            approveBtn.getStyleClass().add("primary-button");
            approveBtn.setMinWidth(100);
            approveBtn.setOnAction(e -> handleApprove(mainContent, book, statusLbl, reviewNotesArea.getText()));

            Button rejectBtn = new Button("Reject");
            rejectBtn.getStyleClass().add("secondary-button");
            rejectBtn.setMinWidth(100);
            rejectBtn.setOnAction(e -> handleReject(mainContent, book, statusLbl, reviewNotesArea.getText()));

            HBox buttonBox = new HBox(10, approveBtn, rejectBtn);
            buttonBox.setAlignment(Pos.CENTER_RIGHT);

            inputBox.getChildren().addAll(
                new javafx.scene.control.Separator(),
                reviewNotesLbl,
                reviewNotesArea,
                buttonBox
            );
        }

        card.getChildren().addAll(
            detailsBox,
            new Separator(),
            summaryTitleLbl,
            summaryArea
        );

        if (!reviewBox.getChildren().isEmpty()) {
            card.getChildren().add(reviewBox);
        }

        if (!inputBox.getChildren().isEmpty()) {
            card.getChildren().add(inputBox);
        }

        return card;
    }

    /**
     * Handle book approval
     */
    private static void handleApprove(VBox mainContent, PendingBook book, Label statusLbl, String reviewNotes) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Approval");
        confirmAlert.setHeaderText(null);
        confirmAlert.setContentText("Are you sure you want to approve this book?\n\nTitle: " + book.getTitle());

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                PendingDao.approvePendingBook(book.getId(), reviewNotes);
                try {
                    NotificationService.notifyAuthorSubmissionApproved(book.getAuthorUserId(), book.getTitle());
                } catch (SQLException ne) {
                    // Non-fatal: logging can be added; do not prevent UI update on notification failure.
                }
                showSuccessAlert("Book Approved", "The book \"" + book.getTitle() + "\" has been approved successfully.");
                
                // Update status label instead of refreshing entire display
                book.setStatus("APPROVED");
                statusLbl.setText("Status: APPROVED");
                statusLbl.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #4caf50;");
            } catch (SQLException e) {
                showErrorAlert("Approval Failed", "A database error occurred: " + e.getMessage());
            }
        }
    }

    /**
     * Handle book rejection with required reason in confirmation dialog
     */
    private static void handleReject(VBox mainContent, PendingBook book, Label statusLbl, String reviewNotes) {
        // Create custom dialog for rejection reason
        javafx.scene.control.Dialog<ButtonType> rejectionDialog = new javafx.scene.control.Dialog<>();
        rejectionDialog.setTitle("Reject Book Submission");
        rejectionDialog.setHeaderText("Confirm Rejection: " + book.getTitle());

        // Create content
        VBox content = new VBox(12);
        content.setPadding(new Insets(15));

        Label instructionLbl = new Label("Please provide a rejection reason that will be sent to the author:");
        instructionLbl.setStyle("-fx-font-size: 11;");

        TextArea rejectionReasonArea = new TextArea();
        rejectionReasonArea.setWrapText(true);
        rejectionReasonArea.setPrefRowCount(4);
        rejectionReasonArea.setPromptText("Enter specific reasons for rejection (e.g., grammar issues, content concerns, format problems)...");
        rejectionReasonArea.setStyle("-fx-font-size: 10; -fx-padding: 5;");

        content.getChildren().addAll(instructionLbl, rejectionReasonArea);

        rejectionDialog.getDialogPane().setContent(content);
        rejectionDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Handle OK button
        javafx.scene.control.Button okButton = (javafx.scene.control.Button) rejectionDialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setOnAction(e -> {
            String reason = rejectionReasonArea.getText().trim();
            if (reason.isEmpty()) {
                showErrorAlert("Missing Rejection Reason", "Please enter a rejection reason before confirming.");
                e.consume(); // Prevent dialog from closing
            }
        });

        Optional<ButtonType> result = rejectionDialog.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            String rejectionReason = rejectionReasonArea.getText().trim();

            // Final confirmation with reason shown
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Confirm Rejection");
            confirmAlert.setHeaderText(null);
            confirmAlert.setContentText("Are you sure you want to reject this book?\n\nTitle: " + book.getTitle() +
                                       "\n\nRejection Reason: " + rejectionReason);

            Optional<ButtonType> confirmResult = confirmAlert.showAndWait();
            if (confirmResult.isPresent() && confirmResult.get() == ButtonType.OK) {
                try {
                    PendingDao.rejectPendingBook(book.getId(), reviewNotes, rejectionReason);
                    try {
                        String notesForNotification = (reviewNotes != null ? reviewNotes.trim() : "");
                        if (notesForNotification.isEmpty()) {
                            notesForNotification = "Reason: " + rejectionReason;
                        } else {
                            notesForNotification = notesForNotification + "\nReason: " + rejectionReason;
                        }
                        NotificationService.notifyAuthorSubmissionRejected(book.getAuthorUserId(), book.getTitle(), notesForNotification);
                    } catch (SQLException ne) {
                        // Non-fatal: ignore notification failure for now
                    }
                    showSuccessAlert("Book Rejected", "The book \"" + book.getTitle() + "\" has been rejected.\n\n" +
                                    "The author will receive the rejection reason:\n" + rejectionReason);
                    
                    // Update status label instead of refreshing entire display
                    book.setStatus("REJECTED");
                    book.setRejectionReason(rejectionReason);
                    statusLbl.setText("Status: REJECTED");
                    statusLbl.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #f44336;");
                } catch (SQLException e) {
                    showErrorAlert("Rejection Failed", "A database error occurred: " + e.getMessage());
                }
            }
        }
    }

    private static String formatDate(Object date) {
        if (date == null) return "N/A";
        return date.toString();
    }

    private static void showSuccessAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * A simple separator line
     */
    private static class Separator extends javafx.scene.control.Separator {
        public Separator() {
            super();
            this.setPrefHeight(1);
            this.setStyle("-fx-padding: 5; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");
        }
    }
}

