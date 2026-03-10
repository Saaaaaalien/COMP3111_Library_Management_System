package org.example.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.app.Navigator;
import org.example.domain.Book;
import org.example.domain.User;
import org.example.service.BorrowService;
import org.example.util.BookPreviewUtil;

import javafx.animation.PauseTransition;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.example.db.BookDao;
import org.example.db.BorrowDao;

/**
 * Available books list for Student/Staff; borrow action with confirmation.
 * <p>
 * Displays all books with {@link org.example.domain.Availability#AVAILABLE} status in a table.
 * The user can select a book and trigger a borrow via {@link BorrowService#borrow(long, long)};
 * a confirmation dialog is shown before committing. An inactivity timer returns the user to the
 * Student/Staff portal after 15 minutes if there is no mouse or keyboard activity.
 */
public final class AvailableBooksScreen {

    /** Preferred width for the Abstract/Summary table column. */
    private static final int SUMMARY_PREF_WIDTH = 250;

    /** Character length above which the summary is shown in a pop-up for easier reading. */
    private static final int SUMMARY_POPUP_THRESHOLD = 200;

    private AvailableBooksScreen() {}

    /**
     * Builds the Available Books scene for the given logged-in user.
     *
     * @param navigator   application navigator for screen transitions and logout
     * @param currentUser the authenticated Student/Staff user (used for borrow and display)
     * @return the configured JavaFX {@link javafx.scene.Scene}
     */
    public static Scene create(Navigator navigator, User currentUser) {
        Label title = new Label("Available Books");
        title.getStyleClass().add("screen-title");

        TableView<BookRow> table = new TableView<>();
        ObservableList<BookRow> items = FXCollections.observableArrayList();
        table.setItems(items);

        TableColumn<BookRow, String> colTitle = new TableColumn<>("Title");
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colTitle.setPrefWidth(180);

        TableColumn<BookRow, String> colAuthor = new TableColumn<>("Author");
        colAuthor.setCellValueFactory(new PropertyValueFactory<>("author"));
        colAuthor.setPrefWidth(120);

        TableColumn<BookRow, String> colPublishDate = new TableColumn<>("Publish Date");
        colPublishDate.setCellValueFactory(new PropertyValueFactory<>("publishDateDisplay"));
        colPublishDate.setPrefWidth(100);

        TableColumn<BookRow, String> colAvailability = new TableColumn<>("Availability Status");
        colAvailability.setCellValueFactory(new PropertyValueFactory<>("availability"));
        colAvailability.setPrefWidth(90);

        TableColumn<BookRow, String> colSummary = new TableColumn<>("Abstract / Summary");
        colSummary.setCellValueFactory(new PropertyValueFactory<>("summary"));
        colSummary.setPrefWidth(SUMMARY_PREF_WIDTH);

        table.getColumns().addAll(List.of(colTitle, colAuthor, colPublishDate, colAvailability, colSummary));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Inactivity timer: return to portal after 15 minutes with no input
        PauseTransition inactivityTimer = new PauseTransition(Duration.minutes(15));
        inactivityTimer.setOnFinished(ev -> navigator.showStudentStaffPortal());

        // Load available books from DB and populate the table; run on init and after each borrow
        Runnable refresh = () -> {
            items.clear();
            try {
                List<Book> books = org.example.db.BookDao.findAllAvailable();
                for (Book b : books) {
                    items.add(new BookRow(b));
                }
            } catch (SQLException ex) {
                runWithTimerPaused(inactivityTimer,
                        () -> showAlert(Alert.AlertType.ERROR, "Error", "Could not load books."));
            }
        };
        refresh.run();

        // Borrow: require selection, show confirmation, then call BorrowService and refresh on success/error
        Button readSummaryBtn = new Button("Read Summary");
        readSummaryBtn.getStyleClass().add("secondary-button");
        readSummaryBtn.setOnAction(e -> {
            BookRow selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                runWithTimerPaused(inactivityTimer,
                        () -> showAlert(Alert.AlertType.WARNING, "No selection", "Please select a book to read its summary."));
                return;
            }
            runWithTimerPaused(inactivityTimer, () -> {
                try {
                    var bookOpt = BookDao.findById(selected.getBookId());
                    if (bookOpt.isEmpty()) {
                        showAlert(Alert.AlertType.ERROR, "Error", "Book not found.");
                        return;
                    }
                    showSummaryDialog(bookOpt.get(), selected, currentUser, inactivityTimer, refresh);
                } catch (SQLException ex) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Could not load book details.");
                }
            });
        });

        Button quickReviewBtn = new Button("Quick Review");
        quickReviewBtn.getStyleClass().add("secondary-button");
        quickReviewBtn.setOnAction(e -> {
            BookRow selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                runWithTimerPaused(inactivityTimer,
                        () -> showAlert(Alert.AlertType.WARNING, "No selection", "Please select a book to review."));
                return;
            }
            runWithTimerPaused(inactivityTimer, () -> {
                try {
                    var bookOpt = BookDao.findById(selected.getBookId());
                    if (bookOpt.isEmpty()) {
                        showAlert(Alert.AlertType.ERROR, "Error", "Book not found.");
                        return;
                    }
                    showQuickReviewDialog(bookOpt.get(), selected, navigator, currentUser, inactivityTimer, refresh);
                } catch (SQLException ex) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Could not load book details.");
                }
            });
        });

        Button borrowBtn = new Button("Borrow Selected Book");
        borrowBtn.getStyleClass().add("primary-button");
        borrowBtn.setOnAction(e -> {
            BookRow selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                runWithTimerPaused(inactivityTimer,
                        () -> showAlert(Alert.AlertType.WARNING, "No selection", "Please select a book to borrow."));
                return;
            }
            String confirmMsg = "Title: " + selected.getTitle() + "\nAuthor: " + selected.getAuthor();
            try {
                int activeCount = BorrowDao.countActiveByBorrowerUserId(currentUser.getId());
                confirmMsg += "\n\nYou have " + activeCount + " of " + BorrowService.MAX_ACTIVE_BORROWS + " books currently borrowed.";
                confirmMsg += "\nBorrow duration: " + BorrowService.BORROW_DURATION_DAYS + " days.";
                Instant dueInstant = Instant.now().plus(BorrowService.BORROW_DURATION_DAYS, ChronoUnit.DAYS);
                String dueStr = DateTimeFormatter.ISO_LOCAL_DATE.format(dueInstant.atZone(java.time.ZoneId.systemDefault()));
                confirmMsg += "\nDue date (after borrow): " + dueStr + ".";
            } catch (SQLException ignored) {
                // keep basic message if count fails
            }
            final String msg = confirmMsg;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Borrow");
            confirm.setHeaderText("Borrow this book?");
            confirm.setContentText(msg);
            runWithTimerPaused(inactivityTimer, () -> {
                Optional<ButtonType> result = confirm.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    try {
                        org.example.domain.Borrow created = BorrowService.borrow(selected.getBookId(), currentUser.getId());
                        String successMsg = "You have successfully borrowed:\nTitle: " + selected.getTitle()
                                + "\nAuthor: " + selected.getAuthor();
                        if (created.getDueAt() != null && !created.getDueAt().isEmpty()) {
                            successMsg += "\nDue date: " + formatDueDate(created.getDueAt());
                        }
                        showAlert(Alert.AlertType.INFORMATION, "Borrow confirmed", successMsg);
                        refresh.run();
                    } catch (BorrowService.BorrowException ex) {
                        showAlert(Alert.AlertType.ERROR, "Borrow failed", ex.getMessage());
                        refresh.run();
                    } catch (SQLException ex) {
                        showAlert(Alert.AlertType.ERROR, "Borrow failed", "A database error occurred.");
                        refresh.run();
                    }
                }
            });
        });

        Button myBorrowedBtn = new Button("My Borrowed Books");
        myBorrowedBtn.getStyleClass().add("secondary-button");
        myBorrowedBtn.setOnAction(e -> navigator.showMyBorrowedBooks(currentUser));

        // Logout returns to Student/Staff portal (entry screen)
        Button logoutBtn = new Button("Logout");
        logoutBtn.getStyleClass().add("secondary-button");
        logoutBtn.setOnAction(e -> navigator.showStudentStaffPortal());

        HBox buttons = new HBox(10, readSummaryBtn, quickReviewBtn, borrowBtn, myBorrowedBtn, logoutBtn);
        buttons.setPadding(new Insets(10, 0, 0, 0));
        buttons.getStyleClass().add("button-bar");

        VBox top = new VBox(10, title, new Label("Logged in as: " + currentUser.getFullName() + " (" + currentUser.getUsername() + ")"));
        VBox tableContainer = new VBox(table);
        tableContainer.getStyleClass().add("table-container");

        VBox center = new VBox(10, tableContainer, buttons);
        center.setPadding(new Insets(10));
        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(center);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("app-root");

        root.addEventFilter(MouseEvent.ANY, ev -> inactivityTimer.playFromStart());
        root.addEventFilter(KeyEvent.ANY, ev -> inactivityTimer.playFromStart());
        inactivityTimer.play();

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL cssResource = AvailableBooksScreen.class.getResource("/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }
        return scene;
    }

    /**
     * Shows a modal alert with the given type, title, and message.
     */
    private static void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Runs the given block while the inactivity timer is paused, then restarts it.
     * Used so that confirmation dialogs and alerts do not trigger the timer.
     */
    private static void runWithTimerPaused(PauseTransition timer, Runnable block) {
        if (timer == null) {
            block.run();
            return;
        }
        timer.pause();
        try {
            block.run();
        } finally {
            timer.playFromStart();
        }
    }

    /** Formats an ISO-8601 instant string (e.g. due_at) to MM/DD/YYYY for display. */
    private static String formatDueDate(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            java.time.Instant instant = java.time.Instant.parse(iso);
            java.time.LocalDate date = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            return String.format("%02d/%02d/%04d", date.getMonthValue(), date.getDayOfMonth(), date.getYear());
        } catch (Exception ex) {
            return iso.length() >= 10 ? iso.substring(0, 10) : iso;
        }
    }

    /**
     * Shows a dialog with the book's summary/abstract so the user can read it before borrowing.
     * If the summary is long, the dialog uses a scrollable area for comfortable reading.
     */
    private static void showSummaryDialog(Book book, BookRow row, User currentUser,
                                         PauseTransition inactivityTimer, Runnable refresh) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Book Summary — " + book.getTitle());

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));

        Label header = new Label("Summary / Abstract");
        header.getStyleClass().add("screen-title");

        content.getChildren().add(header);
        addDetailRow(content, "Title:", book.getTitle());
        addDetailRow(content, "Author:", book.getAuthorFullNameSnapshot());

        String summary = book.getSummary() != null && !book.getSummary().isBlank()
                ? book.getSummary() : "No summary available.";
        TextArea summaryArea = new TextArea(summary);
        summaryArea.setEditable(false);
        summaryArea.setWrapText(true);
        boolean isLong = summary.length() > SUMMARY_POPUP_THRESHOLD;
        summaryArea.setPrefRowCount(isLong ? 16 : 8);
        summaryArea.setStyle("-fx-background-color: #f8fafc;");

        content.getChildren().add(summaryArea);

        Button borrowBtn = new Button("Borrow this book");
        borrowBtn.getStyleClass().add("primary-button");
        borrowBtn.setOnAction(ev -> {
            dialog.close();
            runWithTimerPaused(inactivityTimer, () -> {
                String confirmMsg = "Title: " + row.getTitle() + "\nAuthor: " + row.getAuthor();
                try {
                    int activeCount = BorrowDao.countActiveByBorrowerUserId(currentUser.getId());
                    confirmMsg += "\n\nYou have " + activeCount + " of " + BorrowService.MAX_ACTIVE_BORROWS + " books currently borrowed.";
                    confirmMsg += "\nBorrow duration: " + BorrowService.BORROW_DURATION_DAYS + " days.";
                    Instant dueInstant = Instant.now().plus(BorrowService.BORROW_DURATION_DAYS, ChronoUnit.DAYS);
                    String dueStr = DateTimeFormatter.ISO_LOCAL_DATE.format(dueInstant.atZone(java.time.ZoneId.systemDefault()));
                    confirmMsg += "\nDue date (after borrow): " + dueStr + ".";
                } catch (SQLException ignored) {
                    // keep basic message if count fails
                }
                final String msg = confirmMsg;
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Confirm Borrow");
                confirm.setHeaderText("Borrow this book?");
                confirm.setContentText(msg);
                Optional<ButtonType> result = confirm.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    try {
                        org.example.domain.Borrow created = BorrowService.borrow(row.getBookId(), currentUser.getId());
                        String successMsg = "You have successfully borrowed:\nTitle: " + row.getTitle() + "\nAuthor: " + row.getAuthor();
                        if (created.getDueAt() != null && !created.getDueAt().isEmpty()) {
                            successMsg += "\nDue date: " + formatDueDate(created.getDueAt());
                        }
                        showAlert(Alert.AlertType.INFORMATION, "Borrow confirmed", successMsg);
                        refresh.run();
                    } catch (BorrowService.BorrowException ex) {
                        showAlert(Alert.AlertType.ERROR, "Borrow failed", ex.getMessage());
                        refresh.run();
                    } catch (SQLException ex) {
                        showAlert(Alert.AlertType.ERROR, "Borrow failed", "A database error occurred.");
                        refresh.run();
                    }
                }
            });
        });

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setOnAction(ev -> dialog.close());

        HBox buttons = new HBox(10, borrowBtn, closeBtn);
        buttons.setPadding(new Insets(10, 0, 0, 0));
        content.getChildren().add(buttons);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(isLong ? ScrollPane.ScrollBarPolicy.AS_NEEDED : ScrollPane.ScrollBarPolicy.NEVER);

        double height = isLong ? 480 : 320;
        Scene dialogScene = new Scene(scroll, 500, height);
        java.net.URL cssResource = AvailableBooksScreen.class.getResource("/app.css");
        if (cssResource != null) {
            dialogScene.getStylesheets().add(cssResource.toExternalForm());
        }
        dialog.setScene(dialogScene);
        dialog.showAndWait();
    }

    /**
     * Shows a Quick Review dialog for the given book: details, summary, and first few pages of content
     * (for .txt files). User can close or choose to borrow from the dialog.
     */
    private static void showQuickReviewDialog(Book book, BookRow row,
                                             Navigator navigator, User currentUser,
                                             PauseTransition inactivityTimer, Runnable refresh) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Quick Review — " + book.getTitle());

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label header = new Label("Quick Review");
        header.getStyleClass().add("screen-title");

        VBox details = new VBox(5);
        details.getStyleClass().add("content-card");
        details.setPadding(new Insets(12));
        addDetailRow(details, "Title:", book.getTitle());
        addDetailRow(details, "Author:", book.getAuthorFullNameSnapshot());
        addDetailRow(details, "Genre:", book.getGenre() != null ? book.getGenre() : "—");
        addDetailRow(details, "Publish date:", row.getPublishDateDisplay());

        Label summaryLabel = new Label("Summary / Abstract");
        summaryLabel.setStyle("-fx-font-weight: bold;");
        String summary = book.getSummary() != null && !book.getSummary().isBlank()
                ? book.getSummary() : "No summary available.";
        TextArea summaryArea = new TextArea(summary);
        summaryArea.setEditable(false);
        summaryArea.setWrapText(true);
        summaryArea.setPrefRowCount(4);
        summaryArea.setStyle("-fx-background-color: #f8fafc;");

        Label previewLabel = new Label("Preview (first few pages)");
        previewLabel.setStyle("-fx-font-weight: bold;");
        String previewText = BookPreviewUtil.readTextPreview(book.getFilePath());
        TextArea previewArea = new TextArea(
                previewText != null ? previewText
                        : "Preview could not be loaded (file missing, unsupported format, or read error). You can read the summary above.");
        previewArea.setEditable(false);
        previewArea.setWrapText(true);
        previewArea.setPrefRowCount(12);
        previewArea.setStyle("-fx-background-color: #f8fafc;");

        content.getChildren().addAll(header, details, summaryLabel, summaryArea, previewLabel, previewArea);

        Button borrowBtn = new Button("Borrow this book");
        borrowBtn.getStyleClass().add("primary-button");
        borrowBtn.setOnAction(ev -> {
            dialog.close();
            runWithTimerPaused(inactivityTimer, () -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Confirm Borrow");
                confirm.setHeaderText("Borrow this book?");
                String confirmMsg = "Title: " + row.getTitle() + "\nAuthor: " + row.getAuthor();
                try {
                    int activeCount = BorrowDao.countActiveByBorrowerUserId(currentUser.getId());
                    java.time.Instant dueInstant = java.time.Instant.now().plus(BorrowService.BORROW_DURATION_DAYS, java.time.temporal.ChronoUnit.DAYS);
                    String dueStr = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE.format(dueInstant.atZone(java.time.ZoneId.systemDefault()));
                    confirmMsg += "\n\nBorrow duration: " + BorrowService.BORROW_DURATION_DAYS + " days";
                    confirmMsg += "\nDue date: " + dueStr;
                    confirmMsg += "\n\nYou have " + activeCount + " of " + BorrowService.MAX_ACTIVE_BORROWS + " books currently borrowed.";
                } catch (SQLException ignored) { }
                confirm.setContentText(confirmMsg);
                Optional<ButtonType> result = confirm.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    try {
                        org.example.domain.Borrow created = BorrowService.borrow(row.getBookId(), currentUser.getId());
                        String successMsg = "You have successfully borrowed:\nTitle: " + row.getTitle() + "\nAuthor: " + row.getAuthor();
                        if (created.getDueAt() != null && !created.getDueAt().isEmpty()) {
                            successMsg += "\nDue date: " + formatDueDate(created.getDueAt());
                        }
                        showAlert(Alert.AlertType.INFORMATION, "Borrow confirmed", successMsg);
                        refresh.run();
                    } catch (BorrowService.BorrowException ex) {
                        showAlert(Alert.AlertType.ERROR, "Borrow failed", ex.getMessage());
                        refresh.run();
                    } catch (SQLException ex) {
                        showAlert(Alert.AlertType.ERROR, "Borrow failed", "A database error occurred.");
                        refresh.run();
                    }
                }
            });
        });

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setOnAction(ev -> dialog.close());

        HBox buttons = new HBox(10, borrowBtn, closeBtn);
        buttons.setPadding(new Insets(10, 0, 0, 0));
        content.getChildren().add(buttons);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Scene dialogScene = new Scene(scroll, 560, 620);
        java.net.URL cssResource = AvailableBooksScreen.class.getResource("/app.css");
        if (cssResource != null) {
            dialogScene.getStylesheets().add(cssResource.toExternalForm());
        }
        dialog.setScene(dialogScene);
        dialog.showAndWait();
    }

    private static void addDetailRow(VBox parent, String label, String value) {
        HBox row = new HBox(8);
        Label l = new Label(label);
        l.setStyle("-fx-font-weight: bold; -fx-min-width: 100;");
        Label v = new Label(value != null ? value : "—");
        v.setWrapText(true);
        row.getChildren().addAll(l, v);
        parent.getChildren().add(row);
    }

    /**
     * Table row model for Book (TableView needs getters for property names).
     * Wraps a {@link Book} for display, including formatted publish date and availability label.
     */
    public static class BookRow {
        private final long bookId;
        private final String title;
        private final String author;
        private final String publishDateDisplay;
        private final String availability;
        private final String summary;

        public BookRow(Book b) {
            this.bookId = b.getId();
            this.title = b.getTitle();
            this.author = b.getAuthorFullNameSnapshot();
            this.publishDateDisplay = formatPublishDate(b.getPublishDate());
            this.availability = b.getAvailability().name();
            this.summary = b.getSummary() != null ? b.getSummary() : "";
        }

        /** Formats ISO-8601 publish date to MM/DD/YYYY for display; returns empty or raw substring on parse failure. */
        private static String formatPublishDate(String iso) {
            if (iso == null || iso.isEmpty()) return "";
            try {
                java.time.Instant instant = java.time.Instant.parse(iso);
                java.time.LocalDate date = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                int month = date.getMonthValue();
                int day = date.getDayOfMonth();
                int year = date.getYear();
                return String.format("%02d/%02d/%04d", month, day, year);
            } catch (Exception ex) {
                return iso.length() >= 10 ? iso.substring(0, 10) : iso;
            }
        }

        public long getBookId() { return bookId; }
        public String getTitle() { return title; }
        public String getAuthor() { return author; }
        public String getPublishDateDisplay() { return publishDateDisplay; }
        public String getAvailability() { return availability; }
        public String getSummary() { return summary; }
    }
}
