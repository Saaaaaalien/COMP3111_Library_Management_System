package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.control.ContentDisplay;
import org.example.app.Navigator;
import org.example.db.NotificationDao;
import org.example.db.PublishDraftDao;
import org.example.domain.User;

import java.sql.SQLException;

import java.util.Optional;

public final class AuthorDashboardScreen {

    private static User currentUser;
    private static Navigator navigator;

    private AuthorDashboardScreen() {}

    public static Scene create(Navigator nav, User user) {
        navigator = nav;
        currentUser = user;

        // Main layout
        BorderPane mainPane = new BorderPane();
        mainPane.setPadding(new Insets(30));
        mainPane.setStyle("-fx-background-color: #f5f5f5;");

        // Top: Welcome header
        mainPane.setTop(createHeader());

        // Center: Scrollable content
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(createMainContent());
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); // Horizontal scroll never needed
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: #f5f5f5; -fx-background-color: #f5f5f5;");

        mainPane.setCenter(scrollPane);

        Scene scene = new Scene(mainPane, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        // CSS
        java.net.URL cssResource = AuthorDashboardScreen.class.getResource("/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }

        return scene;
    }

    private static HBox createHeader() {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 30, 0));

        // Welcome message
        VBox welcomeBox = new VBox(5);
        Label welcomeLabel = new Label("Welcome, " + currentUser.getFullName() + "!");
        welcomeLabel.setFont(Font.font("System", FontWeight.BOLD, 28));
        welcomeLabel.setStyle("-fx-text-fill: #2c3e50;");

        Label userInfo = new Label(" | @" + currentUser.getUsername() + ": " + currentUser.getBio());
        userInfo.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 14px;");

        welcomeBox.getChildren().addAll(welcomeLabel, userInfo);

        // Logout button (right-aligned)
        Button logoutBtn = new Button("Logout");
        logoutBtn.setStyle(
                "-fx-background-color: #a8a5a5;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 14px;" +
                        "-fx-padding: 10 20 10 20;" +
                        "-fx-background-radius: 5;"
        );
        logoutBtn.setPrefWidth(120);

        // Hover effect
        logoutBtn.setOnMouseEntered(e ->
                logoutBtn.setStyle(
                        "-fx-background-color: #7a7373;" +
                                "-fx-text-fill: white;" +
                                "-fx-font-weight: bold;" +
                                "-fx-font-size: 14px;" +
                                "-fx-padding: 10 20 10 20;" +
                                "-fx-background-radius: 5;"
                )
        );
        logoutBtn.setOnMouseExited(e ->
                logoutBtn.setStyle(
                        "-fx-background-color: #a8a5a5;" +
                                "-fx-text-fill: white;" +
                                "-fx-font-weight: bold;" +
                                "-fx-font-size: 14px;" +
                                "-fx-padding: 10 20 10 20;" +
                                "-fx-background-radius: 5;"
                )
        );

        logoutBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Logout");
            confirm.setHeaderText("Are you sure you want to logout?");
            confirm.setContentText("You'll need to login again to access your dashboard.");

            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                navigator.showWelcome();
            }
        });

        HBox rightBox = new HBox(logoutBtn);
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(rightBox, javafx.scene.layout.Priority.ALWAYS);

        header.getChildren().addAll(welcomeBox, rightBox);

        return header;
    }

    private static VBox createMainContent() {
        VBox content = new VBox(40);
        content.setAlignment(Pos.TOP_CENTER);

        // Welcome message card
        VBox welcomeCard = createInfoCard();

        HBox phase2Links = new HBox(12);
        phase2Links.setAlignment(Pos.CENTER);
        Button myBooksBtn = new Button("My Books");
        myBooksBtn.getStyleClass().add("secondary-button");
        myBooksBtn.setPrefWidth(140);
        myBooksBtn.setOnAction(e -> navigator.showAuthorPublishedBooks(currentUser));

        Button profileBtn = new Button("Profile");
        profileBtn.getStyleClass().add("secondary-button");
        profileBtn.setPrefWidth(140);
        profileBtn.setOnAction(e -> navigator.showAuthorProfile(currentUser));

        Button notifBtn = new Button("Notifications");
        notifBtn.getStyleClass().add("secondary-button");
        notifBtn.setPrefWidth(140);
        try {
            int n = NotificationDao.countUnread(currentUser.getId());
            notifBtn.setText(n > 0 ? "Notifications (" + n + ")" : "Notifications");
            if (n > 0) {
                Circle dot = new Circle(6, Color.web("#e74c3c"));
                notifBtn.setGraphic(dot);
                notifBtn.setContentDisplay(ContentDisplay.RIGHT);
                notifBtn.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 2; -fx-background-color: white; -fx-text-fill: #2c3e50;");
            }
        } catch (SQLException ignored) {
        }
        notifBtn.setOnAction(e -> navigator.showAuthorNotifications(currentUser));
        phase2Links.getChildren().addAll(myBooksBtn, profileBtn, notifBtn);

        // Publish Book Card
        VBox publishCard = createPublishBookCard();

        // Add some extra space at the bottom to ensure scrolling works well
        Label bottomSpacer = new Label("");
        bottomSpacer.setPrefHeight(50);

        content.getChildren().addAll(welcomeCard, phase2Links, publishCard, bottomSpacer);

        return content;
    }

    private static VBox createInfoCard() {
        VBox card = new VBox(15);
        card.setPadding(new Insets(25));
        card.setStyle(
                "-fx-background-color: white;" +
                        "-fx-background-radius: 15;" +
                        "-fx-border-radius: 15;" +
                        "-fx-border-color: #3498db;" +
                        "-fx-border-width: 2;" +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 0);"
        );

        Label title = new Label("📚 Author Dashboard");
        title.setFont(Font.font("System", FontWeight.BOLD, 22));
        title.setStyle("-fx-text-fill: #2c3e50;");

        Label message = new Label(
                "From here you can publish new books that will be reviewed by librarians before being added to the library."
        );
        message.setWrapText(true);
        message.setStyle("-fx-text-fill: #34495e; -fx-font-size: 14px;");

        card.getChildren().addAll(title, message);

        return card;
    }

    private static VBox createPublishBookCard() {
        VBox card = new VBox(25);
        card.setPadding(new Insets(40));
        card.setMaxWidth(500);
        card.setAlignment(Pos.CENTER);
        card.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #5bb2f5, #58a9d5);" +
                        "-fx-background-radius: 5;"
        );

        // Hover effect
        card.setOnMouseEntered(e ->
                card.setStyle(
                        "-fx-background-color: linear-gradient(to bottom right, #5bb2f5, #5bb2f5);" +
                                "-fx-cursor: hand;"+
                                "-fx-background-radius: 5;"
                )
        );
        card.setOnMouseExited(e ->
                card.setStyle(
                        "-fx-background-color: linear-gradient(to bottom right, #5bb2f5, #58a9d5);"+
                                "-fx-background-radius: 5;"
                )
        );

        // publish button
        Label titleLabel = new Label("Publish New Book");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 28));
        titleLabel.setStyle("-fx-text-fill: white;");

        Label descriptionLabel = new Label("Submit a new book for librarian review."
        );
        descriptionLabel.setWrapText(true);
        descriptionLabel.setPrefWidth(Double.MAX_VALUE);
        descriptionLabel.setAlignment(Pos.CENTER);
        descriptionLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px;");

        Button publishBtn = new Button("Start Publishing →");
        publishBtn.setStyle(
                "-fx-background-color: white;" +
                        "-fx-text-fill: #5bb2f5;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 18px;" +
                        "-fx-padding: 15 30 15 30;" +
                        "-fx-background-radius: 5;" +
                        "-fx-cursor: hand;"
        );
        publishBtn.setPrefWidth(250);

        // Hover effect for button
        publishBtn.setOnMouseEntered(e ->
                publishBtn.setStyle(
                        "-fx-background-color: #f8f8f8;" +
                                "-fx-text-fill: #5bb2f5;" +
                                "-fx-font-weight: bold;" +
                                "-fx-font-size: 18px;" +
                                "-fx-padding: 15 30 15 30;" +
                                "-fx-cursor: hand;"
                )
        );
        publishBtn.setOnMouseExited(e ->
                publishBtn.setStyle(
                        "-fx-background-color: white;" +
                                "-fx-text-fill: #5bb2f5;" +
                                "-fx-font-weight: bold;" +
                                "-fx-font-size: 18px;" +
                                "-fx-padding: 15 30 15 30;"
                )
        );

        // Add click handlers to both card and button for better UX
        card.setOnMouseClicked(e -> {
            try {
                if (PublishDraftDao.findByAuthor(currentUser.getId()).isPresent()) {
                    Alert info = new Alert(Alert.AlertType.INFORMATION);
                    info.setTitle("Draft Loaded");
                    info.setHeaderText(null);
                    info.setContentText("A previously saved draft was found and will be loaded into the publishing form.");
                    info.showAndWait();
                }
            } catch (SQLException ex) {
                // ignore and continue
            }
            System.out.println("Publish card clicked - navigating to PublishBookScreen");
            navigator.showPublishBook(currentUser);
        });

        publishBtn.setOnAction(e -> {
            try {
                if (PublishDraftDao.findByAuthor(currentUser.getId()).isPresent()) {
                    Alert info = new Alert(Alert.AlertType.INFORMATION);
                    info.setTitle("Draft Loaded");
                    info.setHeaderText(null);
                    info.setContentText("A previously saved draft was found and will be loaded into the publishing form.");
                    info.showAndWait();
                }
            } catch (SQLException ex) {
                // ignore and continue
            }
            System.out.println("Publish button clicked - navigating to PublishBookScreen");
            navigator.showPublishBook(currentUser);
        });

        card.getChildren().addAll(titleLabel, descriptionLabel, publishBtn);

        return card;
    }
}