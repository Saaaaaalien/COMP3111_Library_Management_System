package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.app.Navigator;
import org.example.db.PendingDao;
import org.example.domain.PendingBook;
import org.example.domain.User;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Librarian approval screen: displays pending book submissions and allows
 * the librarian to approve or reject them with optional review notes.
 */
public final class LibrarianApprovalScreen {
    private LibrarianApprovalScreen() {}

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static Scene create(Navigator navigator, User librarian) {
        Label title = new Label("Book Approval Dashboard");
        title.getStyleClass().add("screen-title");

        Label librarianInfoLbl = new Label("Logged in as: " + librarian.getFullName());
        librarianInfoLbl.getStyleClass().add("info-label");

        // Main content area
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));
        mainContent.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1;");

        // Load and display pending books
        try {
            List<PendingBook> pendingBooks = PendingDao.findAllPending();
            if (pendingBooks.isEmpty()) {
                Label noBooksLbl = new Label("No pending book submissions awaiting approval.");
                noBooksLbl.setStyle("-fx-font-size: 14; -fx-text-fill: #888;");
                mainContent.getChildren().add(noBooksLbl);
            } else {
                Label countLbl = new Label("Pending Submissions: " + pendingBooks.size());
                countLbl.setStyle("-fx-font-size: 12; -fx-text-fill: #555;");
                mainContent.getChildren().add(countLbl);

                for (PendingBook book : pendingBooks) {
                    VBox bookCard = createBookCard(navigator, librarian, book);
                    mainContent.getChildren().add(bookCard);
                }
            }
        } catch (SQLException e) {
            Label errorLbl = new Label("Error loading pending books: " + e.getMessage());
            errorLbl.setStyle("-fx-text-fill: #d9534f;");
            mainContent.getChildren().add(errorLbl);
        }

        ScrollPane scrollPane = new ScrollPane(mainContent);
        scrollPane.setFitToWidth(true);

        Button logoutBtn = new Button("Logout");
        logoutBtn.getStyleClass().add("secondary-button");
        logoutBtn.setOnAction(e -> navigator.showLibrarianPortal());

        VBox headerBox = new VBox(8, title, librarianInfoLbl);
        headerBox.setPadding(new Insets(20, 20, 0, 20));
        headerBox.setStyle("-fx-border-color: #f0f0f0; -fx-border-width: 0 0 1 0;");

        VBox footerBox = new VBox();
        footerBox.setPadding(new Insets(15, 20, 15, 20));
        footerBox.setAlignment(Pos.CENTER_RIGHT);
        footerBox.getChildren().add(logoutBtn);

        BorderPane root = new BorderPane();
        root.setTop(headerBox);
        root.setCenter(scrollPane);
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
     * Create a card for a single pending book submission
     */
    private static VBox createBookCard(Navigator navigator, User librarian, PendingBook book) {
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
        statusLbl.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #ff9800;");

        VBox detailsBox = new VBox(6, titleLbl, authorLbl, genreLbl, submittedLbl, statusLbl);

        // Summary/Description
        Label summaryTitleLbl = new Label("Summary:");
        summaryTitleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");

        TextArea summaryArea = new TextArea(book.getSummary());
        summaryArea.setWrapText(true);
        summaryArea.setEditable(false);
        summaryArea.setPrefRowCount(4);
        summaryArea.setStyle("-fx-font-size: 10; -fx-padding: 5;");

        // Review notes input
        Label reviewNotesLbl = new Label("Review Notes (optional):");
        reviewNotesLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");

        TextArea reviewNotesArea = new TextArea();
        reviewNotesArea.setWrapText(true);
        reviewNotesArea.setPrefRowCount(3);
        reviewNotesArea.setPromptText("Add any comments or feedback for the author...");
        reviewNotesArea.setStyle("-fx-font-size: 10; -fx-padding: 5;");

        // Approve and Reject buttons
        Button approveBtn = new Button("Approve");
        approveBtn.getStyleClass().add("primary-button");
        approveBtn.setMinWidth(100);
        approveBtn.setOnAction(e -> handleApprove(navigator, book, reviewNotesArea.getText()));

        Button rejectBtn = new Button("Reject");
        rejectBtn.getStyleClass().add("secondary-button");
        rejectBtn.setMinWidth(100);
        rejectBtn.setOnAction(e -> handleReject(navigator, book, reviewNotesArea.getText()));

        HBox buttonBox = new HBox(10, approveBtn, rejectBtn);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(
                detailsBox,
                new Separator(),
                summaryTitleLbl,
                summaryArea,
                reviewNotesLbl,
                reviewNotesArea,
                buttonBox
        );

        return card;
    }

    /**
     * Handle book approval
     */
    private static void handleApprove(Navigator navigator, PendingBook book, String reviewNotes) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Approval");
        confirmAlert.setHeaderText(null);
        confirmAlert.setContentText("Are you sure you want to approve this book?\n\nTitle: " + book.getTitle());

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                PendingDao.approvePendingBook(book.getId(), reviewNotes);
                showSuccessAlert("Book Approved", "The book \"" + book.getTitle() + "\" has been approved.");
                // Refresh the screen
                if (navigator.getStage().getScene().getUserData() instanceof User) {
                    User librarian = (User) navigator.getStage().getScene().getUserData();
                    navigator.showLibrarianApproval(librarian);
                }
            } catch (SQLException e) {
                showErrorAlert("Approval Failed", "A database error occurred: " + e.getMessage());
            }
        }
    }

    /**
     * Handle book rejection
     */
    private static void handleReject(Navigator navigator, PendingBook book, String reviewNotes) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Rejection");
        confirmAlert.setHeaderText(null);
        confirmAlert.setContentText("Are you sure you want to reject this book?\n\nTitle: " + book.getTitle());

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                PendingDao.rejectPendingBook(book.getId(), reviewNotes);
                showSuccessAlert("Book Rejected", "The book \"" + book.getTitle() + "\" has been rejected.");
                // Refresh the screen
                if (navigator.getStage().getScene().getUserData() instanceof User) {
                    User librarian = (User) navigator.getStage().getScene().getUserData();
                    navigator.showLibrarianApproval(librarian);
                }
            } catch (SQLException e) {
                showErrorAlert("Rejection Failed", "A database error occurred: " + e.getMessage());
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
