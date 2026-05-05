package org.example.ui;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import org.example.app.Navigator;
import org.example.db.BookReviewDao;
import org.example.db.NotificationDao;
import org.example.domain.User;
import org.example.service.NotificationService;
import org.example.service.ReviewSentimentService;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Task 2.9 — author-side review handling: list feedback, reply templates, flag, AI/heuristic sentiment, and
 * aggregated analytics. Reader submission of ratings/reviews is not wired in the app yet; this screen still
 * compiles and runs with an empty table until rows exist (e.g. future student/staff UI or test data).
 */
public final class AuthorReviewsScreen {

    private AuthorReviewsScreen() {}

    public static Scene create(Navigator navigator, User authorUser) {
        Label title = new Label("Review Handling");
        title.getStyleClass().add("screen-title");
        Label subtitle = new Label(
                "Review and respond to feedback on your books, then track reply and sentiment status.");

        // Navigation handled by global menu; removed per-screen Back button

        Label analyticsStrip = new Label();
        analyticsStrip.setWrapText(true);
        analyticsStrip.getStyleClass().add("review-analytics-strip");

        TableView<BookReviewDao.AuthorVisibleReview> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(tv -> {
            TableRow<BookReviewDao.AuthorVisibleReview> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldV, review) -> {
                row.getStyleClass().removeAll(
                        "review-row-sentiment-positive",
                        "review-row-sentiment-neutral",
                        "review-row-sentiment-negative",
                        "review-row-sentiment-unclassified");
                if (review == null) {
                    return;
                }
                if (review.isSentimentUnclassified()) {
                    row.getStyleClass().add("review-row-sentiment-unclassified");
                } else {
                    String lab = review.sentimentLabel() == null ? "" : review.sentimentLabel().toLowerCase(Locale.ROOT);
                    switch (lab) {
                        case "positive" -> row.getStyleClass().add("review-row-sentiment-positive");
                        case "negative" -> row.getStyleClass().add("review-row-sentiment-negative");
                        case "neutral" -> row.getStyleClass().add("review-row-sentiment-neutral");
                        default -> row.getStyleClass().add("review-row-sentiment-unclassified");
                    }
                }
            });
            return row;
        });

        TableColumn<BookReviewDao.AuthorVisibleReview, String> bookCol = new TableColumn<>("Book");
        bookCol.setCellValueFactory(c -> {
            BookReviewDao.AuthorVisibleReview r = c.getValue();
            return new SimpleStringProperty(r == null ? "" : safe(r.bookTitle()));
        });
        bookCol.setPrefWidth(150);

        TableColumn<BookReviewDao.AuthorVisibleReview, String> reviewerCol = new TableColumn<>("Reviewer");
        reviewerCol.setCellValueFactory(c -> {
            BookReviewDao.AuthorVisibleReview r = c.getValue();
            return new SimpleStringProperty(r == null ? "" : safe(r.reviewerName()));
        });
        reviewerCol.setPrefWidth(130);

        TableColumn<BookReviewDao.AuthorVisibleReview, Integer> ratingCol = new TableColumn<>("Rating");
        ratingCol.setCellValueFactory(c -> {
            BookReviewDao.AuthorVisibleReview r = c.getValue();
            return new SimpleIntegerProperty(r == null ? 0 : r.rating()).asObject();
        });
        ratingCol.setPrefWidth(70);

        TableColumn<BookReviewDao.AuthorVisibleReview, String> sentimentCol = new TableColumn<>("Sentiment");
        sentimentCol.setPrefWidth(110);
        sentimentCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll(
                        "review-sentiment-positive",
                        "review-sentiment-neutral",
                        "review-sentiment-negative",
                        "review-sentiment-unclassified");
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    return;
                }
                BookReviewDao.AuthorVisibleReview r = getTableRow().getItem();
                if (r.isSentimentUnclassified()) {
                    setText("Unclassified");
                    getStyleClass().add("review-sentiment-unclassified");
                } else {
                    String lab = r.sentimentLabel() == null ? "" : r.sentimentLabel();
                    String src = r.sentimentSource() == null || r.sentimentSource().isBlank()
                            ? ""
                            : " (" + r.sentimentSource() + ")";
                    setText(capitalizeWord(lab) + src);
                    switch (lab.toLowerCase(Locale.ROOT)) {
                        case "positive" -> getStyleClass().add("review-sentiment-positive");
                        case "negative" -> getStyleClass().add("review-sentiment-negative");
                        case "neutral" -> getStyleClass().add("review-sentiment-neutral");
                        default -> getStyleClass().add("review-sentiment-unclassified");
                    }
                }
            }
        });
        sentimentCol.setCellValueFactory(c -> {
            BookReviewDao.AuthorVisibleReview r = c.getValue();
            if (r == null) {
                return new javafx.beans.property.SimpleStringProperty("");
            }
            return new javafx.beans.property.SimpleStringProperty(
                    r.isSentimentUnclassified() ? "Unclassified" : r.sentimentLabel());
        });

        TableColumn<BookReviewDao.AuthorVisibleReview, String> reviewCol = new TableColumn<>("Review");
        reviewCol.setCellValueFactory(c -> {
            BookReviewDao.AuthorVisibleReview r = c.getValue();
            return new SimpleStringProperty(r == null ? "" : safe(r.reviewText()));
        });
        reviewCol.setPrefWidth(220);

        TableColumn<BookReviewDao.AuthorVisibleReview, String> createdCol = new TableColumn<>("Created");
        createdCol.setCellValueFactory(c -> {
            BookReviewDao.AuthorVisibleReview r = c.getValue();
            return new SimpleStringProperty(r == null ? "" : safe(r.createdAt()));
        });
        createdCol.setPrefWidth(160);

        TableColumn<BookReviewDao.AuthorVisibleReview, String> replyCol = new TableColumn<>("Reply Status");
        replyCol.setCellValueFactory(cell -> {
            BookReviewDao.AuthorVisibleReview row = cell.getValue();
            if (row == null) {
                return new javafx.beans.property.SimpleStringProperty("");
            }
            String status = row.hasReply() ? "Replied" : "Pending reply";
            return new javafx.beans.property.SimpleStringProperty(status);
        });
        replyCol.setPrefWidth(120);

        TableColumn<BookReviewDao.AuthorVisibleReview, String> authorReplyCol = new TableColumn<>("Author Reply");
        authorReplyCol.setCellValueFactory(cell -> {
            BookReviewDao.AuthorVisibleReview row = cell.getValue();
            if (row == null || row.authorReplyText() == null || row.authorReplyText().isBlank()) {
                return new javafx.beans.property.SimpleStringProperty("-");
            }
            String raw = row.authorReplyText().trim();
            String compact = raw.replaceAll("\\s+", " ");
            String text = compact.length() > 80 ? compact.substring(0, 80) + "..." : compact;
            return new javafx.beans.property.SimpleStringProperty(text);
        });
        authorReplyCol.setPrefWidth(220);

        table.getColumns().addAll(bookCol, reviewerCol, ratingCol, sentimentCol, reviewCol, authorReplyCol, createdCol, replyCol);

        Runnable refresh = () -> {
            try {
                table.setItems(FXCollections.observableArrayList(BookReviewDao.findVisibleForAuthor(authorUser.getId())));
                table.sort();
                BookReviewDao.FeedbackAnalytics a = BookReviewDao.loadFeedbackAnalytics(authorUser.getId());
                analyticsStrip.setText(formatFeedbackAnalytics(a));
            } catch (SQLException ex) {
                table.setItems(FXCollections.observableArrayList());
                analyticsStrip.setText("Analytics unavailable.");
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

        Button analyzeBtn = new Button("Analyze sentiments");
        analyzeBtn.getStyleClass().add("secondary-button");
        analyzeBtn.setOnAction(e -> runSentimentAnalysis(analyzeBtn, authorUser.getId(), refresh));

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> refresh.run());

        HBox actions = new HBox(10, replyBtn, flagBtn, analyzeBtn, refreshBtn);
        VBox analyticsBox = new VBox(6, new Label("Feedback summary"), analyticsStrip);
        analyticsBox.getStyleClass().add("review-analytics-box");
        VBox tableBox = new VBox(12, analyticsBox, table, actions);
        VBox.setVgrow(table, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));
        root.getStyleClass().add("app-root");
        root.setTop(new VBox(8, title, subtitle));
        root.setCenter(tableBox);

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        var css = AuthorReviewsScreen.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }

    /**
     * Runs {@link ReviewSentimentService} and JDBC updates off the JavaFX thread for rows that are still missing
     * {@code sentiment_label}, then refreshes the UI on the FX thread.
     */
    private static void runSentimentAnalysis(Button analyzeBtn, long authorUserId, Runnable refresh) {
        final long sentimentOwnerId = authorUserId;
        analyzeBtn.setDisable(true);
        String originalLabel = analyzeBtn.getText();
        analyzeBtn.setText("Analyzing…");

        Task<SentimentBatchResult> task = new Task<>() {
            @Override
            protected SentimentBatchResult call() throws SQLException {
                List<BookReviewDao.AuthorVisibleReview> rows = BookReviewDao.findVisibleForAuthor(sentimentOwnerId);
                ReviewSentimentService service = new ReviewSentimentService();
                int processed = 0;
                int skipped = 0;
                int failed = 0;
                for (BookReviewDao.AuthorVisibleReview row : rows) {
                    if (!row.isSentimentUnclassified()) {
                        skipped++;
                        continue;
                    }
                    ReviewSentimentService.SentimentResult result =
                            service.classify(row.reviewText() == null ? "" : row.reviewText(), row.rating());
                    boolean ok = BookReviewDao.updateSentimentForAuthor(
                            row.id(), sentimentOwnerId, result.label(), result.source());
                    if (ok) {
                        processed++;
                    } else {
                        failed++;
                    }
                }
                return new SentimentBatchResult(processed, skipped, failed);
            }
        };

        task.setOnSucceeded(ev -> {
            analyzeBtn.setText(originalLabel);
            analyzeBtn.setDisable(false);
            SentimentBatchResult r = task.getValue();
            refresh.run();
            String msg = "Classified " + r.processed() + " review(s). "
                    + r.skipped() + " already had sentiment."
                    + (r.failed() > 0 ? " " + r.failed() + " update(s) could not be applied." : "");
            new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
        });

        task.setOnFailed(ev -> {
            analyzeBtn.setText(originalLabel);
            analyzeBtn.setDisable(false);
            Throwable ex = task.getException();
            String detail = ex == null ? "Unknown error." : ex.getMessage();
            refresh.run();
            new Alert(Alert.AlertType.WARNING, "Sentiment analysis did not complete: " + detail).showAndWait();
        });

        Thread worker = new Thread(task, "review-sentiment-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private record SentimentBatchResult(int processed, int skipped, int failed) {}

    private static String formatFeedbackAnalytics(BookReviewDao.FeedbackAnalytics a) {
        if (a.totalReviews() == 0) {
            return "No visible reviews yet. Once reviews are available, star and sentiment analytics will appear here.";
        }
        String avg = String.format(Locale.US, "%.2f", a.averageRating());
        return String.format(Locale.US,
                "Total: %d · Avg rating: %s / 5 · Stars: ★1=%d ★2=%d ★3=%d ★4=%d ★5=%d · Sentiment: positive=%d, neutral=%d, negative=%d, unclassified=%d",
                a.totalReviews(),
                avg,
                a.star1Count(),
                a.star2Count(),
                a.star3Count(),
                a.star4Count(),
                a.star5Count(),
                a.sentimentPositiveCount(),
                a.sentimentNeutralCount(),
                a.sentimentNegativeCount(),
                a.sentimentUnclassifiedCount());
    }

    private static String capitalizeWord(String s) {
        if (s == null || s.isBlank()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static Optional<String> promptReply(String initialValue) {
        javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Reply to Review");
        dialog.setHeaderText("Send reply to reviewer");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> templateCombo = new ComboBox<>();
        templateCombo.setPromptText("Canned reply (optional)");
        templateCombo.setMaxWidth(Double.MAX_VALUE);
        templateCombo.getItems().addAll(
                "Thank you for your thoughtful review — I really appreciate you taking the time.",
                "Thanks for reading! I'm glad parts of the book resonated with you.",
                "Thank you for the honest feedback; I'll keep it in mind for future work.",
                "I appreciate the critique and the chance to improve — thank you.",
                "Thanks for the rating! If you continue reading, I hope the rest of the story pulls you in."
        );
        HBox.setHgrow(templateCombo, Priority.ALWAYS);

        TextArea area = new TextArea(initialValue == null ? "" : initialValue);
        area.setWrapText(true);
        area.setPrefRowCount(8);

        Button insertBtn = new Button("Insert");
        insertBtn.getStyleClass().add("secondary-button");
        insertBtn.setOnAction(ev -> {
            String t = templateCombo.getSelectionModel().getSelectedItem();
            if (t == null || t.isBlank()) {
                return;
            }
            javafx.scene.control.IndexRange sel = area.getSelection();
            int start = sel.getStart();
            int end = sel.getEnd();
            String cur = area.getText();
            String before = cur.substring(0, start);
            String after = cur.substring(end);
            String insert = (start > 0 && !before.endsWith("\n") && !before.isEmpty() ? "\n\n" : "") + t;
            area.setText(before + insert + after);
            area.positionCaret(before.length() + insert.length());
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox templateRow = new HBox(8, templateCombo, insertBtn, spacer);

        VBox content = new VBox(10, templateRow, area);
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(btn -> btn == ButtonType.OK ? area.getText() : null);
        return dialog.showAndWait();
    }
}
