package org.example.ui;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.app.Navigator;
import org.example.db.BookDao;
import org.example.domain.Book;
import org.example.domain.BorrowWithBook;
import org.example.domain.User;
import org.example.service.BorrowService;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.beans.binding.Bindings;

/**
 * Screen showing the current user's borrowed books (active and previously returned).
 * User can return a book from here if it is still active.
 */
public final class MyBorrowedBooksScreen {

    private MyBorrowedBooksScreen() {}

    private static boolean isOverdueLoan(BorrowRow row) {
        if (row.getDueAtIso() == null || row.getDueAtIso().isBlank()) {
            return false;
        }
        try {
            return !Instant.now().isBefore(Instant.parse(row.getDueAtIso()));
        } catch (Exception e) {
            return false;
        }
    }

    public static Scene create(Navigator navigator, User currentUser) {
        Label title = new Label("My Borrowed Books");
        title.getStyleClass().add("screen-title");
        Label subtitle = new Label(
                "Active and returned loans. Return books, open the reader, or create a review for a title you have borrowed.");
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("login-hint");

        TableView<BorrowRow> table = new TableView<>();
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        ObservableList<BorrowRow> items = FXCollections.observableArrayList();
        FilteredList<BorrowRow> filtered = new FilteredList<>(items, x -> true);
        table.setItems(filtered);

        TableColumn<BorrowRow, String> colTitle = new TableColumn<>("Title");
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colTitle.setPrefWidth(200);

        TableColumn<BorrowRow, String> colAuthor = new TableColumn<>("Author");
        colAuthor.setCellValueFactory(new PropertyValueFactory<>("author"));
        colAuthor.setPrefWidth(120);

        TableColumn<BorrowRow, String> colGenre = new TableColumn<>("Genre");
        colGenre.setCellValueFactory(new PropertyValueFactory<>("genre"));
        colGenre.setPrefWidth(100);

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

        table.getColumns().addAll(List.of(colTitle, colAuthor, colGenre, colBorrowedAt, colReturnedAt, colDueAt, colStatus));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(tv -> {
            TableRow<BorrowRow> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldItem, item) -> {
                if (item == null) {
                    row.setStyle("");
                    return;
                }
                if (item.isActive() && isOverdueLoan(item)) {
                    row.setStyle("-fx-background-color: #fff5f5;");
                } else {
                    row.setStyle("");
                }
            });
            return row;
        });

        PauseTransition inactivityTimer = new PauseTransition(Duration.minutes(15));
        inactivityTimer.setOnFinished(ev -> navigator.showStudentStaffPortal());

        CheckBox showReturned = new CheckBox("Show returned");
        showReturned.setSelected(false);

        TextField searchField = new TextField();
        searchField.setPromptText("Search title or author...");
        searchField.setMaxWidth(260);
        ComboBox<String> statusQuick = new ComboBox<>(FXCollections.observableArrayList("All rows", "Active only", "Returned only"));
        statusQuick.getSelectionModel().selectFirst();

        ComboBox<String> genreQuick = new ComboBox<>(FXCollections.observableArrayList("All genres"));
        genreQuick.getSelectionModel().selectFirst();

        ComboBox<String> dueQuick = new ComboBox<>(FXCollections.observableArrayList(
                "Any due date", "Active — overdue", "Active — due within 7 days"));
        dueQuick.getSelectionModel().selectFirst();

        CheckBox pdfOnly = new CheckBox("PDF loans only");

        Runnable applyFilter = () -> {
            String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
            String st = statusQuick.getSelectionModel().getSelectedItem();
            String gSel = genreQuick.getSelectionModel().getSelectedItem();
            String dueSel = dueQuick.getSelectionModel().getSelectedItem();
            Instant now = Instant.now();
            filtered.setPredicate(row -> {
                if (showReturned.isSelected()) {
                    // show all
                } else if (!row.isActive()) {
                    return false;
                }
                if (pdfOnly.isSelected() && !row.isPdf()) {
                    return false;
                }
                if (!q.isEmpty()) {
                    if (!row.getTitle().toLowerCase(Locale.ROOT).contains(q)
                            && !row.getAuthor().toLowerCase(Locale.ROOT).contains(q)
                            && !(row.getGenre() != null && row.getGenre().toLowerCase(Locale.ROOT).contains(q))) {
                        return false;
                    }
                }
                if (gSel != null && !"All genres".equals(gSel) && !gSel.isBlank()) {
                    String rg = row.getGenre() == null ? "" : row.getGenre().toLowerCase(Locale.ROOT);
                    if (!rg.contains(gSel.trim().toLowerCase(Locale.ROOT))) {
                        return false;
                    }
                }
                if ("Active only".equals(st) && !row.isActive()) {
                    return false;
                }
                if ("Returned only".equals(st) && row.isActive()) {
                    return false;
                }
                if (dueSel != null && row.isActive() && row.getDueAtIso() != null && !row.getDueAtIso().isBlank()) {
                    try {
                        Instant due = Instant.parse(row.getDueAtIso());
                        if ("Active — overdue".equals(dueSel)) {
                            if (!now.isAfter(due)) {
                                return false;
                            }
                        } else if ("Active — due within 7 days".equals(dueSel)) {
                            if (now.isAfter(due)) {
                                return false;
                            }
                            if (due.isAfter(now.plus(7, ChronoUnit.DAYS))) {
                                return false;
                            }
                        }
                    } catch (Exception ignored) {
                        if (!"Any due date".equals(dueSel)) {
                            return false;
                        }
                    }
                } else if (dueSel != null && !"Any due date".equals(dueSel) && row.isActive()) {
                    return false;
                }
                return true;
            });
        };

        Runnable refresh = () -> {
            items.clear();
            try {
                List<BorrowWithBook> borrows = org.example.db.BorrowDao.findAllByBorrowerUserId(currentUser.getId());
                Set<String> genres = new LinkedHashSet<>();
                genres.add("All genres");
                for (BorrowWithBook b : borrows) {
                    items.add(new BorrowRow(b));
                    String g = b.getGenre();
                    if (g != null && !g.trim().isEmpty()) {
                        for (String part : g.split(",")) {
                            String t = part.trim();
                            if (!t.isEmpty()) {
                                genres.add(t);
                            }
                        }
                    }
                }
                String prevG = genreQuick.getSelectionModel().getSelectedItem();
                genreQuick.setItems(FXCollections.observableArrayList(genres));
                if (prevG != null && genres.contains(prevG)) {
                    genreQuick.getSelectionModel().select(prevG);
                } else {
                    genreQuick.getSelectionModel().selectFirst();
                }
            } catch (SQLException ex) {
                runWithTimerPaused(inactivityTimer,
                    () -> showAlert(Alert.AlertType.ERROR, "Error", "Could not load borrowed books."));
            }
            applyFilter.run();
        };
        refresh.run();

        showReturned.setOnAction(e -> {
            applyFilter.run();
        });
        searchField.textProperty().addListener((a, b, c) -> applyFilter.run());
        statusQuick.setOnAction(e -> applyFilter.run());
        genreQuick.setOnAction(e -> applyFilter.run());
        dueQuick.setOnAction(e -> applyFilter.run());
        pdfOnly.setOnAction(e -> applyFilter.run());

        Button returnBtn = new Button("Return selected");
        returnBtn.getStyleClass().add("primary-button");
        returnBtn.setOnAction(e -> {
            List<BorrowRow> sel = new ArrayList<>(table.getSelectionModel().getSelectedItems());
            List<BorrowRow> activeSel = sel.stream().filter(BorrowRow::isActive).toList();
            if (activeSel.isEmpty()) {
                runWithTimerPaused(inactivityTimer,
                    () -> showAlert(Alert.AlertType.WARNING, "No active selection",
                            "Select one or more borrowed (not yet returned) books to return."));
                return;
            }
            String lines = activeSel.stream()
                    .map(r -> "• " + r.getTitle() + " — " + r.getAuthor())
                    .collect(Collectors.joining("\n"));
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm return");
            confirm.setHeaderText("Return " + activeSel.size() + " book(s)?");
            confirm.setContentText(lines);
            runWithTimerPaused(inactivityTimer, () -> {
                Optional<ButtonType> result = confirm.showAndWait();
                if (result.isEmpty() || result.get() != ButtonType.OK) {
                    return;
                }
                try {
                    List<Long> ids = activeSel.stream().map(BorrowRow::getBorrowId).toList();
                    BorrowService.returnBooksMany(ids, currentUser.getId());
                    showAlert(Alert.AlertType.INFORMATION, "Returned", "Selected books were returned.");
                    refresh.run();
                } catch (BorrowService.BorrowException ex) {
                    showAlert(Alert.AlertType.ERROR, "Return failed", ex.getMessage());
                    refresh.run();
                } catch (SQLException ex) {
                    showAlert(Alert.AlertType.ERROR, "Return failed", "A database error occurred.");
                    refresh.run();
                }
            });
        });

        Button readPdfBtn = new Button("Open Reader");
        readPdfBtn.getStyleClass().add("primary-button");
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
                    selected.getTitle(), selected.getFilePath());
        });

        Button reviewsBtn = new Button("Reviews");
        reviewsBtn.getStyleClass().add("secondary-button");
        reviewsBtn.setTooltip(new Tooltip(
                "Add or edit your rating and review for this borrowed title."));
        reviewsBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        reviewsBtn.setOnAction(e -> {
            BorrowRow selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            try {
                Optional<Book> bookOpt = BookDao.findById(selected.getBookId());
                if (bookOpt.isEmpty()) {
                    runWithTimerPaused(inactivityTimer,
                            () -> showAlert(Alert.AlertType.WARNING, "Not found",
                                    "This title is no longer in the catalog."));
                    return;
                }
                runWithTimerPaused(inactivityTimer,
                        () -> AvailableBooksScreen.showReviewsAndRateDialog(
                                table.getScene().getWindow(), bookOpt.get(), currentUser, refresh));
            } catch (SQLException ex) {
                runWithTimerPaused(inactivityTimer,
                        () -> showAlert(Alert.AlertType.ERROR, "Error", "Could not open reviews."));
            }
        });

        FlowPane buttons = new FlowPane();
        buttons.setHgap(10);
        buttons.setVgap(10);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.getChildren().addAll(returnBtn, readPdfBtn, reviewsBtn);
        buttons.setPadding(new Insets(10, 0, 0, 0));
        buttons.getStyleClass().add("button-bar");

        Label searchLbl = new Label("Search");
        searchLbl.getStyleClass().add("section-heading");
        Label loanLbl = new Label("Loan");
        loanLbl.getStyleClass().add("section-heading");
        Label genreLbl = new Label("Genre");
        genreLbl.getStyleClass().add("section-heading");
        Label dueLbl = new Label("Due");
        dueLbl.getStyleClass().add("section-heading");
        HBox filterRow = new HBox(10,
                searchLbl, searchField,
                loanLbl, statusQuick,
                genreLbl, genreQuick,
                dueLbl, dueQuick,
                pdfOnly);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        Label signedInLbl = new Label("Signed in as " + currentUser.getFullName() + " (" + currentUser.getUsername() + ")");
        signedInLbl.getStyleClass().add("login-hint");
        VBox top = new VBox(10,
                title,
                subtitle,
                showReturned,
                filterRow,
                signedInLbl);
        top.setFillWidth(true);
        ScrollPane topScroll = new ScrollPane(top);
        topScroll.setFitToWidth(true);
        topScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        topScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        VBox tableContainer = new VBox(table);
        tableContainer.getStyleClass().add("table-container");

        VBox center = new VBox(10, tableContainer, buttons);
        center.setPadding(new Insets(10));
        BorderPane root = new BorderPane();
        root.setTop(topScroll);
        root.setCenter(center);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("app-root");

        root.addEventFilter(MouseEvent.ANY, ev -> inactivityTimer.playFromStart());
        root.addEventFilter(KeyEvent.ANY, ev -> inactivityTimer.playFromStart());
        inactivityTimer.play();

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        topScroll.maxHeightProperty().bind(
                Bindings.createDoubleBinding(
                        () -> {
                            double h = scene.getHeight();
                            if (h <= 0) {
                                return 360.0;
                            }
                            return Math.min(420.0, Math.max(160.0, h * 0.48));
                        },
                        scene.heightProperty()));
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
        private final String genre;
        private final String dueAtIso;

        public BorrowRow(BorrowWithBook b) {
            this.borrowId = b.getBorrowId();
            this.bookId = b.getBookId();
            this.title = b.getTitle();
            this.author = b.getAuthor();
            this.genre = b.getGenre() == null || b.getGenre().isBlank() ? "—" : b.getGenre();
            this.borrowedAtDisplay = formatIsoDate(b.getBorrowedAt());
            this.returnedAtDisplay = b.getReturnedAt() != null && !b.getReturnedAt().isEmpty()
                ? formatIsoDate(b.getReturnedAt()) : "—";
            this.dueAtIso = b.getDueAt();
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
        public String getGenre() { return genre; }
        /** Raw due instant for filters; may be null or blank. */
        public String getDueAtIso() { return dueAtIso; }
        public String getBorrowedAtDisplay() { return borrowedAtDisplay; }
        public String getReturnedAtDisplay() { return returnedAtDisplay; }
        public String getDueAtDisplay() { return dueAtDisplay; }
        public String getStatus() { return status; }
        public boolean isActive() { return active; }
    }
}
