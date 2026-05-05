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
import org.example.app.Navigator;
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

        HBox phase2Links = new HBox(12);
        phase2Links.setAlignment(Pos.CENTER);
        Button myBooksBtn = new Button("My Books");
        myBooksBtn.getStyleClass().add("secondary-button");
        myBooksBtn.setPrefWidth(140);
        myBooksBtn.setOnAction(e -> navigator.showAuthorPublishedBooks(currentUser));

        Button statsBtn = new Button("View Stats");
        statsBtn.getStyleClass().add("secondary-button");
        statsBtn.setPrefWidth(140);
        statsBtn.setOnAction(e -> navigator.showAuthorStats(currentUser));

        Button profileBtn = new Button("Profile");
        profileBtn.getStyleClass().add("secondary-button");
        profileBtn.setPrefWidth(140);
        profileBtn.setOnAction(e -> navigator.showAuthorProfile(currentUser));
        phase2Links.getChildren().addAll(myBooksBtn, profileBtn);

        // Author feature cards in one horizontal row
        HBox featureCards = new HBox(14,
                createPublishBookCard(),
                createActionCard("View Stats", "See your publishing performance and trends.", "Open Stats →",
                        "#535D65ED", "#687378FF", () -> navigator.showAuthorStats(currentUser)),
                createActionCard("Review Handling", "Read and manage reader reviews.", "Open Reviews →",
                        "#535D65ED", "#687378FF", () -> navigator.showAuthorReviews(currentUser))
        );
        featureCards.setAlignment(Pos.CENTER);

        // Add some extra space at the bottom to ensure scrolling works well
        Label bottomSpacer = new Label("");
        bottomSpacer.setPrefHeight(50);

        content.getChildren().addAll(phase2Links, featureCards, bottomSpacer);

        return content;
    }

    private static VBox createPublishBookCard() {
        VBox card = new VBox(18);
        card.setPadding(new Insets(28));
        card.setPrefWidth(260);
        card.setMinWidth(260);
        card.setMaxWidth(260);
        card.setAlignment(Pos.CENTER);
        card.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, rgba(83,93,101,0.93), #687378);" +
                        "-fx-background-radius: 5;"
        );

        // Hover effect
        card.setOnMouseEntered(e ->
                card.setStyle(
                        "-fx-background-color: linear-gradient(to bottom right, rgba(83,93,101,0.93), #687378);" +
                                "-fx-cursor: hand;"+
                                "-fx-background-radius: 5;"
                )
        );
        card.setOnMouseExited(e ->
                card.setStyle(
                        "-fx-background-color: linear-gradient(to bottom right, rgba(83,93,101,0.93), #687378);"+
                                "-fx-background-radius: 5;"
                )
        );

        Label titleLabel = new Label("Publish Book");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        titleLabel.setStyle("-fx-text-fill: white;");

        Label descriptionLabel = new Label("Submit a new book for librarian review."
        );
        descriptionLabel.setWrapText(true);
        descriptionLabel.setPrefWidth(220);
        descriptionLabel.setAlignment(Pos.CENTER);
        descriptionLabel.setStyle("-fx-text-fill: white; -fx-font-size: 15px;");

        Button publishBtn = new Button("Start Publishing →");
        publishBtn.setStyle(
                "-fx-background-color: white;" +
                        "-fx-text-fill: rgba(83,93,101,0.93);" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 16px;" +
                        "-fx-padding: 12 24 12 24;" +
                        "-fx-background-radius: 5;" +
                        "-fx-cursor: hand;"
        );
        publishBtn.setPrefWidth(180);

        // Hover effect for button
        publishBtn.setOnMouseEntered(e ->
                publishBtn.setStyle(
                        "-fx-background-color: #f8f8f8;" +
                                "-fx-text-fill: rgba(83,93,101,0.93);" +
                                "-fx-font-weight: bold;" +
                                "-fx-font-size: 16px;" +
                                "-fx-padding: 12 24 12 24;" +
                                "-fx-cursor: hand;"
                )
        );
        publishBtn.setOnMouseExited(e ->
                publishBtn.setStyle(
                        "-fx-background-color: white;" +
                                "-fx-text-fill: rgba(83,93,101,0.93);" +
                                "-fx-font-weight: bold;" +
                                "-fx-font-size: 16px;" +
                                "-fx-padding: 12 24 12 24;"
                )
        );

        // Add click handlers to both card and button for better UX
        card.setOnMouseClicked(e -> {
            try {
                if (hasMeaningfulDraft()) {
                    Alert info = new Alert(Alert.AlertType.INFORMATION);
                    info.setTitle("Draft Loaded");
                    info.setHeaderText(null);
                    info.setContentText("A previously saved draft was found and will be loaded into the publishing form.");
                    info.showAndWait();
                }
            } catch (SQLException ex) {
                // ignore and continue
            }
            navigator.showPublishBook(currentUser);
        });

        publishBtn.setOnAction(e -> {
            try {
                if (hasMeaningfulDraft()) {
                    Alert info = new Alert(Alert.AlertType.INFORMATION);
                    info.setTitle("Draft Loaded");
                    info.setHeaderText(null);
                    info.setContentText("A previously saved draft was found and will be loaded into the publishing form.");
                    info.showAndWait();
                }
            } catch (SQLException ex) {
                // ignore and continue
            }
            navigator.showPublishBook(currentUser);
        });

        card.getChildren().addAll(titleLabel, descriptionLabel, publishBtn);

        return card;
    }

    private static VBox createActionCard(String title, String description, String buttonText,
                                         String colorStart, String colorEnd, Runnable action) {
        VBox card = new VBox(18);
        card.setPadding(new Insets(28));
        card.setPrefWidth(260);
        card.setMinWidth(260);
        card.setMaxWidth(260);
        card.setAlignment(Pos.CENTER);
        card.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, " + colorStart + ", " + colorEnd + ");" +
                        "-fx-background-radius: 5;"
        );
        card.setOnMouseEntered(e -> card.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, " + colorStart + ", " + colorStart + ");" +
                        "-fx-cursor: hand;" +
                        "-fx-background-radius: 5;"
        ));
        card.setOnMouseExited(e -> card.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, " + colorStart + ", " + colorEnd + ");" +
                        "-fx-background-radius: 5;"
        ));

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        titleLabel.setStyle("-fx-text-fill: white;");

        Label descriptionLabel = new Label(description);
        descriptionLabel.setWrapText(true);
        descriptionLabel.setPrefWidth(220);
        descriptionLabel.setAlignment(Pos.CENTER);
        descriptionLabel.setStyle("-fx-text-fill: white; -fx-font-size: 15px;");

        Button actionBtn = new Button(buttonText);
        actionBtn.setStyle(
                "-fx-background-color: white;" +
                        "-fx-text-fill: " + colorStart + ";" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 16px;" +
                        "-fx-padding: 12 24 12 24;" +
                        "-fx-background-radius: 5;" +
                        "-fx-cursor: hand;"
        );
        actionBtn.setPrefWidth(180);
        actionBtn.setOnMouseEntered(e -> actionBtn.setStyle(
                "-fx-background-color: #f8f8f8;" +
                        "-fx-text-fill: " + colorStart + ";" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 16px;" +
                        "-fx-padding: 12 24 12 24;" +
                        "-fx-cursor: hand;"
        ));
        actionBtn.setOnMouseExited(e -> actionBtn.setStyle(
                "-fx-background-color: white;" +
                        "-fx-text-fill: " + colorStart + ";" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 16px;" +
                        "-fx-padding: 12 24 12 24;"
        ));

        card.setOnMouseClicked(e -> action.run());
        actionBtn.setOnAction(e -> action.run());

        card.getChildren().addAll(titleLabel, descriptionLabel, actionBtn);
        return card;
    }

    private static boolean hasMeaningfulDraft() throws SQLException {
        Optional<PublishDraftDao.Draft> draftOpt = PublishDraftDao.findByAuthor(currentUser.getId());
        if (draftOpt.isEmpty()) {
            return false;
        }
        PublishDraftDao.Draft d = draftOpt.get();
        return !isBlank(d.title())
                || !isBlank(d.genre())
                || !isBlank(d.summary())
                || !isBlank(d.filePath())
                || !isBlank(d.coverPath());
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}