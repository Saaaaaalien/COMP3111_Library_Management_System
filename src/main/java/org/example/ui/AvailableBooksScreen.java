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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.app.Navigator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.example.db.BookDao;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
                if ("AVAILABLE".equals(item.getAvailability())) {
                    row.setStyle("-fx-text-fill: #1565c0;");
                } else {
                    row.setStyle("");
                }
            });
            return row;
        });

        TableColumn<BookRow, Boolean> colPick = new TableColumn<>("Borrow");
        colPick.setCellValueFactory(data -> data.getValue().borrowSelectedProperty());
        colPick.setCellFactory(CheckBoxTableCell.forTableColumn(colPick));
        colPick.setPrefWidth(70);

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

        TableColumn<BookRow, String> colGenre = new TableColumn<>("Genre");
        colGenre.setCellValueFactory(new PropertyValueFactory<>("genre"));
        colGenre.setPrefWidth(100);

        TableColumn<BookRow, String> colSummary = new TableColumn<>("Abstract / Summary");
        colSummary.setCellValueFactory(new PropertyValueFactory<>("summary"));
        colSummary.setPrefWidth(SUMMARY_PREF_WIDTH);

        table.getColumns().addAll(List.of(colPick, colTitle, colAuthor, colGenre, colPublishDate, colAvailability, colSummary));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Inactivity timer: return to portal after 15 minutes with no input
        PauseTransition inactivityTimer = new PauseTransition(Duration.minutes(15));
        inactivityTimer.setOnFinished(ev -> navigator.showStudentStaffPortal());

        TextField searchField = new TextField();
        searchField.setPromptText("Search by title or author...");
        searchField.setMaxWidth(300);

        ComboBox<String> genreFilter = new ComboBox<>(FXCollections.observableArrayList("All genres"));
        genreFilter.getSelectionModel().selectFirst();
        DatePicker publishFrom = new DatePicker();
        publishFrom.setPromptText("Published from");
        DatePicker publishTo = new DatePicker();
        publishTo.setPromptText("Published to");

        Runnable updateFilter = () -> {
            String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
            String gSel = genreFilter.getSelectionModel().getSelectedItem();
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
                    if (!gSel.equalsIgnoreCase(row.getGenre())) {
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
                        return false;
                    }
                }
                return true;
            });
        };

        // Load available books from DB and populate the table; run on init and after each borrow
        Runnable refresh = () -> {
            allItems.clear();
            try {
                List<Book> books = BookDao.findAllAvailable();
                Set<String> genres = new HashSet<>();
                for (Book b : books) {
                    allItems.add(new BookRow(b));
                    if (b.getGenre() != null && !b.getGenre().isBlank()) {
                        genres.add(b.getGenre());
                    }
                }
                List<String> gItems = new ArrayList<>();
                gItems.add("All genres");
                gItems.addAll(genres.stream().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList()));
                genreFilter.setItems(FXCollections.observableArrayList(gItems));
                genreFilter.getSelectionModel().selectFirst();
            } catch (SQLException ex) {
                runWithTimerPaused(inactivityTimer,
                        () -> showAlert(Alert.AlertType.ERROR, "Error", "Could not load books."));
            }
            updateFilter.run();
        };
        refresh.run();

        searchField.textProperty().addListener((obs, oldText, newText) -> updateFilter.run());
        genreFilter.setOnAction(e -> updateFilter.run());
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

        Button borrowManyBtn = new Button("Borrow checked books");
        borrowManyBtn.getStyleClass().add("primary-button");
        borrowManyBtn.setOnAction(e -> {
            List<Long> ids = allItems.stream().filter(BookRow::isBorrowSelected).map(BookRow::getBookId).distinct().toList();
            if (ids.isEmpty()) {
                runWithTimerPaused(inactivityTimer,
                        () -> showAlert(Alert.AlertType.WARNING, "None selected", "Check one or more books in the Borrow column."));
                return;
            }
            String titles = allItems.stream().filter(BookRow::isBorrowSelected).map(BookRow::getTitle)
                    .collect(Collectors.joining("\n• ", "• ", ""));
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Borrow multiple");
            confirm.setHeaderText("Borrow " + ids.size() + " book(s)?");
            confirm.setContentText(titles);
            runWithTimerPaused(inactivityTimer, () -> {
                if (confirm.showAndWait().orElse(null) != ButtonType.OK) {
                    return;
                }
                try {
                    BorrowService.borrowMany(ids, currentUser.getId());
                    allItems.forEach(r -> r.borrowSelectedProperty().set(false));
                    showAlert(Alert.AlertType.INFORMATION, "Borrowed", "Selected books were borrowed.");
                    refresh.run();
                } catch (BorrowService.BorrowException ex) {
                    showAlert(Alert.AlertType.ERROR, "Borrow failed", ex.getMessage());
                    refresh.run();
                } catch (SQLException ex) {
                    showAlert(Alert.AlertType.ERROR, "Borrow failed", "A database error occurred.");
                    refresh.run();
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

        // Disable book actions when nothing is selected
        readSummaryBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        quickReviewBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        borrowBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        HBox searchRow = new HBox(8,
                new Label("Search:"), searchField,
                new Label("Genre:"), genreFilter,
                publishFrom, publishTo);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        searchRow.setSpacing(10);

        HBox recBox = new HBox(8);
        recBox.setAlignment(Pos.CENTER_LEFT);
        recBox.getChildren().add(new Label("Popular picks:"));
        try {
            for (Book rb : BookDao.findRecommendedAvailable(5)) {
                CheckBox chip = new CheckBox(rb.getTitle());
                chip.setUserData(rb.getId());
                chip.selectedProperty().addListener((o, ov, nv) -> {
                    allItems.stream().filter(r -> r.getBookId() == rb.getId()).findFirst()
                            .ifPresent(r -> r.borrowSelectedProperty().set(Boolean.TRUE.equals(nv)));
                });
                recBox.getChildren().add(chip);
            }
        } catch (SQLException ignored) {
        }

        Label loggedInLabel = new Label("Logged in as: " + currentUser.getFullName() + " (" + currentUser.getUsername() + ")");

        VBox headerBox = new VBox(4, title, subtitle, loggedInLabel);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        VBox tableContainer = new VBox(table);
        tableContainer.getStyleClass().add("table-container");
        tableContainer.setPadding(new Insets(10));

        // Group primary and navigation actions
        Button profileBtn = new Button("Profile");
        profileBtn.setOnAction(e -> navigator.showStudentStaffProfile(currentUser));
        Button notifBtn = new Button("Notifications");
        notifBtn.setOnAction(e -> navigator.showStudentStaffNotifications(currentUser));

        HBox leftActions = new HBox(10, readSummaryBtn, quickReviewBtn, borrowBtn, borrowManyBtn, profileBtn, notifBtn);
        leftActions.setAlignment(Pos.CENTER_LEFT);
        HBox rightActions = new HBox(10, myBorrowedBtn, logoutBtn);
        rightActions.setAlignment(Pos.CENTER_RIGHT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox buttons = new HBox(10, leftActions, spacer, rightActions);
        buttons.setPadding(new Insets(10, 0, 0, 0));
        buttons.getStyleClass().add("button-bar");

        VBox content = new VBox(16, headerBox, recBox, searchRow, tableContainer, buttons);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(10));
        content.setMaxWidth(900);
        content.getStyleClass().add("content-card");

        BorderPane root = new BorderPane();
        root.setCenter(content);
        BorderPane.setAlignment(content, Pos.CENTER);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("app-root");

        root.addEventFilter(MouseEvent.ANY, ev -> inactivityTimer.playFromStart());
        root.addEventFilter(KeyEvent.ANY, ev -> inactivityTimer.playFromStart());
        inactivityTimer.play();

        ScrollPane scrollRoot = new ScrollPane(root);
        scrollRoot.setFitToHeight(true);
        scrollRoot.setFitToWidth(false);
        scrollRoot.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollRoot.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollRoot.setPannable(true);

        Scene scene = new Scene(scrollRoot, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
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
     * Shows a Quick Review dialog for the given book in a reader-style layout:
     * header with title/author and a central scrollable preview area (first few pages).
     * User can close or choose to borrow from the dialog.
     */
    private static void showQuickReviewDialog(Book book, BookRow row,
                                             Navigator navigator, User currentUser,
                                             PauseTransition inactivityTimer, Runnable refresh) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
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
                    imageView.setFitWidth(520);
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

        // Actions: Borrow / Close
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

        HBox buttonRow = new HBox(10, borrowBtn, closeBtn);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);
        buttonRow.setPadding(new Insets(10, 0, 0, 0));

        rootContent.getChildren().addAll(headerBox, readerScroll, buttonRow);

        ScrollPane rootScroll = new ScrollPane(rootContent);
        rootScroll.setFitToWidth(true);
        rootScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rootScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Scene dialogScene = new Scene(rootScroll, 700, 700);
        java.net.URL cssResource = AvailableBooksScreen.class.getResource("/app.css");
        if (cssResource != null) {
            dialogScene.getStylesheets().add(cssResource.toExternalForm());
        }
        dialog.setScene(dialogScene);
        dialog.showAndWait();
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
        private final BooleanProperty borrowSelected = new SimpleBooleanProperty(false);

        public BookRow(Book b) {
            this.bookId = b.getId();
            this.title = b.getTitle();
            this.author = b.getAuthorFullNameSnapshot();
            this.genre = b.getGenre() != null ? b.getGenre() : "";
            String p = b.getPublishDate() != null ? b.getPublishDate() : Instant.now().toString();
            this.publishDateIso = p;
            this.publishDateDisplay = formatPublishDate(p);
            this.availability = b.getAvailability().name();
            this.summary = b.getSummary() != null ? b.getSummary() : "";
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
    }
}

