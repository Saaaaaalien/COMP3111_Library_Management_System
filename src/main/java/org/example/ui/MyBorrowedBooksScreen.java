package org.example.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import org.example.app.Navigator;
import org.example.db.NotificationDao;
import org.example.domain.BorrowWithBook;
import org.example.domain.User;
import org.example.service.BorrowService;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Screen showing the current user's borrowed books (active and previously returned).
 * User can return a book from here if it is still active.
 */
public final class MyBorrowedBooksScreen {

    private MyBorrowedBooksScreen() {}

    public static Scene create(Navigator navigator, User currentUser) {
        Label title = new Label("My Borrowed Books");
        title.getStyleClass().add("screen-title");

        TableView<BorrowRow> table = new TableView<>();
        ObservableList<BorrowRow> items = FXCollections.observableArrayList();
        table.setItems(items);

        TableColumn<BorrowRow, String> colTitle = new TableColumn<>("Title");
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colTitle.setPrefWidth(200);

        TableColumn<BorrowRow, String> colAuthor = new TableColumn<>("Author");
        colAuthor.setCellValueFactory(new PropertyValueFactory<>("author"));
        colAuthor.setPrefWidth(120);

        TableColumn<BorrowRow, String> colBorrowedAt = new TableColumn<>("Borrowed Date");
        colBorrowedAt.setCellValueFactory(new PropertyValueFactory<>("borrowedAtDisplay"));
        colBorrowedAt.setPrefWidth(120);

        TableColumn<BorrowRow, String> colReturnedAt = new TableColumn<>("Returned Date");
        colReturnedAt.setCellValueFactory(new PropertyValueFactory<>("returnedAtDisplay"));
        colReturnedAt.setPrefWidth(120);

        TableColumn<BorrowRow, String> colDueAt = new TableColumn<>("Due Date");
        colDueAt.setCellValueFactory(new PropertyValueFactory<>("dueAtDisplay"));
        colDueAt.setPrefWidth(120);

        TableColumn<BorrowRow, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setPrefWidth(80);

        table.getColumns().addAll(List.of(colTitle, colAuthor, colBorrowedAt, colReturnedAt, colDueAt, colStatus));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        PauseTransition inactivityTimer = new PauseTransition(Duration.minutes(15));
        inactivityTimer.setOnFinished(ev -> navigator.showStudentStaffPortal());

        Button notifBtn = new Button("Notification Board");
        notifBtn.getStyleClass().add("secondary-button");
        notifBtn.setOnAction(e -> navigator.showStudentStaffNotifications(currentUser, true));

        Runnable refreshNotifLabel = () -> {
            try {
                int n = NotificationDao.countUnread(currentUser.getId());
                notifBtn.setText(n > 0 ? "Notification Board (" + n + ")" : "Notification Board");
            } catch (Exception ignored) {
                notifBtn.setText("Notification Board");
            }
        };

        // Spec: "currently borrowed" should show active loans by default.
        // Keep an option to reveal returned history for convenience.
        CheckBox showReturned = new CheckBox("Show returned");
        showReturned.setSelected(false);

        Runnable refresh = () -> {
            items.clear();
            try {
                List<BorrowWithBook> borrows = org.example.db.BorrowDao.findAllByBorrowerUserId(currentUser.getId());
                for (BorrowWithBook b : borrows) {
                    if (showReturned.isSelected() || b.isActive()) {
                        items.add(new BorrowRow(b));
                    }
                }
            } catch (SQLException ex) {
                runWithTimerPaused(inactivityTimer,
                    () -> showAlert(Alert.AlertType.ERROR, "Error", "Could not load borrowed books."));
            }
            refreshNotifLabel.run();
        };
        refresh.run();

        showReturned.setOnAction(e -> refresh.run());

        Button returnBtn = new Button("Return Selected");
        returnBtn.getStyleClass().add("primary-button");
        // Disable actions until a row is selected (consistent with AvailableBooksScreen).
        returnBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        returnBtn.setOnAction(e -> {
            BorrowRow selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                runWithTimerPaused(inactivityTimer,
                    () -> showAlert(Alert.AlertType.WARNING, "No selection", "Please select a book to return."));
                return;
            }
            if (!selected.isActive()) {
                runWithTimerPaused(inactivityTimer,
                    () -> showAlert(Alert.AlertType.INFORMATION, "Already returned", "This book has already been returned."));
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Return");
            confirm.setHeaderText("Return this book?");
            confirm.setContentText("Title: " + selected.getTitle() + "\nAuthor: " + selected.getAuthor());
            runWithTimerPaused(inactivityTimer, () -> {
                Optional<ButtonType> result = confirm.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    try {
                        BorrowService.returnBook(selected.getBorrowId(), currentUser.getId());
                        showAlert(
                            Alert.AlertType.INFORMATION,
                            "Return confirmed",
                            "You have returned: " + selected.getTitle());
                        refresh.run();
                    } catch (BorrowService.BorrowException ex) {
                        showAlert(Alert.AlertType.ERROR, "Return failed", ex.getMessage());
                        refresh.run();
                    } catch (SQLException ex) {
                        showAlert(Alert.AlertType.ERROR, "Return failed", "A database error occurred.");
                        refresh.run();
                    }
                }
            });
        });

        Button readPdfBtn = new Button("Open Reader");
        readPdfBtn.getStyleClass().add("primary-button");
        // Disable actions until a row is selected (consistent with AvailableBooksScreen).
        readPdfBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        readPdfBtn.setOnAction(e -> {
            BorrowRow selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                runWithTimerPaused(inactivityTimer,
                    () -> showAlert(Alert.AlertType.WARNING, "No selection", "Select an active borrowed book."));
                return;
            }
            if (!selected.isActive()) {
                runWithTimerPaused(inactivityTimer,
                    () -> showAlert(Alert.AlertType.INFORMATION, "Returned", "Only active loans can be opened in the reader."));
                return;
            }
            if (!selected.isPdf()) {
                runWithTimerPaused(inactivityTimer,
                    () -> showAlert(Alert.AlertType.INFORMATION, "Not a PDF", "The reader opens PDF files only for this build."));
                return;
            }
            PdfReaderScreen.open(navigator, currentUser, selected.getBorrowId(), selected.getBookId(),
                    selected.getTitle(), selected.getFilePath(), "MY_BORROWS");
        });

        Button profileBtn = new Button("Manage Profile");
        profileBtn.getStyleClass().add("secondary-button");
        profileBtn.setOnAction(e -> navigator.showStudentStaffProfile(currentUser));

        Button availableBooksBtn = new Button("Go to Available Books");
        availableBooksBtn.getStyleClass().add("secondary-button");
        availableBooksBtn.setOnAction(e -> navigator.showAvailableBooks(currentUser));

        Button logoutBtn = new Button("Logout");
        logoutBtn.getStyleClass().add("secondary-button");
        logoutBtn.setOnAction(e -> navigator.showStudentStaffPortal());

        FlowPane buttons = new FlowPane();
        buttons.setHgap(10);
        buttons.setVgap(10);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.getChildren().addAll(returnBtn, readPdfBtn, profileBtn, notifBtn, availableBooksBtn, logoutBtn);
        buttons.setPadding(new Insets(10, 0, 0, 0));
        buttons.getStyleClass().add("button-bar");

        VBox top = new VBox(10,
                title,
                showReturned,
                new Label("Logged in as: " + currentUser.getFullName() + " (" + currentUser.getUsername() + ")"));
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
        java.net.URL cssResource = MyBorrowedBooksScreen.class.getResource("/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }
        return scene;
    }

    private static void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

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

    /**
     * Table row model for a borrow with book info (TableView needs getters for property names).
     */
    public static class BorrowRow {
        private final long borrowId;
        private final String title;
        private final String author;
        private final String borrowedAtDisplay;
        private final String returnedAtDisplay;
        private final String dueAtDisplay;
        private final String status;
        private final boolean active;

        private final long bookId;
        private final String filePath;

        public BorrowRow(BorrowWithBook b) {
            this.borrowId = b.getBorrowId();
            this.bookId = b.getBookId();
            this.title = b.getTitle();
            this.author = b.getAuthor();
            this.borrowedAtDisplay = formatIsoDate(b.getBorrowedAt());
            this.returnedAtDisplay = b.getReturnedAt() != null && !b.getReturnedAt().isEmpty()
                ? formatIsoDate(b.getReturnedAt()) : "—";
            this.dueAtDisplay = b.getDueAt() != null && !b.getDueAt().isEmpty()
                ? formatIsoDate(b.getDueAt()) : "—";
            this.active = b.isActive();
            this.status = active ? "Borrowed" : "Returned";
            this.filePath = b.getFilePath();
        }

        private static String formatIsoDate(String iso) {
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

        public long getBorrowId() { return borrowId; }
        public long getBookId() { return bookId; }
        public String getFilePath() { return filePath; }
        public boolean isPdf() {
            return filePath != null && filePath.toLowerCase().endsWith(".pdf");
        }
        public String getTitle() { return title; }
        public String getAuthor() { return author; }
        public String getBorrowedAtDisplay() { return borrowedAtDisplay; }
        public String getReturnedAtDisplay() { return returnedAtDisplay; }
        public String getDueAtDisplay() { return dueAtDisplay; }
        public String getStatus() { return status; }
        public boolean isActive() { return active; }
    }
}
