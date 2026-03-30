package org.example.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.app.Navigator;
import org.example.db.BorrowDao;
import org.example.db.ReadingHighlightDao;
import org.example.db.ReadingProgressDao;
import org.example.domain.Borrow;
import org.example.domain.User;
import org.example.service.BorrowService;
import org.example.util.PdfReaderUtil;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/**
 * Borrowed-book PDF reader: rendered pages + per-page extracted text so users can select text and save highlights.
 * (True in-browser PDF.js selection is an optional upgrade documented in the Phase 2 plan.)
 */
public final class PdfReaderScreen {

    private PdfReaderScreen() {}

    public static void open(Navigator navigator, User user, long borrowId, long bookId, String title, String filePath) {
        if (filePath == null || !filePath.toLowerCase().endsWith(".pdf")) {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setContentText("Only PDF files can be opened in the reader.");
            a.showAndWait();
            return;
        }
        Path path = Path.of(filePath);
        if (!java.nio.file.Files.isRegularFile(path)) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setContentText("Book file not found on disk.");
            a.showAndWait();
            return;
        }

        Stage readerStage = new Stage();
        readerStage.initOwner(navigator.getStage());
        readerStage.setTitle("Read: " + title);

        int[] pageCount = {0};
        int[] currentPage = {0};
        try {
            pageCount[0] = PdfReaderUtil.getPageCount(path);
        } catch (Exception e) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setContentText("Could not open PDF: " + e.getMessage());
            a.showAndWait();
            return;
        }
        if (pageCount[0] <= 0) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setContentText("PDF has no pages.");
            a.showAndWait();
            return;
        }

        try {
            ReadingProgressDao.getLastPage(borrowId).ifPresent(p -> currentPage[0] = Math.min(Math.max(0, p), pageCount[0] - 1));
        } catch (SQLException ignored) {
        }

        ImageView pageView = new ImageView();
        pageView.setPreserveRatio(true);
        pageView.setFitWidth(520);

        TextArea textPane = new TextArea();
        textPane.setWrapText(true);
        textPane.setPrefRowCount(18);
        textPane.setPromptText("Select text here and click \"Save selection as highlight\".");

        ListView<ReadingHighlightDao.HighlightRow> highlightList = new ListView<>();
        highlightList.setPrefHeight(120);
        highlightList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ReadingHighlightDao.HighlightRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText("p." + (item.pageIndex() + 1) + ": " + truncateHighlightPreview(item.highlightText()));
                }
            }
        });

        Runnable saveProgress = () -> {
            try {
                ReadingProgressDao.upsert(borrowId, user.getId(), bookId, currentPage[0], null, Instant.now().toString());
            } catch (SQLException ignored) {
            }
        };

        Runnable reloadHighlights = () -> {
            try {
                List<ReadingHighlightDao.HighlightRow> rows = ReadingHighlightDao.findByBorrow(borrowId);
                highlightList.setItems(FXCollections.observableArrayList(rows));
            } catch (SQLException ex) {
                highlightList.setItems(FXCollections.observableArrayList());
            }
        };

        Runnable showPage = () -> {
            try {
                var img = PdfReaderUtil.renderPage(path, currentPage[0], 1.2f);
                pageView.setImage(img);
                textPane.setText(PdfReaderUtil.extractPageText(path, currentPage[0]));
            } catch (Exception ex) {
                textPane.setText("Could not load page: " + ex.getMessage());
            }
            reloadHighlights.run();
        };

        Label pageLabel = new Label();
        Runnable updatePageLabel = () -> pageLabel.setText("Page " + (currentPage[0] + 1) + " / " + pageCount[0]);

        Button prevBtn = new Button("Previous");
        Button nextBtn = new Button("Next");
        prevBtn.setOnAction(e -> {
            if (currentPage[0] > 0) {
                saveProgress.run();
                currentPage[0]--;
                showPage.run();
                updatePageLabel.run();
            }
        });
        nextBtn.setOnAction(e -> {
            if (currentPage[0] < pageCount[0] - 1) {
                saveProgress.run();
                currentPage[0]++;
                showPage.run();
                updatePageLabel.run();
            }
        });

        Button addHlBtn = new Button("Save selection as highlight");
        addHlBtn.setOnAction(e -> {
            String sel = textPane.getSelectedText();
            if (sel == null || sel.isBlank()) {
                new Alert(Alert.AlertType.WARNING, "Select text in the pane first.").showAndWait();
                return;
            }
            try {
                ReadingHighlightDao.insert(borrowId, user.getId(), currentPage[0], sel.trim(), Instant.now().toString());
                reloadHighlights.run();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not save highlight.").showAndWait();
            }
        });

        Button delHlBtn = new Button("Remove highlight");
        delHlBtn.setOnAction(e -> {
            var sel = highlightList.getSelectionModel().getSelectedItem();
            if (sel == null) {
                return;
            }
            try {
                ReadingHighlightDao.delete(sel.id(), borrowId, user.getId());
                reloadHighlights.run();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not remove.").showAndWait();
            }
        });

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> {
            saveProgress.run();
            readerStage.close();
        });

        Runnable checkBorrowStillValid = () -> {
            try {
                var opt = BorrowDao.findById(borrowId);
                if (opt.isEmpty()) {
                    Platform.runLater(() -> {
                        readerStage.close();
                        new Alert(Alert.AlertType.INFORMATION, "This borrow is no longer active; the reader was closed.").showAndWait();
                    });
                    return;
                }
                Borrow b = opt.get();
                if (b.getReturnedAt() != null && !b.getReturnedAt().isEmpty()) {
                    Platform.runLater(() -> {
                        readerStage.close();
                        new Alert(Alert.AlertType.INFORMATION, "This book was returned; the reader was closed.").showAndWait();
                    });
                    return;
                }
                if (b.getDueAt() != null && !b.getDueAt().isEmpty()) {
                    try {
                        Instant due = Instant.parse(b.getDueAt());
                        if (!Instant.now().isBefore(due)) {
                            Platform.runLater(() -> {
                                try {
                                    BorrowService.processDueReturns();
                                } catch (SQLException ignored) {
                                }
                                readerStage.close();
                                new Alert(Alert.AlertType.WARNING, "The loan period ended; the book was auto-returned.").showAndWait();
                            });
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (SQLException ignored) {
            }
        };

        Timeline dueWatch = new Timeline(new KeyFrame(Duration.seconds(20), ev -> checkBorrowStillValid.run()));
        dueWatch.setCycleCount(Timeline.INDEFINITE);
        dueWatch.play();
        readerStage.setOnCloseRequest(ev -> {
            dueWatch.stop();
            saveProgress.run();
        });

        ScrollPane imgScroll = new ScrollPane(pageView);
        imgScroll.setFitToWidth(true);
        VBox left = new VBox(8, new Label("Page image"), imgScroll, new HBox(10, prevBtn, nextBtn, pageLabel));
        VBox.setVgrow(imgScroll, Priority.ALWAYS);

        VBox right = new VBox(8, new Label("Text on this page (for highlights)"), textPane, addHlBtn,
                new Label("Your highlights"), highlightList, delHlBtn);
        VBox.setVgrow(textPane, Priority.ALWAYS);

        HBox split = new HBox(16, left, right);
        split.setPadding(new Insets(12));
        HBox.setHgrow(right, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        Label header = new Label(title);
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        root.setTop(new VBox(4, header, new Label("Logged in as " + user.getFullName())));
        root.setCenter(split);
        root.setBottom(new HBox(10, closeBtn));
        BorderPane.setMargin(root.getBottom(), new Insets(10));

        Scene sc = new Scene(root, 980, 720);
        var css = PdfReaderScreen.class.getResource("/app.css");
        if (css != null) {
            sc.getStylesheets().add(css.toExternalForm());
        }
        readerStage.setScene(sc);
        readerStage.initModality(Modality.NONE);
        showPage.run();
        updatePageLabel.run();
        readerStage.show();
    }

    private static final int HIGHLIGHT_PREVIEW_MAX_CHARS = 80;

    private static String truncateHighlightPreview(String s) {
        if (s == null) {
            return "";
        }
        int max = HIGHLIGHT_PREVIEW_MAX_CHARS;
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
