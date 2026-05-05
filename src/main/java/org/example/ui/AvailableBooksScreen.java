package org.example.ui;

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
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Separator;
import javafx.stage.Window;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.app.Navigator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.example.db.BookDao;
import org.example.db.BookReviewDao;
import org.example.db.BorrowDao;
import org.example.domain.Book;
import org.example.domain.User;
import org.example.service.BorrowService;
import org.example.util.BookPreviewUtil;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

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
    private static double summaryDialogWidth = 560;
    private static double summaryDialogHeight = 320;
    private static Double summaryDialogX;
    private static Double summaryDialogY;
    private static double quickDialogWidth = 1000;
    private static double quickDialogHeight = 760;
    private static Double quickDialogX;
    private static Double quickDialogY;
    private static final List<String> FIXED_GENRES = List.of(
            "All genres", "Fiction", "Non-Fiction", "Mystery", "Fantasy", "Science Fiction",
            "Biography", "History", "Self-Help", "Education", "Technology", "Romance", "Other"
    );

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

        Label subtitle = new Label("Browse and borrow available books.");
        subtitle.setWrapText(true);

        TableView<BookRow> table = new TableView<>();
        table.setEditable(true);
        ObservableList<BookRow> allItems = FXCollections.observableArrayList();
        FilteredList<BookRow> filteredItems = new FilteredList<>(allItems, row -> true);
        table.setItems(filteredItems);

        table.setRowFactory(tv -> {
            TableRow<BookRow> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldItem, item) -> {
                if (item == null) {
                    row.setStyle("");
                    return;
                }
                // Spec styling: black for available copies, dark red when borrowed / unavailable.
                if ("AVAILABLE".equals(item.getAvailability())) {
                    row.setStyle("-fx-text-fill: #000000;");
                } else {
                    row.setStyle("-fx-text-fill: #8b0000;");
                }
            });
            return row;
        });

        TableColumn<BookRow, Boolean> colPick = new TableColumn<>("Borrow");
        colPick.setCellValueFactory(data -> data.getValue().borrowSelectedProperty());
        colPick.setCellFactory(column -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();
            private BooleanProperty bound;

            {
                checkBox.setOnAction(ev -> {
                    BookRow row = getTableRow() != null ? getTableRow().getItem() : null;
                    if (row != null && "AVAILABLE".equals(row.getAvailability())) {
                        row.borrowSelectedProperty().set(checkBox.isSelected());
                    }
                });
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (bound != null) {
                    try {
                        checkBox.selectedProperty().unbindBidirectional(bound);
                    } catch (Exception ignored) {
                    }
                    bound = null;
                }
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                BookRow row = getTableRow().getItem();
                bound = row.borrowSelectedProperty();
                checkBox.selectedProperty().bindBidirectional(bound);
                checkBox.setDisable(!"AVAILABLE".equals(row.getAvailability()));
                setGraphic(checkBox);
            }
        });
        colPick.setEditable(true);
        colPick.setPrefWidth(70);

        TableColumn<BookRow, String> colTitle = new TableColumn<>("Title");
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colTitle.setPrefWidth(180);
        colTitle.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                BookRow row = getTableRow() != null ? getTableRow().getItem() : null;
                applyAvailabilityTextColor(this, row);
            }
        });

        TableColumn<BookRow, String> colAuthor = new TableColumn<>("Author");
        colAuthor.setCellValueFactory(new PropertyValueFactory<>("author"));
        colAuthor.setPrefWidth(120);
        colAuthor.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                BookRow row = getTableRow() != null ? getTableRow().getItem() : null;
                applyAvailabilityTextColor(this, row);
            }
        });

        TableColumn<BookRow, String> colPublishDate = new TableColumn<>("Publish Date");
        colPublishDate.setCellValueFactory(new PropertyValueFactory<>("publishDateDisplay"));
        colPublishDate.setPrefWidth(100);
        colPublishDate.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                BookRow row = getTableRow() != null ? getTableRow().getItem() : null;
                applyAvailabilityTextColor(this, row);
            }
        });

        TableColumn<BookRow, String> colAvailability = new TableColumn<>("Availability Status");
        colAvailability.setCellValueFactory(new PropertyValueFactory<>("availability"));
        colAvailability.setPrefWidth(90);
        colAvailability.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                BookRow row = getTableRow() != null ? getTableRow().getItem() : null;
                applyAvailabilityTextColor(this, row);
            }
        });

        TableColumn<BookRow, String> colGenre = new TableColumn<>("Genre");
        colGenre.setCellValueFactory(new PropertyValueFactory<>("genre"));
        colGenre.setPrefWidth(100);
        colGenre.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                BookRow row = getTableRow() != null ? getTableRow().getItem() : null;
                applyAvailabilityTextColor(this, row);
            }
        });

        TableColumn<BookRow, String> colAvg = new TableColumn<>("Avg rating");
        colAvg.setCellValueFactory(new PropertyValueFactory<>("avgRatingDisplay"));
        colAvg.setPrefWidth(80);
        colAvg.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                styleCatalogStringCell(this, item, empty);
            }
        });

        TableColumn<BookRow, String> colRevCount = new TableColumn<>("Reviews");
        colRevCount.setCellValueFactory(new PropertyValueFactory<>("reviewCountDisplay"));
        colRevCount.setPrefWidth(70);
        colRevCount.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                styleCatalogStringCell(this, item, empty);
            }
        });

        TableColumn<BookRow, String> colSummary = new TableColumn<>("Abstract / Summary");
        colSummary.setCellValueFactory(new PropertyValueFactory<>("summary"));
        colSummary.setPrefWidth(SUMMARY_PREF_WIDTH);
        colSummary.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                styleCatalogStringCell(this, item, empty);
            }
        });

        table.getColumns().addAll(List.of(colPick, colTitle, colAuthor, colGenre, colAvg, colRevCount, colPublishDate, colAvailability, colSummary));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Inactivity timer: return to portal after 15 minutes with no input
        PauseTransition inactivityTimer = new PauseTransition(Duration.minutes(15));
        inactivityTimer.setOnFinished(ev -> navigator.showStudentStaffPortal());

        TextField searchField = new TextField();
        searchField.setPromptText("Search by title or author...");
        searchField.setMaxWidth(300);

        ComboBox<String> genreFilter = new ComboBox<>(FXCollections.observableArrayList(FIXED_GENRES));
        genreFilter.getSelectionModel().selectFirst();
        ComboBox<String> availabilityFilter = new ComboBox<>(FXCollections.observableArrayList("All", "AVAILABLE", "BORROWED"));
        // Default behavior for this screen is to show ONLY available books.
        availabilityFilter.getSelectionModel().select("AVAILABLE");
        DatePicker publishFrom = new DatePicker();
        publishFrom.setPromptText("Published from");
        DatePicker publishTo = new DatePicker();
        publishTo.setPromptText("Published to");

        Runnable updateFilter = () -> {
            String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
            String gSel = genreFilter.getSelectionModel().getSelectedItem();
            String aSel = availabilityFilter.getSelectionModel().getSelectedItem();
            LocalDate fromD = publishFrom.getValue();
            LocalDate toD = publishTo.getValue();
            filteredItems.setPredicate(row -> {
                if (!query.isEmpty()) {
                    boolean match = row.getTitle().toLowerCase().contains(query)
                            || row.getAuthor().toLowerCase().contains(query);
                    if (!match) {
                        return false;
                    }
                }
                if (gSel != null && !gSel.isBlank() && !"All genres".equals(gSel)) {
                    Set<String> rowGenres = tokenizeGenres(row.getGenre());
                    if (!rowGenres.contains(gSel.trim().toLowerCase())) {
                        return false;
                    }
                }
                if (aSel != null && !"All".equalsIgnoreCase(aSel)) {
                    String rowA = row.getAvailability() == null ? "" : row.getAvailability().trim();
                    if (!aSel.trim().equalsIgnoreCase(rowA)) {
                        return false;
                    }
                }
                if (fromD != null || toD != null) {
                    try {
                        LocalDate pd = Instant.parse(row.getPublishDateIso()).atZone(ZoneId.systemDefault()).toLocalDate();
                        if (fromD != null && pd.isBefore(fromD)) {
                            return false;
                        }
                        if (toD != null && pd.isAfter(toD)) {
                            return false;
                        }
                    } catch (Exception ex) {
                        // Do not hide rows (e.g. borrowed books) when publish date is missing or unparseable
                    }
                }
                return true;
            });
        };

        // Load books from DB and populate the table; filtering (genre + availability) is applied via updateFilter.
        Runnable refresh = () -> {
            String prevGenre = genreFilter.getSelectionModel().getSelectedItem();
            String prevAvail = availabilityFilter.getSelectionModel().getSelectedItem();
            allItems.clear();
            try {
                List<Book> books = BookDao.findAll();
                java.util.Map<Long, BookReviewDao.BookRatingAggregate> aggMap =
                        BookReviewDao.aggregateForBookIds(books.stream().map(Book::getId).toList());
                Set<String> discoveredGenres = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                for (Book b : books) {
                    BookReviewDao.BookRatingAggregate agg = aggMap.getOrDefault(b.getId(),
                            new BookReviewDao.BookRatingAggregate(0, 0));
                    allItems.add(new BookRow(b, agg));
                    discoveredGenres.addAll(tokenizeGenres(b.getGenre()));
                }
                List<String> mergedGenres = new java.util.ArrayList<>(FIXED_GENRES);
                for (String g : discoveredGenres) {
                    if (FIXED_GENRES.stream().noneMatch(x -> x.equalsIgnoreCase(g))) {
                        mergedGenres.add(toTitleCase(g));
                    }
                }
                genreFilter.setItems(FXCollections.observableArrayList(mergedGenres));
                if (prevGenre != null && mergedGenres.stream().anyMatch(x -> x.equalsIgnoreCase(prevGenre))) {
                    mergedGenres.stream().filter(x -> x.equalsIgnoreCase(prevGenre)).findFirst()
                            .ifPresent(g -> genreFilter.getSelectionModel().select(g));
                } else {
                    genreFilter.getSelectionModel().selectFirst();
                }
                if (prevAvail != null && availabilityFilter.getItems().contains(prevAvail)) {
                    availabilityFilter.getSelectionModel().select(prevAvail);
                }
            } catch (SQLException ex) {
                runWithTimerPaused(inactivityTimer,
                        () -> showAlert(Alert.AlertType.ERROR, "Error", "Could not load books."));
            }
            updateFilter.run();
        };
        refresh.run();

        searchField.textProperty().addListener((obs, oldText, newText) -> updateFilter.run());
        genreFilter.setOnAction(e -> updateFilter.run());
        availabilityFilter.setOnAction(e -> updateFilter.run());
        publishFrom.valueProperty().addListener((o, ov, nv) -> updateFilter.run());
        publishTo.valueProperty().addListener((o, ov, nv) -> updateFilter.run());

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
                    showSummaryDialog(table.getScene().getWindow(), bookOpt.get(), selected, currentUser, inactivityTimer, refresh);
                } catch (SQLException ex) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Could not load book details.");
                }
            });
        });

        Button quickReviewBtn = new Button("Quick Preview");
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
                    showQuickReviewDialog(table.getScene().getWindow(), bookOpt.get(), selected, navigator, currentUser, inactivityTimer, refresh);
                } catch (SQLException ex) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Could not load book details.");
                }
            });
        });

        Button borrowBtn = new Button("Borrow Selected");
        borrowBtn.getStyleClass().add("primary-button");
        borrowBtn.setTooltip(new Tooltip("Select a row, or check the Borrow column for multiple books."));
        borrowBtn.setOnAction(e -> {
            List<BookRow> checkedRows = allItems.stream().filter(BookRow::isBorrowSelected).toList();
            BookRow selected = table.getSelectionModel().getSelectedItem();

            if (!checkedRows.isEmpty()) {
                List<Long> ids = checkedRows.stream().map(BookRow::getBookId).distinct().toList();
                boolean hasUnavailable = checkedRows.stream().anyMatch(r -> !"AVAILABLE".equals(r.getAvailability()));
                if (hasUnavailable) {
                    runWithTimerPaused(inactivityTimer,
                            () -> showAlert(Alert.AlertType.WARNING, "Unavailable selection",
                                    "Uncheck borrowed books, or only available titles can be borrowed."));
                    return;
                }
                try {
                    int activeCount = BorrowDao.countActiveByBorrowerUserId(currentUser.getId());
                    if (activeCount + ids.size() > BorrowService.MAX_ACTIVE_BORROWS) {
                        int remaining = Math.max(0, BorrowService.MAX_ACTIVE_BORROWS - activeCount);
                        runWithTimerPaused(inactivityTimer,
                                () -> showAlert(Alert.AlertType.WARNING, "Borrow limit reached",
                                        "You can borrow at most " + remaining + " more book(s) now (limit: "
                                                + BorrowService.MAX_ACTIVE_BORROWS + " active borrows)."));
                        return;
                    }
                } catch (SQLException ignored) {
                    // If we can't read count, fall back to backend validation on confirm.
                }
                String titles = checkedRows.stream().map(BookRow::getTitle)
                        .collect(Collectors.joining("\n• ", "• ", ""));
                String confirmMsg = titles;
                try {
                    int activeCount = BorrowDao.countActiveByBorrowerUserId(currentUser.getId());
                    confirmMsg += "\n\nYou have " + activeCount + " of " + BorrowService.MAX_ACTIVE_BORROWS + " books currently borrowed.";
                    confirmMsg += "\nBorrow duration: " + BorrowService.BORROW_DURATION_DAYS + " days.";
                    Instant dueInstant = Instant.now().plus(BorrowService.BORROW_DURATION_DAYS, ChronoUnit.DAYS);
                    String dueStr = DateTimeFormatter.ISO_LOCAL_DATE.format(dueInstant.atZone(java.time.ZoneId.systemDefault()));
                    confirmMsg += "\nDue date (after borrow): " + dueStr + ".";
                } catch (SQLException ignored) {
                    // Keep multi-title-only confirmation if count fails.
                }
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Confirm Borrow");
                confirm.setHeaderText("Borrow " + ids.size() + " book(s)?");
                confirm.setContentText(confirmMsg);
                runWithTimerPaused(inactivityTimer, () -> {
                    if (confirm.showAndWait().orElse(null) != ButtonType.OK) {
                        return;
                    }
                    try {
                        BorrowService.borrowMany(ids, currentUser.getId());
                        allItems.forEach(r -> r.borrowSelectedProperty().set(false));
                        showAlert(Alert.AlertType.INFORMATION, "Borrow confirmed", "Selected books were borrowed.");
                        refresh.run();
                    } catch (BorrowService.BorrowException ex) {
                        showAlert(Alert.AlertType.ERROR, "Borrow failed", ex.getMessage());
                        refresh.run();
                    } catch (SQLException ex) {
                        showAlert(Alert.AlertType.ERROR, "Borrow failed", "A database error occurred.");
                        refresh.run();
                    }
                });
                return;
            }

            if (selected == null) {
                runWithTimerPaused(inactivityTimer,
                        () -> showAlert(Alert.AlertType.WARNING, "No selection",
                                "Select a book in the table, or check one or more rows in the Borrow column."));
                return;
            }
            if (!"AVAILABLE".equals(selected.getAvailability())) {
                runWithTimerPaused(inactivityTimer,
                        () -> showAlert(Alert.AlertType.WARNING, "Unavailable", "Only available books can be borrowed."));
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

        // Button myBorrowedBtn = new Button("Go to My Borrowed Books");
        // myBorrowedBtn.getStyleClass().add("secondary-button");
        // myBorrowedBtn.setOnAction(e -> navigator.showMyBorrowedBooks(currentUser));

        // // Logout returns to Student/Staff portal (entry screen)
        // Button logoutBtn = new Button("Logout");
        // logoutBtn.getStyleClass().add("secondary-button");
        // logoutBtn.setOnAction(e -> navigator.showStudentStaffPortal());

        readSummaryBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        quickReviewBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        Button reviewsBtn = new Button("Reviews");
        reviewsBtn.getStyleClass().add("secondary-button");
        reviewsBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        reviewsBtn.setOnAction(e -> {
            BookRow selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                runWithTimerPaused(inactivityTimer,
                        () -> showAlert(Alert.AlertType.WARNING, "No selection", "Select a book first."));
                return;
            }
            runWithTimerPaused(inactivityTimer, () -> {
                try {
                    var bookOpt = BookDao.findById(selected.getBookId());
                    if (bookOpt.isEmpty()) {
                        showAlert(Alert.AlertType.ERROR, "Error", "Book not found.");
                        return;
                    }
                    showReviewsAndRateDialog(table.getScene().getWindow(), bookOpt.get(), currentUser, refresh);
                } catch (SQLException ex) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Could not load reviews.");
                }
            });
        });

        Label searchLbl = new Label("Search");
        searchLbl.getStyleClass().add("field-label");
        Label genreLbl = new Label("Genre");
        genreLbl.getStyleClass().add("field-label");
        Label availabilityLbl = new Label("Availability");
        availabilityLbl.getStyleClass().add("field-label");
        HBox searchRow = new HBox(8,
                searchLbl, searchField,
                genreLbl, genreFilter,
                availabilityLbl, availabilityFilter,
                publishFrom, publishTo);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        searchRow.setSpacing(10);

        HBox recBox = new HBox(8);
        recBox.setAlignment(Pos.CENTER_LEFT);
        Label recLbl = new Label("Recommended for You");
        recLbl.getStyleClass().add("field-label");
        recLbl.setTooltip(new Tooltip("Based on genres you have borrowed before; otherwise popular available titles."));
        recBox.getChildren().add(recLbl);
        try {
            int recLimit = 3;
            for (Book rb : BookDao.findRecommendedForUserAvailable(currentUser.getId(), recLimit)) {
                CheckBox chip = new CheckBox(rb.getTitle());
                chip.setUserData(rb.getId());
                chip.selectedProperty().addListener((o, ov, nv) -> {
                    allItems.stream().filter(r -> r.getBookId() == rb.getId()).findFirst()
                            .ifPresent(r -> r.borrowSelectedProperty().set(Boolean.TRUE.equals(nv)));
                });
                recBox.getChildren().add(chip);
            }
        } catch (SQLException ignored) {
            try {
                int recLimit = 3;
                for (Book rb : BookDao.findRecommendedAvailable(recLimit)) {
                    CheckBox chip = new CheckBox(rb.getTitle());
                    chip.setUserData(rb.getId());
                    chip.selectedProperty().addListener((o, ov, nv) -> {
                        allItems.stream().filter(r -> r.getBookId() == rb.getId()).findFirst()
                                .ifPresent(r -> r.borrowSelectedProperty().set(Boolean.TRUE.equals(nv)));
                    });
                    recBox.getChildren().add(chip);
                }
            } catch (SQLException ignored2) {
                // Keep empty recommendations area if even fallback fails.
            }
        }

        Label recEmptyHint = new Label();
        recEmptyHint.setWrapText(true);
        recEmptyHint.getStyleClass().add("login-hint");
        if (recBox.getChildren().size() <= 1) {
            recEmptyHint.setText("No quick picks right now. Borrow a few books so recommendations can follow the genres you use.");
        }

        Label loggedInLabel = new Label("Logged in as: " + currentUser.getFullName() + " (" + currentUser.getUsername() + ")");

        VBox headerBox = new VBox(4, title, subtitle, loggedInLabel);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        VBox tableContainer = new VBox(table);
        tableContainer.getStyleClass().add("table-container");
        tableContainer.setPadding(new Insets(10));

        // Group primary and navigation actions
        // Button profileBtn = new Button("Manage Profile");
        // profileBtn.getStyleClass().add("secondary-button");
        // profileBtn.setTooltip(new Tooltip("Open your profile settings"));
        // profileBtn.setOnAction(e -> navigator.showStudentStaffProfile(currentUser));
        // Button notifBtn = new Button("Notification Board");
        // notifBtn.getStyleClass().add("secondary-button");
        // notifBtn.setTooltip(new Tooltip("View unread and archived notifications"));
        // notifBtn.setOnAction(e -> navigator.showStudentStaffNotifications(currentUser, false));
        // try {
        //     int unread = NotificationDao.countUnread(currentUser.getId());
        //     if (unread > 0) {
        //         notifBtn.setText("Notification Board (" + unread + ")");
        //     }
        // } catch (SQLException ignored) {
        // }

        FlowPane buttons = new FlowPane();
        buttons.setHgap(10);
        buttons.setVgap(10);
        buttons.getChildren().addAll(
                borrowBtn,
                readSummaryBtn,
                quickReviewBtn,
                reviewsBtn
        );
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.setPadding(new Insets(10, 0, 0, 0));
        buttons.getStyleClass().add("button-bar");

        VBox top = new VBox(10, headerBox, recBox, recEmptyHint, searchRow);
        top.setPadding(new Insets(10));

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
    private static void showSummaryDialog(Window owner, Book book, BookRow row, User currentUser,
                                         PauseTransition inactivityTimer, Runnable refresh) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) dialog.initOwner(owner);
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

        final boolean[] borrowRequested = {false};
        Button borrowBtn = new Button("Borrow this book");
        borrowBtn.getStyleClass().add("primary-button");
        borrowBtn.setOnAction(ev -> {
            // Avoid nested modal dialogs (dialog.close() + confirmation showAndWait())
            // which can leave the outer summary dialog in a weird state.
            borrowRequested[0] = true;
            dialog.close();
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
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().setAll(ButtonType.CANCEL);
        javafx.scene.Node systemCloseBtn = pane.lookupButton(ButtonType.CANCEL);
        if (systemCloseBtn != null) {
            systemCloseBtn.setManaged(false);
            systemCloseBtn.setVisible(false);
        }
        pane.setContent(scroll);
        java.net.URL cssResource = AvailableBooksScreen.class.getResource("/app.css");
        if (cssResource != null) {
            pane.getStylesheets().add(cssResource.toExternalForm());
        }
        pane.setPrefSize(summaryDialogWidth, Math.max(summaryDialogHeight, height));
        dialog.setResizable(true);
        dialog.setOnShown(ev -> restoreDialogBounds(dialog, summaryDialogX, summaryDialogY, summaryDialogWidth, Math.max(summaryDialogHeight, height)));
        dialog.setOnHiding(ev -> {
            Stage stage = extractDialogStage(dialog);
            if (stage != null) {
                summaryDialogWidth = stage.getWidth();
                summaryDialogHeight = stage.getHeight();
                summaryDialogX = stage.getX();
                summaryDialogY = stage.getY();
            }
        });
        dialog.showAndWait();

        if (borrowRequested[0]) {
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
        }
    }

    /**
     * Shows a Quick Review dialog for the given book in a reader-style layout:
     * header with title/author and a central scrollable preview area (first few pages).
     * User can close or choose to borrow from the dialog.
     */
    private static void showQuickReviewDialog(Window owner, Book book, BookRow row,
                                             Navigator navigator, User currentUser,
                                             PauseTransition inactivityTimer, Runnable refresh) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Quick Review — " + book.getTitle());

        VBox rootContent = new VBox(16);
        rootContent.setPadding(new Insets(20));

        // Header: title + author
        Label titleLabel = new Label(book.getTitle());
        titleLabel.getStyleClass().add("screen-title");

        Label authorLabel = new Label("by " + book.getAuthorFullNameSnapshot());
        authorLabel.setStyle("-fx-font-size: 13px;");

        Label hintLabel = new Label("Preview \u2014 first few pages");
        hintLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        VBox headerBox = new VBox(4, titleLabel, authorLabel, hintLabel);

        // Reader-style preview area: prefer PDF page images when available, otherwise fall back to text
        VBox pageCard = new VBox(12);
        pageCard.setPadding(new Insets(16));
        pageCard.getStyleClass().add("content-card");

        boolean isPdf = book.getFilePath() != null && book.getFilePath().toLowerCase().endsWith(".pdf");
        if (isPdf) {
            java.util.List<Image> pages = BookPreviewUtil.readPdfPreviewImages(book.getFilePath(), 5);
            if (!pages.isEmpty()) {
                for (Image img : pages) {
                    ImageView imageView = new ImageView(img);
                    imageView.setPreserveRatio(true);
                    imageView.setFitWidth(860);
                    pageCard.getChildren().add(imageView);
                }
            } else {
                addTextPreviewFallback(pageCard, book);
            }
        } else {
            addTextPreviewFallback(pageCard, book);
        }

        ScrollPane readerScroll = new ScrollPane(pageCard);
        readerScroll.setFitToWidth(true);
        readerScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        readerScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        final boolean[] borrowRequested = {false};
        // Actions: Borrow / Close
        Button borrowBtn = new Button("Borrow this book");
        borrowBtn.getStyleClass().add("primary-button");
        borrowBtn.setOnAction(ev -> {
            // Close first, then run confirmation + borrow after the dialog's nested loop ends.
            borrowRequested[0] = true;
            dialog.close();
        });

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setOnAction(ev -> dialog.close());

        HBox buttonRow = new HBox(10, borrowBtn, closeBtn);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);
        buttonRow.setPadding(new Insets(10, 0, 0, 0));

        rootContent.getChildren().addAll(headerBox, readerScroll, buttonRow);

        ScrollPane rootScroll = new ScrollPane(rootContent);
        rootScroll.setFitToWidth(true);
        rootScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rootScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().setAll(ButtonType.CANCEL);
        javafx.scene.Node systemCloseBtn = pane.lookupButton(ButtonType.CANCEL);
        if (systemCloseBtn != null) {
            systemCloseBtn.setManaged(false);
            systemCloseBtn.setVisible(false);
        }
        pane.setContent(rootScroll);
        java.net.URL cssResource = AvailableBooksScreen.class.getResource("/app.css");
        if (cssResource != null) {
            pane.getStylesheets().add(cssResource.toExternalForm());
        }
        pane.setPrefSize(quickDialogWidth, quickDialogHeight);
        dialog.setResizable(true);
        dialog.setOnShown(ev -> restoreDialogBounds(dialog, quickDialogX, quickDialogY, quickDialogWidth, quickDialogHeight));
        dialog.setOnHiding(ev -> {
            Stage stage = extractDialogStage(dialog);
            if (stage != null) {
                quickDialogWidth = stage.getWidth();
                quickDialogHeight = stage.getHeight();
                quickDialogX = stage.getX();
                quickDialogY = stage.getY();
            }
        });
        dialog.showAndWait();

        if (borrowRequested[0]) {
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
        }
    }

    /**
     * Adds a text-based preview into the given pageCard, using extracted content when possible,
     * or falling back to the book summary.
     */
    private static void addTextPreviewFallback(VBox pageCard, Book book) {
        String previewText = BookPreviewUtil.readTextPreview(book.getFilePath());
        if (previewText == null || previewText.isBlank()) {
            String summary = book.getSummary() != null && !book.getSummary().isBlank()
                    ? book.getSummary()
                    : "Preview could not be loaded (file missing, unsupported format, or read error).";
            previewText = summary;
        }
        TextArea previewArea = new TextArea(previewText);
        previewArea.setEditable(false);
        previewArea.setWrapText(true);
        previewArea.setStyle("-fx-background-color: white; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        previewArea.setPrefRowCount(20);
        pageCard.getChildren().add(previewArea);
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

    private static Stage extractDialogStage(Dialog<?> dialog) {
        if (dialog == null || dialog.getDialogPane() == null || dialog.getDialogPane().getScene() == null) {
            return null;
        }
        Window window = dialog.getDialogPane().getScene().getWindow();
        if (window instanceof Stage stage) {
            return stage;
        }
        return null;
    }

    private static void restoreDialogBounds(Dialog<?> dialog, Double x, Double y, double width, double height) {
        Stage stage = extractDialogStage(dialog);
        if (stage == null) {
            return;
        }
        if (width > 0 && height > 0) {
            stage.setWidth(width);
            stage.setHeight(height);
        }
        if (x != null && y != null && Double.isFinite(x) && Double.isFinite(y)) {
            stage.setX(x);
            stage.setY(y);
        }
    }

    private static Set<String> tokenizeGenres(String raw) {
        if (raw == null || raw.isBlank()) return java.util.Collections.emptySet();
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    private static String toTitleCase(String s) {
        if (s == null || s.isBlank()) return "";
        String[] parts = s.split("\\s+");
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!b.isEmpty()) b.append(' ');
            b.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) b.append(p.substring(1).toLowerCase());
        }
        return b.toString();
    }

    /**
     * Table row model for Book (TableView needs getters for property names).
     * Wraps a {@link Book} for display, including formatted publish date and availability label.
     */
    public static class BookRow {
        private final long bookId;
        private final String title;
        private final String author;
        private final String genre;
        private final String publishDateDisplay;
        private final String publishDateIso;
        private final String availability;
        private final String summary;
        private final String avgRatingDisplay;
        private final String reviewCountDisplay;
        private final BooleanProperty borrowSelected = new SimpleBooleanProperty(false);

        public BookRow(Book b, BookReviewDao.BookRatingAggregate agg) {
            this.bookId = b.getId();
            this.title = b.getTitle();
            this.author = b.getAuthorFullNameSnapshot();
            this.genre = b.getGenre() != null ? b.getGenre() : "";
            String p = b.getPublishDate() != null ? b.getPublishDate() : Instant.now().toString();
            this.publishDateIso = p;
            this.publishDateDisplay = formatPublishDate(p);
            this.availability = b.getAvailability().name();
            this.summary = b.getSummary() != null ? b.getSummary() : "";
            if (agg != null && agg.reviewCount() > 0) {
                this.avgRatingDisplay = agg.averageDisplay();
                this.reviewCountDisplay = String.valueOf(agg.reviewCount());
            } else {
                this.avgRatingDisplay = "—";
                this.reviewCountDisplay = "0";
            }
        }

        public BooleanProperty borrowSelectedProperty() { return borrowSelected; }
        public boolean isBorrowSelected() { return borrowSelected.get(); }

        public String getGenre() { return genre; }
        public String getPublishDateIso() { return publishDateIso; }

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
        public String getAvgRatingDisplay() { return avgRatingDisplay; }
        public String getReviewCountDisplay() { return reviewCountDisplay; }
    }

    private static void applyAvailabilityTextColor(TableCell<BookRow, String> cell, BookRow row) {
        if (row != null && !"AVAILABLE".equals(row.getAvailability())) {
            cell.setStyle("-fx-text-fill: #8b0000;");
        } else {
            cell.setStyle("-fx-text-fill: #000000;");
        }
    }

    /** Consistent black (available) vs deep red (on loan) tint for catalog table text cells. */
    private static void styleCatalogStringCell(TableCell<BookRow, String> cell, String item, boolean empty) {
        if (empty || item == null) {
            cell.setText(null);
            cell.setStyle("");
            return;
        }
        cell.setText(item);
        BookRow row = cell.getTableRow() != null ? cell.getTableRow().getItem() : null;
        applyAvailabilityTextColor(cell, row);
    }

    /**
     * Opens the catalog review/rating dialog for a book. Shared from Available Books, My Borrowed Books, and Reading History.
     */
    public static void showReviewsAndRateDialog(Window owner, Book book, User currentUser, Runnable refreshList) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle("Reviews — " + book.getTitle());

        BookReviewDao.BookRatingAggregate agg;
        try {
            agg = BookReviewDao.aggregateForBook(book.getId());
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not load ratings.");
            return;
        }

        Label header = new Label("Average " + agg.averageDisplay() + " · " + agg.reviewCount() + " review(s)");
        ComboBox<String> sortBox = new ComboBox<>(FXCollections.observableArrayList("Recent", "Helpful"));
        sortBox.getSelectionModel().selectFirst();

        ListView<BookReviewDao.CatalogReviewRow> list = new ListView<>();
        list.setPrefHeight(220);
        Runnable reloadList = () -> {
            try {
                String sort = "Helpful".equals(sortBox.getSelectionModel().getSelectedItem()) ? "HELPFUL" : "RECENT";
                list.setItems(FXCollections.observableArrayList(BookReviewDao.findPublicReviewsForBook(book.getId(), sort)));
            } catch (SQLException ex) {
                list.setItems(FXCollections.observableArrayList());
            }
        };
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(BookReviewDao.CatalogReviewRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                VBox box = new VBox(4);
                Label line1 = new Label(item.displayName() + " · " + item.rating() + "/5 · helpful " + item.helpfulVotes());
                line1.setStyle("-fx-font-weight: bold;");
                TextArea ta = new TextArea(item.reviewText() != null ? item.reviewText() : "");
                ta.setEditable(false);
                ta.setWrapText(true);
                ta.setPrefRowCount(2);
                Button helpful = new Button("Mark helpful");
                helpful.getStyleClass().add("secondary-button");
                try {
                    boolean marked = BookReviewDao.userMarkedHelpful(item.id(), currentUser.getId());
                    helpful.setDisable(marked || item.reviewerUserId() == currentUser.getId());
                    helpful.setText(marked ? "You marked helpful" : "Mark helpful");
                } catch (SQLException ex) {
                    helpful.setDisable(true);
                }
                helpful.setOnAction(ev -> {
                    try {
                        if (BookReviewDao.incrementHelpful(item.id(), currentUser.getId())) {
                            reloadList.run();
                        }
                    } catch (SQLException ex) {
                        // ignore
                    }
                });
                box.getChildren().addAll(line1, ta, helpful);
                setGraphic(box);
                setText(null);
            }
        });
        reloadList.run();
        sortBox.setOnAction(e -> reloadList.run());

        Separator sep = new Separator();

        Label submitHead = new Label("Your review (requires borrow history for this title)");
        Spinner<Integer> ratingSpin = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 5, 5, 1));
        TextArea yourReview = new TextArea();
        yourReview.setPromptText("Optional review text");
        yourReview.setWrapText(true);
        yourReview.setPrefRowCount(3);
        CheckBox anon = new CheckBox("Post anonymously");

        try {
            BookReviewDao.findOwnReview(book.getId(), currentUser.getId()).ifPresent(draft -> {
                ratingSpin.getValueFactory().setValue(draft.rating());
                yourReview.setText(draft.reviewText() != null ? draft.reviewText() : "");
                anon.setSelected(draft.anonymous());
            });
        } catch (SQLException ignored) {
            // Leave defaults if load fails
        }

        Button submit = new Button("Submit / update my review");
        submit.getStyleClass().add("primary-button");
        submit.setOnAction(ev -> {
            try {
                if (!BookReviewDao.hasUserEverBorrowedBook(currentUser.getId(), book.getId())) {
                    Alert needBorrow = new Alert(Alert.AlertType.INFORMATION);
                    needBorrow.setHeaderText(null);
                    needBorrow.setContentText(
                            "You can rate or review only after you have borrowed this book at least once.");
                    needBorrow.showAndWait();
                    return;
                }
                int r = ratingSpin.getValue() == null ? 5 : ratingSpin.getValue();
                String txt = yourReview.getText() == null ? "" : yourReview.getText().trim();
                BookReviewDao.upsertStudentReview(book.getId(), currentUser.getId(), r,
                        txt.isEmpty() ? null : txt, anon.isSelected(), Instant.now().toString());
                reloadList.run();
                refreshList.run();
                Alert saved = new Alert(Alert.AlertType.INFORMATION);
                saved.setTitle("Saved");
                saved.setHeaderText(null);
                saved.setContentText("Your review was saved.");
                saved.showAndWait();
            } catch (SQLException ex) {
                Alert err = new Alert(Alert.AlertType.ERROR);
                err.setTitle("Save failed");
                err.setHeaderText(null);
                err.setContentText(ex.getMessage() != null ? ex.getMessage() : "Could not save review.");
                err.showAndWait();
            }
        });

        VBox root = new VBox(10, header, new HBox(8, new Label("Sort:"), sortBox), list, sep, submitHead,
                new Label("Rating (1–5):"), ratingSpin, yourReview, anon, submit);
        root.setPadding(new Insets(16));
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        DialogPane pane = dialog.getDialogPane();
        pane.setContent(sp);
        pane.getButtonTypes().add(ButtonType.CLOSE);
        java.net.URL cssResource = AvailableBooksScreen.class.getResource("/app.css");
        if (cssResource != null) {
            pane.getStylesheets().add(cssResource.toExternalForm());
        }
        pane.setPrefSize(520, 560);
        dialog.showAndWait();
    }
}

