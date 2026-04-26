package org.example.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.example.app.Navigator;
import org.example.db.BookReviewDao;
import org.example.db.NotificationDao;
import org.example.domain.User;
import org.example.service.NotificationService;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * Author-side review handling (view, reply, flag).
 */
public final class AuthorReviewsScreen {

    private AuthorReviewsScreen() {}

    public static Scene create(Navigator navigator, User authorUser) {
        Label title = new Label("Review Handling");
        title.getStyleClass().add("screen-title");
        Label subtitle = new Label("Review and respond to feedback on your books.");

        Button backBtn = new Button("Back");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> navigator.showAuthorDashboard(authorUser));

        TableView<BookReviewDao.AuthorVisibleReview> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<BookReviewDao.AuthorVisibleReview, String> bookCol = new TableColumn<>("Book");
        bookCol.setCellValueFactory(new PropertyValueFactory<>("bookTitle"));
        bookCol.setPrefWidth(150);

        TableColumn<BookReviewDao.AuthorVisibleReview, String> reviewerCol = new TableColumn<>("Reviewer");
        reviewerCol.setCellValueFactory(new PropertyValueFactory<>("reviewerName"));
        reviewerCol.setPrefWidth(130);

        TableColumn<BookReviewDao.AuthorVisibleReview, Integer> ratingCol = new TableColumn<>("Rating");
        ratingCol.setCellValueFactory(new PropertyValueFactory<>("rating"));
        ratingCol.setPrefWidth(70);

        TableColumn<BookReviewDao.AuthorVisibleReview, String> reviewCol = new TableColumn<>("Review");
        reviewCol.setCellValueFactory(new PropertyValueFactory<>("reviewText"));
        reviewCol.setPrefWidth(260);

        TableColumn<BookReviewDao.AuthorVisibleReview, String> createdCol = new TableColumn<>("Created");
        createdCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        createdCol.setPrefWidth(160);

        TableColumn<BookReviewDao.AuthorVisibleReview, String> replyCol = new TableColumn<>("Reply Status");
        replyCol.setCellValueFactory(cell -> {
            BookReviewDao.AuthorVisibleReview row = cell.getValue();
            String status = row.hasReply() ? "Replied" : "Pending reply";
            return new javafx.beans.property.SimpleStringProperty(status);
        });
        replyCol.setPrefWidth(120);

        table.getColumns().addAll(bookCol, reviewerCol, ratingCol, reviewCol, createdCol, replyCol);

        Runnable refresh = () -> {
            try {
                table.setItems(FXCollections.observableArrayList(BookReviewDao.findVisibleForAuthor(authorUser.getId())));
            } catch (SQLException ex) {
                table.setItems(FXCollections.observableArrayList());
                new Alert(Alert.AlertType.ERROR, "Could not load reviews.").showAndWait();
            }
        };
        refresh.run();

        Button replyBtn = new Button("Reply");
        replyBtn.getStyleClass().add("primary-button");
        replyBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        replyBtn.setOnAction(e -> {
            BookReviewDao.AuthorVisibleReview selected = table.getSelectionModel().getSelectedItem();
            if (selected == null || selected.isFlagged()) {
                return;
            }
            Optional<String> replyOpt = promptReply(selected.authorReplyText());
            if (replyOpt.isEmpty()) {
                return;
            }
            String replyText = replyOpt.get().trim();
            if (replyText.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Reply cannot be empty.").showAndWait();
                return;
            }
            try {
                String now = Instant.now().toString();
                boolean updated = BookReviewDao.replyToReview(selected.id(), authorUser.getId(), replyText, now);
                if (!updated) {
                    new Alert(Alert.AlertType.WARNING, "Review is no longer editable.").showAndWait();
                    refresh.run();
                    return;
                }
                String excerpt = replyText.length() > 120 ? replyText.substring(0, 120) + "..." : replyText;
                NotificationDao.insert(
                        selected.reviewerUserId(),
                        NotificationService.CAT_AUTHOR_REVIEW_REPLY,
                        "Author replied to your review",
                        "Book: \"" + selected.bookTitle() + "\"\nReply: " + excerpt,
                        now,
                        1,
                        null
                );
                refresh.run();
                new Alert(Alert.AlertType.INFORMATION, "Reply posted and reviewer notified.").showAndWait();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not save reply.").showAndWait();
            }
        });

        Button flagBtn = new Button("Flag");
        flagBtn.getStyleClass().add("secondary-button");
        flagBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        flagBtn.setOnAction(e -> {
            BookReviewDao.AuthorVisibleReview selected = table.getSelectionModel().getSelectedItem();
            if (selected == null || selected.isFlagged()) {
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Flag review");
            confirm.setHeaderText("Flag and hide this review?");
            confirm.setContentText("This review will be removed from your author review list.");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
            try {
                String now = Instant.now().toString();
                boolean updated = BookReviewDao.flagByAuthor(selected.id(), authorUser.getId(), now);
                if (updated) {
                    NotificationDao.insert(
                            authorUser.getId(),
                            NotificationService.CAT_AUTHOR_REVIEW_FLAGGED,
                            "Review flagged",
                            "Review flagged by you and removed from your review list.",
                            now,
                            1,
                            null
                    );
                }
                refresh.run();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not flag review.").showAndWait();
            }
        });

        HBox actions = new HBox(10, replyBtn, flagBtn);
        VBox tableBox = new VBox(10, table, actions);
        VBox.setVgrow(table, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));
        root.getStyleClass().add("app-root");
        root.setTop(new VBox(8, backBtn, title, subtitle));
        root.setCenter(tableBox);

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        var css = AuthorReviewsScreen.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }

    private static Optional<String> promptReply(String initialValue) {
        javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Reply to Review");
        dialog.setHeaderText("Send reply to reviewer");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextArea area = new TextArea(initialValue == null ? "" : initialValue);
        area.setWrapText(true);
        area.setPrefRowCount(8);
        dialog.getDialogPane().setContent(area);
        dialog.setResultConverter(btn -> btn == ButtonType.OK ? area.getText() : null);
        return dialog.showAndWait();
    }
}
