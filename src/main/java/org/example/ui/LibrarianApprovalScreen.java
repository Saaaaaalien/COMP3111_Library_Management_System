package org.example.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.app.Navigator;
import org.example.db.BookDao;
import org.example.db.PendingDao;
import org.example.domain.PendingBook;
import org.example.domain.User;
import org.example.service.NotificationService;
import org.example.util.BookPreviewUtil;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

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

    // Tracks which PENDING book IDs are selected for bulk actions
    private static final Set<Long> selectedBulkIds = new LinkedHashSet<>();

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

        Button manageUsersBtn = new Button("Manage Users");
        manageUsersBtn.getStyleClass().add("primary-button");
        manageUsersBtn.setOnAction(e -> navigator.showLibrarianManageUsers(librarian));

        Button myProfileBtn = new Button("My Profile");
        myProfileBtn.getStyleClass().add("secondary-button");
        myProfileBtn.setOnAction(e -> navigator.showLibrarianProfile(librarian));

        Button borrowRecordsBtn = new Button("Borrow Records");
        borrowRecordsBtn.getStyleClass().add("secondary-button");
        borrowRecordsBtn.setOnAction(e -> navigator.showLibrarianBorrowRecords(librarian));

        Button notificationsBtn = new Button("🔔 Notifications");
        notificationsBtn.getStyleClass().add("secondary-button");
        notificationsBtn.setOnAction(e -> navigator.showLibrarianNotifications(librarian));

        VBox headerBox = new VBox(8, title, librarianInfoLbl, searchFilterBox);
        headerBox.setPadding(new Insets(20, 20, 0, 20));
        headerBox.setStyle("-fx-border-color: #f0f0f0; -fx-border-width: 0 0 1 0;");

        HBox footerBtnBox = new HBox(10, manageUsersBtn, borrowRecordsBtn, myProfileBtn, notificationsBtn, logoutBtn);
        footerBtnBox.setAlignment(Pos.CENTER_RIGHT);

        VBox footerBox = new VBox();
        footerBox.setPadding(new Insets(15, 20, 15, 20));
        footerBox.setAlignment(Pos.CENTER_RIGHT);
        footerBox.getChildren().add(footerBtnBox);

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
     * Load and display books based on current search/filter state.
     * Also rebuilds the bulk-action bar (Select All / Deselect All / Bulk Approve / Bulk Reject)
     * when PENDING submissions are present in the result set.
     */
    private static void loadAndDisplayBooks(VBox mainContent) {
        mainContent.getChildren().clear();
        selectedBulkIds.clear();

        try {
            List<PendingBook> books;

            if (currentSearchTerm.isEmpty() && currentStatusFilter.isEmpty()) {
                books = PendingDao.findAll();
            } else if (currentSearchTerm.isEmpty()) {
                books = PendingDao.filterByStatus(currentStatusFilter);
            } else if (currentStatusFilter.isEmpty()) {
                books = PendingDao.searchBooks(currentSearchTerm);
            } else {
                books = PendingDao.searchAndFilter(currentSearchTerm, currentStatusFilter);
            }

            if (books.isEmpty()) {
                Label noBooksLbl = new Label("No book submissions match your search criteria.");
                noBooksLbl.setStyle("-fx-font-size: 14; -fx-text-fill: #888;");
                mainContent.getChildren().add(noBooksLbl);
            } else {
                // ── Count label ───────────────────────────────────────────────
                Label countLbl = new Label("Results: " + books.size() + " submission(s)");
                countLbl.setStyle("-fx-font-size: 12; -fx-text-fill: #555;");
                mainContent.getChildren().add(countLbl);

                // ── Collect PENDING books and their CheckBoxes for Select-All ─
                List<PendingBook> pendingBooks = new ArrayList<>();
                List<CheckBox> pendingCheckBoxes = new ArrayList<>();
                for (PendingBook b : books) {
                    if ("PENDING".equals(b.getStatus())) {
                        pendingBooks.add(b);
                    }
                }

                // ── Bulk action bar (only when there are PENDING submissions) ─
                if (!pendingBooks.isEmpty()) {
                    Button selectAllBtn = new Button("☑ Select All Pending");
                    selectAllBtn.getStyleClass().add("secondary-button");

                    Button deselectAllBtn = new Button("☐ Deselect All");
                    deselectAllBtn.getStyleClass().add("secondary-button");

                    Button bulkApproveBtn = new Button("✔ Bulk Approve");
                    bulkApproveBtn.getStyleClass().add("primary-button");
                    bulkApproveBtn.setOnAction(e -> handleBulkApprove(mainContent, pendingBooks));

                    Button bulkRejectBtn = new Button("✖ Bulk Reject");
                    bulkRejectBtn.getStyleClass().add("secondary-button");
                    bulkRejectBtn.setOnAction(e -> handleBulkReject(mainContent, pendingBooks));

                    HBox bulkBar = new HBox(10,
                            new Label("Bulk Actions:"),
                            selectAllBtn, deselectAllBtn,
                            new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
                            bulkApproveBtn, bulkRejectBtn);
                    bulkBar.setAlignment(Pos.CENTER_LEFT);
                    bulkBar.setPadding(new Insets(6, 0, 6, 0));
                    bulkBar.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #e0e0e0;"
                            + " -fx-border-width: 1; -fx-border-radius: 4; -fx-padding: 8;");
                    mainContent.getChildren().add(bulkBar);

                    // Wire Select-All / Deselect-All AFTER cards are built
                    // (pendingCheckBoxes is filled during card creation below)
                    selectAllBtn.setOnAction(e -> {
                        for (CheckBox cb : pendingCheckBoxes) cb.setSelected(true);
                    });
                    deselectAllBtn.setOnAction(e -> {
                        for (CheckBox cb : pendingCheckBoxes) cb.setSelected(false);
                    });
                }

                // ── Build cards ───────────────────────────────────────────────
                for (PendingBook book : books) {
                    VBox bookCard = createBookCard(mainContent, book, pendingCheckBoxes);
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
     * Create a card for a single pending book submission.
     *
     * @param pendingCheckBoxes mutable list — if this book is PENDING, its selection
     *                          CheckBox is appended here so the bulk bar can
     *                          select/deselect all at once.
     */
    private static VBox createBookCard(VBox mainContent, PendingBook book,
                                       List<CheckBox> pendingCheckBoxes) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-border-color: #ddd; -fx-border-width: 1; -fx-border-radius: 5;");

        // If this submission is an edit of a published book that has been removed from the catalog,
        // the librarian should not be able to approve/reject it.
        boolean lockDecision = false;
        if (book.getOriginalBookId() > 0) {
            try {
                lockDecision = !BookDao.isVisible(book.getOriginalBookId());
            } catch (SQLException ignored) {
                // Best-effort UI locking; backend guard in PendingDao still enforces correctness.
                lockDecision = false;
            }
        }

        // ── Bulk-selection CheckBox (PENDING only) ───────────────────────
        if ("PENDING".equals(book.getStatus())) {
            CheckBox selectBox = new CheckBox("Select for bulk action");
            selectBox.setStyle("-fx-font-size: 11; -fx-text-fill: #555;");
            selectBox.setSelected(selectedBulkIds.contains(book.getId()));
            selectBox.setOnAction(e -> {
                if (selectBox.isSelected()) {
                    selectedBulkIds.add(book.getId());
                } else {
                    selectedBulkIds.remove(book.getId());
                }
            });
            card.getChildren().add(selectBox);
            pendingCheckBoxes.add(selectBox);

            // Keep selectedBulkIds in sync when Select All drives the checkbox
            selectBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) selectedBulkIds.add(book.getId());
                else selectedBulkIds.remove(book.getId());
            });
        }

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

        // File metadata + preview / download actions
        String fileSizeStr = book.getFileSize() > 0
                ? String.format("%.1f KB", book.getFileSize() / 1024.0) : "unknown size";
        String fileTypeStr = book.getFileType() != null ? book.getFileType().toUpperCase() : "?";
        Label fileLbl = new Label("File: " + (book.getFileName() != null ? book.getFileName() : "—")
                + "  [" + fileTypeStr + ", " + fileSizeStr + "]");
        fileLbl.setStyle("-fx-font-size: 10; -fx-text-fill: #555;");

        Button previewBtn = new Button("\uD83D\uDC41 Preview Content");
        previewBtn.getStyleClass().add("secondary-button");
        boolean fileExists = book.getFilePath() != null && new File(book.getFilePath()).exists();
        previewBtn.setDisable(!fileExists || !BookPreviewUtil.isSupportedPreviewType(book.getFilePath()));
        if (!fileExists) {
            previewBtn.setText("\u26A0 File not found");
        }
        previewBtn.setOnAction(e -> showFilePreviewDialog(book));

        Button openBtn = new Button("\uD83D\uDCE5 Download File");
        openBtn.getStyleClass().add("secondary-button");
        openBtn.setDisable(!fileExists);
        openBtn.setOnAction(e -> downloadFile(book.getFilePath()));

        HBox fileRow = new HBox(10, fileLbl, previewBtn, openBtn);
        fileRow.setAlignment(Pos.CENTER_LEFT);

        VBox detailsBox = new VBox(6, titleLbl, authorLbl, genreLbl, submittedLbl, statusLbl, fileRow);

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

        // Show input controls for all submissions (review notes only for pending)
        VBox inputBox = new VBox();
        
        // Review notes input - only for pending submissions
        if ("PENDING".equals(book.getStatus())) {
            Label reviewNotesLbl = new Label("Review Notes (optional):");
            reviewNotesLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");

            TextArea reviewNotesArea = new TextArea();
            reviewNotesArea.setWrapText(true);
            reviewNotesArea.setPrefRowCount(2);
            reviewNotesArea.setPromptText("Add any comments or feedback for the author...");
            reviewNotesArea.setStyle("-fx-font-size: 10; -fx-padding: 5;");

            inputBox.getChildren().addAll(
                new javafx.scene.control.Separator(),
                reviewNotesLbl,
                reviewNotesArea
            );

            // Approve and Reject buttons (always visible)
            Button approveBtn = new Button("Approve");
            approveBtn.getStyleClass().add("primary-button");
            approveBtn.setMinWidth(100);
            approveBtn.setDisable(lockDecision);
            approveBtn.setOnAction(e -> handleApprove(mainContent, book, reviewNotesArea.getText()));

            Button rejectBtn = new Button("Reject");
            rejectBtn.getStyleClass().add("secondary-button");
            rejectBtn.setMinWidth(100);
            rejectBtn.setDisable(lockDecision);
            rejectBtn.setOnAction(e -> handleReject(mainContent, book, reviewNotesArea.getText()));

            HBox buttonBox = new HBox(10, approveBtn, rejectBtn);
            buttonBox.setAlignment(Pos.CENTER_RIGHT);
            inputBox.getChildren().add(buttonBox);
        } else {
            // For approved/rejected books, show buttons to change decision
            Label changeLbl = new Label("Change Decision:");
            changeLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 11; -fx-text-fill: #666;");

            Button approveBtn = new Button("Approve");
            approveBtn.getStyleClass().add("primary-button");
            approveBtn.setMinWidth(100);
            approveBtn.setDisable(lockDecision);
            approveBtn.setOnAction(e -> handleApprove(mainContent, book, ""));

            Button rejectBtn = new Button("Reject");
            rejectBtn.getStyleClass().add("secondary-button");
            rejectBtn.setMinWidth(100);
            rejectBtn.setDisable(lockDecision);
            rejectBtn.setOnAction(e -> handleReject(mainContent, book, ""));

            HBox buttonBox = new HBox(10, approveBtn, rejectBtn);
            buttonBox.setAlignment(Pos.CENTER_RIGHT);

            inputBox.getChildren().addAll(
                new javafx.scene.control.Separator(),
                changeLbl,
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
    private static void handleApprove(VBox mainContent, PendingBook book, String reviewNotes) {
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

                // Refresh the display to clear previous state
                loadAndDisplayBooks(mainContent);
            } catch (SQLException e) {
                showErrorAlert("Approval Failed", "A database error occurred: " + e.getMessage());
            }
        }
    }

    /**
     * Handle book rejection with required reason in confirmation dialog
     */
    private static void handleReject(VBox mainContent, PendingBook book, String reviewNotes) {
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
                    
                    // Refresh the display to clear previous state
                    loadAndDisplayBooks(mainContent);
                } catch (SQLException e) {
                    showErrorAlert("Rejection Failed", "A database error occurred: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Bulk-approve all currently selected PENDING submissions.
     * Shows a confirmation dialog listing the selected titles before proceeding.
     */
    private static void handleBulkApprove(VBox mainContent, List<PendingBook> allPendingBooks) {
        List<PendingBook> targets = new ArrayList<>();
        for (PendingBook b : allPendingBooks) {
            if (selectedBulkIds.contains(b.getId())) {
                targets.add(b);
            }
        }

        if (targets.isEmpty()) {
            showErrorAlert("No Books Selected",
                    "Please select at least one pending book using the checkboxes before using Bulk Approve.");
            return;
        }

        // Build confirmation message
        StringBuilder sb = new StringBuilder();
        sb.append("You are about to APPROVE the following ").append(targets.size()).append(" book(s):\n\n");
        for (int i = 0; i < targets.size(); i++) {
            sb.append("  ").append(i + 1).append(". ").append(targets.get(i).getTitle()).append("\n");
        }
        sb.append("\nThis action will notify each author. Proceed?");

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Bulk Approval");
        confirm.setHeaderText("Bulk Approve — " + targets.size() + " submission(s)");
        confirm.setContentText(sb.toString());
        confirm.getDialogPane().setPrefWidth(480);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        // Perform approvals
        List<String> failed = new ArrayList<>();
        for (PendingBook b : targets) {
            try {
                PendingDao.approvePendingBook(b.getId(), "");
                try {
                    NotificationService.notifyAuthorSubmissionApproved(b.getAuthorUserId(), b.getTitle());
                } catch (SQLException ne) {
                    // Non-fatal
                }
            } catch (SQLException e) {
                failed.add(b.getTitle() + " (" + e.getMessage() + ")");
            }
        }

        if (failed.isEmpty()) {
            showSuccessAlert("Bulk Approval Complete",
                    targets.size() + " book(s) have been approved successfully.");
        } else {
            showErrorAlert("Bulk Approval Partially Failed",
                    "The following books could not be approved:\n" + String.join("\n", failed));
        }
        loadAndDisplayBooks(mainContent);
    }

    /**
     * Bulk-reject all currently selected PENDING submissions.
     * Prompts for a single rejection reason applied to every selected book,
     * then shows a confirmation dialog listing titles + reason before proceeding.
     */
    private static void handleBulkReject(VBox mainContent, List<PendingBook> allPendingBooks) {
        List<PendingBook> targets = new ArrayList<>();
        for (PendingBook b : allPendingBooks) {
            if (selectedBulkIds.contains(b.getId())) {
                targets.add(b);
            }
        }

        if (targets.isEmpty()) {
            showErrorAlert("No Books Selected",
                    "Please select at least one pending book using the checkboxes before using Bulk Reject.");
            return;
        }

        // ── Step 1: Collect shared rejection reason ───────────────────────
        javafx.scene.control.Dialog<ButtonType> reasonDialog = new javafx.scene.control.Dialog<>();
        reasonDialog.setTitle("Bulk Reject — Rejection Reason");
        reasonDialog.setHeaderText("Rejecting " + targets.size() + " submission(s)");

        VBox reasonContent = new VBox(10);
        reasonContent.setPadding(new Insets(15));

        Label instruction = new Label(
                "Enter a rejection reason that will be sent to ALL selected authors:");
        instruction.setStyle("-fx-font-size: 11;");

        // List selected titles for awareness
        StringBuilder titleList = new StringBuilder();
        for (int i = 0; i < targets.size(); i++) {
            titleList.append("  ").append(i + 1).append(". ").append(targets.get(i).getTitle()).append("\n");
        }
        TextArea selectedTitlesArea = new TextArea(titleList.toString().trim());
        selectedTitlesArea.setEditable(false);
        selectedTitlesArea.setWrapText(true);
        selectedTitlesArea.setPrefRowCount(Math.min(targets.size() + 1, 5));
        selectedTitlesArea.setStyle("-fx-font-size: 10; -fx-text-fill: #555;");

        TextArea reasonArea = new TextArea();
        reasonArea.setWrapText(true);
        reasonArea.setPrefRowCount(4);
        reasonArea.setPromptText(
                "Provide specific reasons (e.g., grammar issues, content concerns, format problems)...");
        reasonArea.setStyle("-fx-font-size: 10; -fx-padding: 5;");

        reasonContent.getChildren().addAll(instruction, selectedTitlesArea,
                new Label("Rejection Reason (required):"), reasonArea);
        reasonDialog.getDialogPane().setContent(reasonContent);
        reasonDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        reasonDialog.getDialogPane().setPrefWidth(500);

        // Validate reason is non-empty before closing
        javafx.scene.control.Button okBtn =
                (javafx.scene.control.Button) reasonDialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText("Next →");
        okBtn.setOnAction(e -> {
            if (reasonArea.getText().trim().isEmpty()) {
                showErrorAlert("Missing Rejection Reason",
                        "Please enter a rejection reason before continuing.");
                e.consume();
            }
        });

        Optional<ButtonType> reasonResult = reasonDialog.showAndWait();
        if (reasonResult.isEmpty() || reasonResult.get() != ButtonType.OK) {
            return;
        }
        String sharedReason = reasonArea.getText().trim();

        // ── Step 2: Final confirmation ────────────────────────────────────
        StringBuilder confirmMsg = new StringBuilder();
        confirmMsg.append("You are about to REJECT the following ").append(targets.size()).append(" book(s):\n\n");
        for (int i = 0; i < targets.size(); i++) {
            confirmMsg.append("  ").append(i + 1).append(". ").append(targets.get(i).getTitle()).append("\n");
        }
        confirmMsg.append("\nRejection Reason (sent to all authors):\n  ").append(sharedReason)
                  .append("\n\nThis action cannot be undone. Proceed?");

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Bulk Rejection");
        confirm.setHeaderText("Bulk Reject — " + targets.size() + " submission(s)");
        confirm.setContentText(confirmMsg.toString());
        confirm.getDialogPane().setPrefWidth(500);

        Optional<ButtonType> confirmResult = confirm.showAndWait();
        if (confirmResult.isEmpty() || confirmResult.get() != ButtonType.OK) {
            return;
        }

        // ── Step 3: Perform rejections ────────────────────────────────────
        List<String> failed = new ArrayList<>();
        for (PendingBook b : targets) {
            try {
                PendingDao.rejectPendingBook(b.getId(), "", sharedReason);
                try {
                    NotificationService.notifyAuthorSubmissionRejected(
                            b.getAuthorUserId(), b.getTitle(), "Reason: " + sharedReason);
                } catch (SQLException ne) {
                    // Non-fatal
                }
            } catch (SQLException e) {
                failed.add(b.getTitle() + " (" + e.getMessage() + ")");
            }
        }

        if (failed.isEmpty()) {
            showSuccessAlert("Bulk Rejection Complete",
                    targets.size() + " book(s) have been rejected. Authors have been notified.");
        } else {
            showErrorAlert("Bulk Rejection Partially Failed",
                    "The following books could not be rejected:\n" + String.join("\n", failed));
        }
        loadAndDisplayBooks(mainContent);
    }

    private static String formatDate(Object date) {
        if (date == null) return "N/A";
        return date.toString();
    }

    /**
     * Opens a modal dialog showing up to 5 pages of the submitted book file.
     * PDFs are rendered as images; text/doc/docx are shown in a TextArea.
     */
    private static void showFilePreviewDialog(PendingBook book) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Content Preview — " + book.getTitle());

        VBox container = new VBox(12);
        container.setPadding(new Insets(16));

        Label heading = new Label("Preview: " + book.getTitle());
        heading.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        Label subHeading = new Label("Author: " + book.getAuthorFullName()
                + "  |  Type: " + (book.getFileType() != null ? book.getFileType().toUpperCase() : "?"));
        subHeading.setStyle("-fx-font-size: 11; -fx-text-fill: #555;");
        container.getChildren().addAll(heading, subHeading);

        String filePath = book.getFilePath();
        boolean isPdf = filePath != null && filePath.toLowerCase().endsWith(".pdf");

        if (isPdf) {
            List<Image> pages = BookPreviewUtil.readPdfPreviewImages(filePath, 5);
            if (pages.isEmpty()) {
                container.getChildren().add(new Label("PDF rendering failed or file is unreadable."));
            } else {
                Label hint = new Label("Showing first " + pages.size() + " page(s).");
                hint.setStyle("-fx-font-size: 10; -fx-text-fill: #888;");
                container.getChildren().add(hint);
                for (Image img : pages) {
                    ImageView iv = new ImageView(img);
                    iv.setPreserveRatio(true);
                    iv.setFitWidth(580);
                    container.getChildren().add(iv);
                }
            }
        } else {
            String text = BookPreviewUtil.readTextPreview(filePath);
            if (text == null || text.isBlank()) {
                text = "Content preview is not available for this file type or the file could not be read.";
            }
            TextArea area = new TextArea(text);
            area.setEditable(false);
            area.setWrapText(true);
            area.setPrefRowCount(22);
            area.setStyle("-fx-font-family: monospace; -fx-font-size: 11;");
            container.getChildren().add(area);
        }

        Button openBtn = new Button("\uD83D\uDCE5 Download File");
        openBtn.setOnAction(e -> downloadFile(filePath));
        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setOnAction(e -> dialog.close());
        HBox footer = new HBox(10, openBtn, closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(8, 0, 0, 0));
        container.getChildren().add(footer);

        ScrollPane scroll = new ScrollPane(container);
        scroll.setFitToWidth(true);
        Scene dialogScene = new Scene(scroll, 640, 560);
        java.net.URL css = LibrarianApprovalScreen.class.getResource("/app.css");
        if (css != null) dialogScene.getStylesheets().add(css.toExternalForm());
        dialog.setScene(dialogScene);
        dialog.showAndWait();
    }

    /**
     * Opens a Save dialog so the librarian can choose where to save a copy of the
     * submitted book file.  Uses JavaFX FileChooser; no external viewer is launched.
     */
    private static void downloadFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            showErrorAlert("File Not Available", "No file path is recorded for this submission.");
            return;
        }
        File source = new File(filePath);
        if (!source.exists()) {
            showErrorAlert("File Not Found",
                    "The submitted file could not be located at:\n" + filePath);
            return;
        }

        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Save Book File As");
        chooser.setInitialFileName(source.getName());

        // Suggest appropriate extension filter
        String name = source.getName().toLowerCase();
        javafx.stage.FileChooser.ExtensionFilter filter;
        if (name.endsWith(".pdf")) {
            filter = new javafx.stage.FileChooser.ExtensionFilter("PDF files", "*.pdf");
        } else if (name.endsWith(".docx")) {
            filter = new javafx.stage.FileChooser.ExtensionFilter("Word documents", "*.docx");
        } else if (name.endsWith(".doc")) {
            filter = new javafx.stage.FileChooser.ExtensionFilter("Word documents", "*.doc");
        } else {
            filter = new javafx.stage.FileChooser.ExtensionFilter("All files", "*.*");
        }
        chooser.getExtensionFilters().add(filter);

        File dest = chooser.showSaveDialog(null);
        if (dest == null) {
            return; // user cancelled
        }

        try {
            Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            showSuccessAlert("Download Complete",
                    "File saved to:\n" + dest.getAbsolutePath());
        } catch (IOException ex) {
            showErrorAlert("Download Failed", "Could not save the file:\n" + ex.getMessage());
        }
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

