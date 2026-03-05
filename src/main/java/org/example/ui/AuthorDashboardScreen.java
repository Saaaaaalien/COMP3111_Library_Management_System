package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.example.app.Navigator;
import org.example.domain.Book;
import org.example.domain.PendingBook;
import org.example.domain.User;
import org.example.service.PublishService;

import java.util.List;
import java.util.Optional;

public final class AuthorDashboardScreen {

    private static User currentAuthor;
    private static Navigator navigator;
    private static Label welcomeLabel;
    private static VBox statsBox;
    private static VBox publishedBooksBox;
    private static VBox pendingBooksBox;

    private AuthorDashboardScreen() {}

    public static Scene create(Navigator nav, User author) {
        navigator = nav;
        currentAuthor = author;

        // Main layout
        BorderPane mainPane = new BorderPane();
        mainPane.setPadding(new Insets(20));

        // Top: Welcome header
        mainPane.setTop(createHeader());

        // Center: Dashboard content
        mainPane.setCenter(createDashboardContent());

        // Bottom: Action buttons
        mainPane.setBottom(createActionButtons());

        Scene scene = new Scene(mainPane, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());

        // Add CSS
        java.net.URL cssResource = AuthorDashboardScreen.class.getResource("/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }

        return scene;
    }

    private static HBox createHeader() {
        HBox header = new HBox(20);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 20, 0));

        // Welcome message
        welcomeLabel = new Label("Welcome, " + currentAuthor.getFullName() + "!");
        welcomeLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        welcomeLabel.getStyleClass().add("welcome-label");

        // Author info
        Label authorInfo = new Label( currentAuthor.getUsername());
        authorInfo.setStyle("-fx-text-fill: #666; -fx-font-size: 14px;");

        VBox welcomeBox = new VBox(5, welcomeLabel, authorInfo);

        // Logout button
        Button logoutBtn = new Button("Logout");
        logoutBtn.getStyleClass().add("logout-button");
        logoutBtn.setOnAction(e -> navigator.showWelcome());

        HBox rightBox = new HBox(logoutBtn);
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        rightBox.setPrefWidth(200);

        header.getChildren().addAll(welcomeBox, rightBox);
        HBox.setHgrow(welcomeBox, javafx.scene.layout.Priority.ALWAYS);

        return header;
    }

    private static VBox createDashboardContent() {
        VBox content = new VBox(30);
        content.setPadding(new Insets(20, 0, 20, 0));

        // Quick stats
        statsBox = createStatsBox();
        content.getChildren().add(statsBox);

        // Main actions grid
        GridPane actionGrid = createActionGrid();
        content.getChildren().add(actionGrid);

        // Recent published books
        publishedBooksBox = createPublishedBooksSection();
        content.getChildren().add(publishedBooksBox);

        // Pending submissions
        pendingBooksBox = createPendingBooksSection();
        content.getChildren().add(pendingBooksBox);
//
//        // Refresh data
//        refreshDashboardData();

        return content;
    }

    private static VBox createStatsBox() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(15));
        box.getStyleClass().add("stats-box");

        Label statsTitle = new Label("📊 Your Publishing Stats");
        statsTitle.setFont(Font.font("System", FontWeight.BOLD, 16));

        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(30);
        statsGrid.setVgap(10);

        // These will be updated dynamically
        Label publishedLabel = new Label("Published Books:");
        publishedLabel.setStyle("-fx-font-weight: bold;");
        Label publishedCount = new Label("0");
        publishedCount.setId("publishedCount");

        Label pendingLabel = new Label("Pending Approval:");
        pendingLabel.setStyle("-fx-font-weight: bold;");
        Label pendingCount = new Label("0");
        pendingCount.setId("pendingCount");

        Label approvedLabel = new Label("Approved This Month:");
        approvedLabel.setStyle("-fx-font-weight: bold;");
        Label approvedCount = new Label("0");
        approvedCount.setId("approvedCount");

        statsGrid.add(publishedLabel, 0, 0);
        statsGrid.add(publishedCount, 1, 0);
        statsGrid.add(pendingLabel, 2, 0);
        statsGrid.add(pendingCount, 3, 0);
        statsGrid.add(approvedLabel, 4, 0);
        statsGrid.add(approvedCount, 5, 0);

        box.getChildren().addAll(statsTitle, statsGrid);

        return box;
    }

    private static GridPane createActionGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(20);
        grid.setAlignment(Pos.CENTER);

        // Publish New Book Card
        VBox publishCard = createActionCard(
                "📖 Publish New Book",
                "Submit a new book for librarian approval",
                "Publish Book",
                "#27ae60"
        );
        publishCard.setOnMouseClicked(e -> navigator.showPublishBook(currentAuthor));

        // My Books Card
        VBox myBooksCard = createActionCard(
                "📚 My Published Books",
                "View all your published books",
                "View Books",
                "#3498db"
        );
//        myBooksCard.setOnMouseClicked(e -> showMyBooks());

        // Pending Submissions Card
        VBox pendingCard = createActionCard(
                "⏳ Pending Submissions",
                "Track books waiting for approval",
                "View Status",
                "#f39c12"
        );
//        pendingCard.setOnMouseClicked(e -> showPendingSubmissions());

        // Edit Profile Card
        VBox profileCard = createActionCard(
                "👤 Edit Profile",
                "Update your profile information",
                "Edit Profile",
                "#9b59b6"
        );
        profileCard.setOnMouseClicked(e -> showEditProfile());

        grid.add(publishCard, 0, 0);
        grid.add(myBooksCard, 1, 0);
        grid.add(pendingCard, 0, 1);
        grid.add(profileCard, 1, 1);

        return grid;
    }

    private static VBox createActionCard(String title, String description,
                                         String buttonText, String color) {
        VBox card = new VBox(15);
        card.setPadding(new Insets(20));
        card.setPrefWidth(250);
        card.setPrefHeight(180);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                "-fx-border-radius: 10; -fx-border-color: #ddd; -fx-border-width: 1; " +
                "-fx-cursor: hand;");

        // Hover effect
        card.setOnMouseEntered(e ->
                card.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 10; " +
                        "-fx-border-radius: 10; -fx-border-color: " + color + "; -fx-border-width: 2; " +
                        "-fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 0);")
        );
        card.setOnMouseExited(e ->
                card.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                        "-fx-border-radius: 10; -fx-border-color: #ddd; -fx-border-width: 1; " +
                        "-fx-cursor: hand;")
        );

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        titleLabel.setStyle("-fx-text-fill: " + color + ";");

        Label descLabel = new Label(description);
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #666;");

        Button actionBtn = new Button(buttonText);
        actionBtn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-padding: 8 15 8 15;");
        actionBtn.setPrefWidth(150);

        card.getChildren().addAll(titleLabel, descLabel, actionBtn);

        return card;
    }

    private static VBox createPublishedBooksSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(15));
        section.getStyleClass().add("section-box");

        Label sectionTitle = new Label("📚 Your Recent Published Books");
        sectionTitle.setFont(Font.font("System", FontWeight.BOLD, 16));

        ListView<Book> bookList = new ListView<>();
        bookList.setPrefHeight(150);
        bookList.setCellFactory(lv -> new ListCell<Book>() {
            @Override
            protected void updateItem(Book book, boolean empty) {
                super.updateItem(book, empty);
                if (empty || book == null) {
                    setText(null);
                } else {
                    setText(book.getTitle() + " - " + book.getGenre() +
                            " (" + book.getPublishDate() + ")");
                }
            }
        });

        // Double-click to view details
        bookList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Book selected = bookList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    showBookDetails(selected);
                }
            }
        });

        section.getChildren().addAll(sectionTitle, bookList);

        return section;
    }

    private static VBox createPendingBooksSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(15));
        section.getStyleClass().add("section-box");

        Label sectionTitle = new Label("⏳ Pending Submissions");
        sectionTitle.setFont(Font.font("System", FontWeight.BOLD, 16));

        ListView<PendingBook> pendingList = new ListView<>();
        pendingList.setPrefHeight(120);
        pendingList.setCellFactory(lv -> new ListCell<PendingBook>() {
            @Override
            protected void updateItem(PendingBook book, boolean empty) {
                super.updateItem(book, empty);
                if (empty || book == null) {
                    setText(null);
                } else {
                    String status = book.getStatus();
                    String statusEmoji = status.equals("PENDING") ? "⏳" :
                            status.equals("APPROVED") ? "✅" : "❌";
                    setText(statusEmoji + " " + book.getTitle() + " - " +
                            book.getGenre() + " (" + status + ")");
                }
            }
        });

        section.getChildren().addAll(sectionTitle, pendingList);

        return section;
    }

    private static HBox createActionButtons() {
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(20, 0, 0, 0));

//        Button refreshBtn = new Button("🔄 Refresh Dashboard");
//        refreshBtn.getStyleClass().add("secondary-button");
//        refreshBtn.setOnAction(e -> refreshDashboardData());

        Button helpBtn = new Button("❓ Help");
        helpBtn.getStyleClass().add("secondary-button");
        helpBtn.setOnAction(e -> showHelp());

//        buttonBox.getChildren().addAll(refreshBtn, helpBtn);

        return buttonBox;
    }

//    private static void refreshDashboardData() {
//        try {
           // Get stats
//            List<Book> publishedBooks = BookService.getBooksByAuthor(currentAuthor.getId());
//            List<PendingBook> pendingBooks = PublishService.getBooksByAuthor(currentAuthor.getId());

//            long pendingCount = pendingBooks.stream()
//                    .filter(b -> "PENDING".equals(b.getStatus()))
//                    .count();
//            long approvedCount = pendingBooks.stream()
//                    .filter(b -> "APPROVED".equals(b.getStatus()))
//                    .count();

//            // Update stats
//            updateLabel("publishedCount", String.valueOf(publishedBooks.size()));
//            updateLabel("pendingCount", String.valueOf(pendingCount));
//            updateLabel("approvedCount", String.valueOf(approvedCount));

//            // Update book lists
//            if (publishedBooksBox != null) {
//                ListView<Book> bookList = (ListView<Book>) publishedBooksBox.getChildren().get(1);
//                bookList.getItems().setAll(publishedBooks);
//            }
//
//            if (pendingBooksBox != null) {
//                ListView<PendingBook> pendingList = (ListView<PendingBook>) pendingBooksBox.getChildren().get(1);
//                pendingList.getItems().setAll(pendingBooks);
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            showAlert("Error", "Failed to refresh dashboard data");
//        }
//    }

    private static void updateLabel(String id, String value) {
        if (statsBox != null) {
            GridPane statsGrid = (GridPane) statsBox.getChildren().get(1);
            for (int i = 1; i < statsGrid.getChildren().size(); i += 2) {
                javafx.scene.Node node = statsGrid.getChildren().get(i);
                if (node instanceof Label && node.getId() != null && node.getId().equals(id)) {
                    ((Label) node).setText(value);
                    break;
                }
            }
        }
    }

//    private static void showMyBooks() {
//        // Create a popup or navigate to a detailed view
//        Alert alert = new Alert(Alert.AlertType.INFORMATION);
//        alert.setTitle("My Books");
//        alert.setHeaderText("Your Published Books");

//        List<Book> books = BookService.getBooksByAuthor(currentAuthor.getId());
//        if (books.isEmpty()) {
//            alert.setContentText("You haven't published any books yet.");
//        } else {
//            StringBuilder content = new StringBuilder();
//            for (Book book : books) {
//                content.append("• ").append(book.getTitle())
//                        .append(" (").append(book.getGenre()).append(")\n");
//            }
//            alert.setContentText(content.toString());
//        }
//
//        alert.showAndWait();
//    }

//    private static void showPendingSubmissions() {
//        Alert alert = new Alert(Alert.AlertType.INFORMATION);
//        alert.setTitle("Pending Submissions");
//        alert.setHeaderText("Books Awaiting Approval");
//
//        List<PendingBook> pending = PublishService.getBooksByAuthor(currentAuthor.getId());
//        if (pending.isEmpty()) {
//            alert.setContentText("You have no pending submissions.");
//        } else {
//            StringBuilder content = new StringBuilder();
//            for (PendingBook book : pending) {
//                content.append("• ").append(book.getTitle())
//                        .append(" - Status: ").append(book.getStatus());
//                if (book.getReviewNotes() != null && !book.getReviewNotes().isEmpty()) {
//                    content.append("\n  Notes: ").append(book.getReviewNotes());
//                }
//                content.append("\n");
//            }
//            alert.setContentText(content.toString());
//        }
//
//        alert.showAndWait();
//    }

    private static void showEditProfile() {
        // This would navigate to an edit profile screen
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Edit Profile");
        alert.setHeaderText("Profile Information");
        alert.setContentText("Full Name: " + currentAuthor.getFullName() + "\n" +
                "Username: " + currentAuthor.getUsername() + "\n" +
                "Bio: " + (currentAuthor.getBio() != null ?
                currentAuthor.getBio() : "No bio provided"));
        alert.showAndWait();
    }

    private static void showBookDetails(Book book) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Book Details");
        alert.setHeaderText(book.getTitle());
        alert.setContentText(
                "Author: " + book.getAuthorFullNameSnapshot() + "\n" +
                        "Genre: " + book.getGenre() + "\n" +
                        "Published: " + book.getPublishDate() + "\n" +
                        "Availability: " + book.getAvailability() + "\n\n" +
                        "Summary:\n" + book.getSummary()
        );
        alert.showAndWait();
    }

    private static void showHelp() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Help");
        alert.setHeaderText("Author Dashboard Guide");
        alert.setContentText(
                "📖 Publish New Book: Submit a new book for librarian approval\n" +
                        "📚 My Published Books: View all your approved books\n" +
                        "⏳ Pending Submissions: Track books waiting for approval\n" +
                        "👤 Edit Profile: Update your personal information\n\n" +
                        "Double-click any book to view details"
        );
        alert.showAndWait();
    }

    private static void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}