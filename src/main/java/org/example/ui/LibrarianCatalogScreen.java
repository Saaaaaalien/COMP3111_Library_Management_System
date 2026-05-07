package org.example.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.example.app.Navigator;
import org.example.db.BookDao;
import org.example.db.BorrowDao;
import org.example.db.UserDao;
import org.example.domain.Availability;
import org.example.domain.Book;
import org.example.domain.BookChangeLog;
import org.example.domain.Role;
import org.example.domain.User;
import org.example.service.BookRemovalService;
import org.example.service.BookSummaryService;
import org.example.service.BulkBookOperationService;
import org.example.util.BookPreviewUtil;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Librarian screen for managing all published books.
 */
public final class LibrarianCatalogScreen {

    private static final long MAX_BOOK_FILE_SIZE = 10L * 1024 * 1024;
    private static final long MAX_COVER_FILE_SIZE = 2L * 1024 * 1024;
    private static final String PUBLISHED_UPLOAD_DIR = System.getProperty("user.home")
            + File.separator + "library_uploads"
            + File.separator + "published";
    private static final String PUBLISHED_COVER_DIR = "data" + File.separator + "covers";

    private LibrarianCatalogScreen() {}

    public static final class Row {
        private final long id;
        private final String title;
        private final String author;
        private final String genre;
        private final String availability;

        public Row(Book b) {
            this.id = b.getId();
            this.title = b.getTitle();
            this.author = b.getAuthorFullNameSnapshot();
            this.genre = b.getGenre();
            this.availability = b.getAvailability().name();
        }

        public long getId() { return id; }
        public String getTitle() { return title; }
        public String getAuthor() { return author; }
        public String getGenre() { return genre; }
        public String getAvailability() { return availability; }
    }

    private record BookFormData(String title,
                                long primaryAuthorUserId,
                                String authorNames,
                                String genre,
                                String description,
                                File bookFile,
                                File coverFile) {
    }

    public static Scene create(Navigator navigator, User librarian) {
        Label title = new Label("Manage Published Books");
        title.getStyleClass().add("screen-title");

        TableView<Row> table = new TableView<>();
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        var items = FXCollections.<Row>observableArrayList();

        TableColumn<Row, String> c1 = new TableColumn<>("Title");
        c1.setCellValueFactory(new PropertyValueFactory<>("title"));
        c1.setPrefWidth(280);

        TableColumn<Row, String> c2 = new TableColumn<>("Author Names");
        c2.setCellValueFactory(new PropertyValueFactory<>("author"));
        c2.setPrefWidth(250);

        TableColumn<Row, String> c3 = new TableColumn<>("Genre");
        c3.setCellValueFactory(new PropertyValueFactory<>("genre"));
        c3.setPrefWidth(180);

        TableColumn<Row, String> c4 = new TableColumn<>("Status");
        c4.setCellValueFactory(new PropertyValueFactory<>("availability"));
        c4.setPrefWidth(120);

        table.getColumns().addAll(List.of(c1, c2, c3, c4));
        table.setItems(items);

        Runnable refresh = () -> {
            items.clear();
            try {
                for (Book b : BookDao.findAll()) {
                    items.add(new Row(b));
                }
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Load Failed", ex.getMessage());
            }
        };
        refresh.run();

        Button addBtn = new Button("Add New Book");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> {
            navigator.showLibrarianPublishBook(librarian);
        });

        Button editBtn = new Button("Edit Selected Book");
        editBtn.getStyleClass().add("secondary-button");
        editBtn.setOnAction(e -> {
            Row sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) {
                showAlert(Alert.AlertType.WARNING, "No Selection", "Select a published book to edit.");
                return;
            }
            try {
                if (BorrowDao.countActiveBorrowsForBook(sel.getId()) > 0) {
                    showAlert(Alert.AlertType.WARNING, "Book Borrowed",
                            "Cannot edit this book while it is borrowed. Please try again after all students/staff have returned it.");
                    return;
                }
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Error Checking Status", ex.getMessage());
                return;
            }
            navigator.showLibrarianEditPublishedBook(librarian, sel.getId());
        });

        Button removeBtn = new Button("Remove Selected from Catalog");
        removeBtn.getStyleClass().add("secondary-button");
        removeBtn.setOnAction(e -> {
            Row sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) {
                showAlert(Alert.AlertType.WARNING, "No Selection", "Select a book.");
                return;
            }
            if (!confirm("Confirm Removal",
                    "Remove \"" + sel.getTitle() + "\" from catalog? Active borrowers will be notified and loans closed.")) {
                return;
            }
            try {
                BookRemovalService.removePublishedBook(sel.getId());
                showAlert(Alert.AlertType.INFORMATION, "Success", "Book removed.");
                refresh.run();
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Removal Failed", ex.getMessage());
            }
        });

        Button bulkEditBtn = new Button("Bulk Edit Selected");
        bulkEditBtn.getStyleClass().add("secondary-button");
        bulkEditBtn.setOnAction(e -> {
            showBulkEditDialog(table, librarian, refresh);
        });

        Button bulkDeleteBtn = new Button("Bulk Delete Selected");
        bulkDeleteBtn.getStyleClass().add("secondary-button");
        bulkDeleteBtn.setStyle("-fx-text-fill: white; -fx-background-color: #e74c3c;");
        bulkDeleteBtn.setOnAction(e -> {
            showBulkDeleteDialog(table, librarian, refresh);
        });

        Button versionHistoryBtn = new Button("View Edit History");
        versionHistoryBtn.getStyleClass().add("secondary-button");
        versionHistoryBtn.setOnAction(e -> {
            Row sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) {
                showAlert(Alert.AlertType.WARNING, "No Selection", "Select a book to view its history.");
                return;
            }
            showVersionHistoryDialog(sel.getId());
        });

        // Button approvalBtn = new Button("Pending Approvals");
        // approvalBtn.getStyleClass().add("secondary-button");
        // approvalBtn.setOnAction(e -> navigator.showLibrarianApproval(librarian));

        // FILTER UI
        VBox filterBox = createFilterBox(table, items, refresh);

        // Navigation handled by global menu; removed per-screen portal/logout button
        HBox bar1 = new HBox(10, addBtn, editBtn, removeBtn);
        bar1.setPadding(new Insets(8, 0, 0, 0));
        
        HBox bar2 = new HBox(10, bulkEditBtn, bulkDeleteBtn, versionHistoryBtn);
        bar2.setPadding(new Insets(8, 0, 0, 0));

        VBox top = new VBox(8, title, bar1, bar2, filterBox);
        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(table);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        var css = LibrarianCatalogScreen.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }

    /**
     * Creates the advanced filter UI component.
     */
    private static VBox createFilterBox(TableView<Row> table, javafx.collections.ObservableList<Row> items, Runnable refresh) {
        VBox filterBox = new VBox(8);
        filterBox.setStyle("-fx-border-color: #bdc3c7; -fx-border-width: 1; -fx-padding: 10;");
        
        Label filterLabel = new Label("Advanced Filters:");
        filterLabel.setStyle("-fx-font-weight: bold;");
        
        HBox filterControls = new HBox(10);
        filterControls.setAlignment(Pos.CENTER_LEFT);
        
        ComboBox<String> genreCombo = new ComboBox<>();
        genreCombo.setPromptText("All Genres");
        genreCombo.setPrefWidth(150);
        
        ComboBox<String> authorCombo = new ComboBox<>();
        authorCombo.setPromptText("All Authors");
        authorCombo.setPrefWidth(150);
        
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.setItems(FXCollections.observableArrayList("All Statuses", "AVAILABLE", "BORROWED"));
        statusCombo.setPromptText("All Statuses");
        statusCombo.setPrefWidth(150);
        
        Button applyFiltersBtn = new Button("Apply Filters");
        applyFiltersBtn.getStyleClass().add("secondary-button");
        
        Button clearFiltersBtn = new Button("Clear Filters");
        clearFiltersBtn.getStyleClass().add("secondary-button");
        
        Runnable loadFilterOptions = () -> {
            try {
                List<String> genres = BulkBookOperationService.getAllGenres();
                genreCombo.setItems(FXCollections.observableArrayList(genres));
                
                List<String> authors = BulkBookOperationService.getAllAuthors();
                authorCombo.setItems(FXCollections.observableArrayList(authors));
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Filter Load Failed", ex.getMessage());
            }
        };
        loadFilterOptions.run();
        
        applyFiltersBtn.setOnAction(e -> {
            try {
                String selectedGenre = genreCombo.getValue();
                String selectedAuthor = authorCombo.getValue();
                String selectedStatus = statusCombo.getValue();
                
                String genreFilter = (selectedGenre != null && !selectedGenre.isEmpty()) ? selectedGenre : null;
                String authorFilter = (selectedAuthor != null && !selectedAuthor.isEmpty()) ? selectedAuthor : null;
                Availability statusFilter = null;
                if (selectedStatus != null && !selectedStatus.equals("All Statuses")) {
                    try {
                        statusFilter = Availability.valueOf(selectedStatus);
                    } catch (IllegalArgumentException ignored) {}
                }
                
                List<Book> filteredBooks = BulkBookOperationService.filterBooks(genreFilter, authorFilter, statusFilter);
                items.clear();
                for (Book book : filteredBooks) {
                    items.add(new Row(book));
                }
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Filter Failed", ex.getMessage());
            }
        });
        
        clearFiltersBtn.setOnAction(e -> {
            genreCombo.setValue(null);
            authorCombo.setValue(null);
            statusCombo.setValue(null);
            refresh.run();
        });
        
        filterControls.getChildren().addAll(
            new Label("Genre:"), genreCombo,
            new Label("Author:"), authorCombo,
            new Label("Status:"), statusCombo,
            applyFiltersBtn, clearFiltersBtn
        );
        
        filterBox.getChildren().addAll(filterLabel, filterControls);
        return filterBox;
    }

    /**
     * Shows bulk edit dialog for multiple selected books.
     */
    private static void showBulkEditDialog(TableView<Row> table, User librarian, Runnable refresh) {
        List<Row> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Select at least one book to bulk edit.");
            return;
        }
        
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Bulk Edit Books (" + selected.size() + " selected)");
        
        ButtonType applyButtonType = new ButtonType("Apply Changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyButtonType, ButtonType.CANCEL);
        
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.setItems(FXCollections.observableArrayList("No Change", "AVAILABLE", "BORROWED"));
        statusCombo.setValue("No Change");
        statusCombo.setPrefWidth(150);
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(
            new Label("Update Availability Status for " + selected.size() + " books:"),
            new HBox(5, new Label("New Status:"), statusCombo)
        );
        dialog.getDialogPane().setContent(content);
        
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == applyButtonType) {
            try {
                String selectedStatus = statusCombo.getValue();
                if (selectedStatus != null && !selectedStatus.equals("No Change")) {
                    List<Long> bookIds = new ArrayList<>();
                    for (Row row : selected) {
                        bookIds.add(row.getId());
                    }
                    Availability newStatus = Availability.valueOf(selectedStatus);
                    BulkBookOperationService.bulkUpdateAvailability(bookIds, newStatus, librarian);
                    showAlert(Alert.AlertType.INFORMATION, "Success", 
                        "Updated " + selected.size() + " books. Changes logged in version history.");
                    refresh.run();
                }
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Bulk Edit Failed", ex.getMessage());
            }
        }
    }

    /**
     * Shows bulk delete confirmation dialog.
     */
    private static void showBulkDeleteDialog(TableView<Row> table, User librarian, Runnable refresh) {
        List<Row> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Select at least one book to delete.");
            return;
        }
        
        StringBuilder bookList = new StringBuilder();
        for (Row row : selected) {
            bookList.append("• ").append(row.getTitle()).append("\n");
        }
        
        if (!confirm("Confirm Bulk Delete", 
            "Delete " + selected.size() + " books?\n\n" + bookList.toString() + 
            "\nActive borrowers will be notified and loans closed.")) {
            return;
        }
        
        try {
            List<Long> bookIds = new ArrayList<>();
            for (Row row : selected) {
                bookIds.add(row.getId());
            }
            BulkBookOperationService.bulkDeleteBooks(bookIds, librarian);
            showAlert(Alert.AlertType.INFORMATION, "Success", 
                "Deleted " + selected.size() + " books. Changes logged in version history.");
            refresh.run();
        } catch (SQLException ex) {
            showAlert(Alert.AlertType.ERROR, "Bulk Delete Failed", ex.getMessage());
        }
    }

    /**
     * Shows version history dialog for a book.
     */
    private static void showVersionHistoryDialog(long bookId) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Version History");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        
        ListView<String> historyList = new ListView<>();
        try {
            List<BookChangeLog> changes = BulkBookOperationService.getBookChangeHistory(bookId);
            List<String> displayItems = new ArrayList<>();
            
            if (changes.isEmpty()) {
                displayItems.add("No changes recorded for this book.");
            } else {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                for (BookChangeLog log : changes) {
                    try {
                        String dateStr = log.getChangeDate() != null 
                            ? log.getChangeDate().format(formatter) 
                            : "N/A";
                        String item = String.format(
                            "[%s] %s by %s\nField: %s | Old: %s | New: %s",
                            dateStr,
                            log.getChangeType(),
                            log.getLibrarianName(),
                            log.getFieldName() != null ? log.getFieldName() : "N/A",
                            log.getOldValue() != null ? log.getOldValue() : "N/A",
                            log.getNewValue() != null ? log.getNewValue() : "N/A"
                        );
                        if (log.getDescription() != null && !log.getDescription().isEmpty()) {
                            item += "\nNote: " + log.getDescription();
                        }
                        displayItems.add(item);
                    } catch (Exception e) {
                        displayItems.add("Error parsing change log entry: " + e.getMessage());
                    }
                }
            }
            
            historyList.setItems(FXCollections.observableArrayList(displayItems));
            historyList.setCellFactory(list -> new ListCell<String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setPrefHeight(60);
                    } else {
                        setText(item);
                        setWrapText(true);
                        setPrefHeight(80);
                    }
                }
            });
            
        } catch (SQLException ex) {
            showAlert(Alert.AlertType.ERROR, "Load Failed", ex.getMessage());
            ex.printStackTrace();
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Error", "Unexpected error: " + ex.getMessage());
            ex.printStackTrace();
        }
        
        VBox content = new VBox(historyList);
        content.setPadding(new Insets(10));
        VBox.setVgrow(historyList, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(600, 500);
        
        dialog.showAndWait();
    }

    private static Optional<BookFormData> showBookFormDialog(Book existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Published Book" : "Edit Published Book");
        final BookFormData[] formHolder = new BookFormData[1];

        ButtonType saveButtonType = new ButtonType(existing == null ? "Add Book" : "Save Changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField titleField = new TextField(existing == null ? "" : trimToEmpty(existing.getTitle()));
        TextField authorNamesField = new TextField(existing == null ? "" : trimToEmpty(existing.getAuthorFullNameSnapshot()));
        TextField genreField = new TextField(existing == null ? "" : trimToEmpty(existing.getGenre()));
        TextArea descriptionArea = new TextArea(existing == null ? "" : trimToEmpty(existing.getSummary()));
        descriptionArea.setPrefRowCount(6);
        descriptionArea.setWrapText(true);

        TextField bookFileField = new TextField(existing == null ? "" : trimToEmpty(existing.getFilePath()));
        Button browseBookBtn = new Button("Browse");

        TextField coverFileField = new TextField(existing == null ? "" : trimToEmpty(existing.getCoverImagePath()));
        Button browseCoverBtn = new Button("Browse");

        Button generateDescBtn = new Button("Generate Description");
        Label generationStatusLabel = new Label("Status: Draft");
        generationStatusLabel.setStyle("-fx-text-fill: #7f8c8d;");

        browseBookBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Book File");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Supported Files", "*.pdf", "*.txt", "*.doc", "*.docx")
            );
            File selected = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (selected != null) {
                bookFileField.setText(selected.getAbsolutePath());
            }
        });

        browseCoverBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Cover Image");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.jpeg", "*.png")
            );
            File selected = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (selected != null) {
                coverFileField.setText(selected.getAbsolutePath());
            }
        });

        generateDescBtn.setOnAction(e -> {
            File candidate = toExistingFile(bookFileField.getText());
            if (candidate == null) {
                showAlert(Alert.AlertType.WARNING, "Missing File", "Select a valid book file before generating description.");
                return;
            }
            if (!BookPreviewUtil.isSupportedPreviewType(candidate.getAbsolutePath())) {
                showAlert(Alert.AlertType.WARNING, "Unsupported File", "Description generation supports PDF, TXT, DOC, and DOCX files.");
                return;
            }

            generateDescBtn.setDisable(true);
            String originalText = generateDescBtn.getText();
            generateDescBtn.setText("Generating...");
            generationStatusLabel.setText("Status: Generating...");
            generationStatusLabel.setStyle("-fx-text-fill: #2980b9;");

            Task<BookSummaryService.SummaryResult> task = new Task<>() {
                @Override
                protected BookSummaryService.SummaryResult call() {
                    BookSummaryService service = new BookSummaryService();
                    return service.generateSummaryFromBookFile(candidate.getAbsolutePath());
                }
            };

            task.setOnSucceeded(ev -> {
                BookSummaryService.SummaryResult result = task.getValue();
                if (result.success()) {
                    descriptionArea.setText(result.summary());
                    generationStatusLabel.setText("Status: Generated");
                    generationStatusLabel.setStyle("-fx-text-fill: #27ae60;");
                    showAlert(Alert.AlertType.INFORMATION, "Description Generated", result.message());
                } else {
                    generationStatusLabel.setText("Status: Draft");
                    generationStatusLabel.setStyle("-fx-text-fill: #7f8c8d;");
                    showAlert(Alert.AlertType.ERROR, "Generation Failed", result.message());
                }
                generateDescBtn.setText(originalText);
                generateDescBtn.setDisable(false);
            });

            task.setOnFailed(ev -> {
                generationStatusLabel.setText("Status: Draft");
                generationStatusLabel.setStyle("-fx-text-fill: #7f8c8d;");
                generateDescBtn.setText(originalText);
                generateDescBtn.setDisable(false);
                Throwable ex = task.getException();
                showAlert(Alert.AlertType.ERROR, "Generation Failed",
                        ex == null ? "Unexpected error during description generation." : ex.getMessage());
            });

            Thread worker = new Thread(task, "librarian-description-generation");
            worker.setDaemon(true);
            worker.start();
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        grid.add(new Label("Title *"), 0, 0);
        grid.add(titleField, 1, 0);

        grid.add(new Label("Author Names *"), 0, 1);
        grid.add(authorNamesField, 1, 1);

        Label authorHint = new Label("Use comma-separated names. First name must match a registered AUTHOR account.");
        authorHint.setWrapText(true);
        authorHint.setStyle("-fx-text-fill: #666; -fx-font-size: 11;");
        grid.add(authorHint, 1, 2);

        grid.add(new Label("Genre *"), 0, 3);
        grid.add(genreField, 1, 3);

        grid.add(new Label("Description *"), 0, 4);
        grid.add(descriptionArea, 1, 4);

        HBox generationBar = new HBox(10, generateDescBtn, generationStatusLabel);
        generationBar.setAlignment(Pos.CENTER_LEFT);
        grid.add(new Label("LLM Tools"), 0, 5);
        grid.add(generationBar, 1, 5);

        HBox bookPicker = new HBox(8, bookFileField, browseBookBtn);
        HBox.setHgrow(bookFileField, Priority.ALWAYS);
        grid.add(new Label("Book File *"), 0, 6);
        grid.add(bookPicker, 1, 6);

        HBox coverPicker = new HBox(8, coverFileField, browseCoverBtn);
        HBox.setHgrow(coverFileField, Priority.ALWAYS);
        grid.add(new Label("Cover Upload (optional)"), 0, 7);
        grid.add(coverPicker, 1, 7);

        Region spacer = new Region();
        spacer.setMinHeight(4);
        grid.add(spacer, 1, 8);

        dialog.getDialogPane().setContent(grid);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            List<String> errors = new ArrayList<>();

            String title = trimToEmpty(titleField.getText());
            String authorNames = normalizeAuthorNames(authorNamesField.getText());
            String genre = trimToEmpty(genreField.getText());
            String description = trimToEmpty(descriptionArea.getText());
            File selectedBook = toExistingFile(bookFileField.getText());
            File selectedCover = toExistingFile(coverFileField.getText());

            if (title.isEmpty()) errors.add("Title is required.");
            if (authorNames.isEmpty()) errors.add("Author Names are required.");
            if (genre.isEmpty()) errors.add("Genre is required.");
            if (description.isEmpty()) errors.add("Description is required.");
            if (selectedBook == null) {
                errors.add("A valid book file is required.");
            } else {
                validateBookFile(selectedBook, errors);
            }
            if (selectedCover != null) {
                validateCoverFile(selectedCover, errors);
            }

            long primaryAuthorId = -1;
            if (!authorNames.isEmpty()) {
                try {
                    primaryAuthorId = resolvePrimaryAuthorUserId(authorNames);
                } catch (SQLException ex) {
                    errors.add("Could not validate author list: " + ex.getMessage());
                } catch (IllegalArgumentException ex) {
                    errors.add(ex.getMessage());
                }
            }

            if (!errors.isEmpty()) {
                event.consume();
                showAlert(Alert.AlertType.WARNING, "Validation Failed", String.join("\n", errors));
                return;
            }

                formHolder[0] = new BookFormData(
                    title,
                    primaryAuthorId,
                    authorNames,
                    genre,
                    description,
                    selectedBook,
                    selectedCover
                );
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveButtonType) {
            return Optional.empty();
        }

        if (formHolder[0] != null) {
            return Optional.of(formHolder[0]);
        }
        return Optional.empty();
    }

    private static long resolvePrimaryAuthorUserId(String authorNames) throws SQLException {
        String[] allNames = authorNames.split(",");
        String primaryName = "";
        for (String candidate : allNames) {
            if (candidate != null && !candidate.trim().isEmpty()) {
                primaryName = candidate.trim();
                break;
            }
        }
        if (primaryName.isEmpty()) {
            throw new IllegalArgumentException("Author Names must contain at least one name.");
        }

        List<User> authors = UserDao.findAllByRole(Role.AUTHOR);
        for (User author : authors) {
            if (primaryName.equalsIgnoreCase(trimToEmpty(author.getFullName()))) {
                return author.getId();
            }
        }
        throw new IllegalArgumentException("Primary author '" + primaryName + "' is not a registered author account.");
    }

    private static String normalizeAuthorNames(String raw) {
        if (raw == null) {
            return "";
        }
        String[] tokens = raw.split(",");
        List<String> normalized = new ArrayList<>();
        for (String token : tokens) {
            String name = token == null ? "" : token.trim();
            if (!name.isEmpty()) {
                normalized.add(name);
            }
        }
        return String.join(", ", normalized);
    }

    private static File toExistingFile(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        File file = new File(path.trim());
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            return null;
        }
        return file;
    }

    private static void validateBookFile(File file, List<String> errors) {
        String ext = extensionOf(file.getName());
        if (!List.of("pdf", "txt", "doc", "docx").contains(ext)) {
            errors.add("Book file must be PDF, TXT, DOC, or DOCX.");
        }
        if (file.length() == 0) {
            errors.add("Book file cannot be empty.");
        }
        if (file.length() > MAX_BOOK_FILE_SIZE) {
            errors.add("Book file must be at most 10MB.");
        }
    }

    private static void validateCoverFile(File file, List<String> errors) {
        String ext = extensionOf(file.getName());
        if (!List.of("jpg", "jpeg", "png").contains(ext)) {
            errors.add("Cover image must be JPG, JPEG, or PNG.");
        }
        if (file.length() == 0) {
            errors.add("Cover image cannot be empty.");
        }
        if (file.length() > MAX_COVER_FILE_SIZE) {
            errors.add("Cover image must be at most 2MB.");
        }
    }

    private static String persistUploadedBookFile(File source) throws IOException {
        Files.createDirectories(Paths.get(PUBLISHED_UPLOAD_DIR));
        String safeName = sanitizeFilename(source.getName());
        String uniqueName = System.currentTimeMillis() + "_" + safeName;
        Path destination = Paths.get(PUBLISHED_UPLOAD_DIR, uniqueName);
        Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
        return destination.toAbsolutePath().toString();
    }

    private static String persistCoverFile(File source) throws IOException {
        Files.createDirectories(Paths.get(PUBLISHED_COVER_DIR));
        String ext = extensionOf(source.getName());
        String uniqueName = System.currentTimeMillis() + "_" + Math.abs(Objects.hash(source.getName(), source.length())) + "." + ext;
        Path destination = Paths.get(PUBLISHED_COVER_DIR, uniqueName);
        Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
        return destination.toAbsolutePath().toString();
    }

    private static String extensionOf(String name) {
        if (name == null) {
            return "";
        }
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return "";
        }
        return name.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private static String sanitizeFilename(String filename) {
        String base = filename == null ? "upload.bin" : filename;
        return base.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String trimToEmpty(String input) {
        return input == null ? "" : input.trim();
    }

    private static String filePathOrEmpty(File file) {
        return file == null ? "" : trimToEmpty(file.getAbsolutePath());
    }

    private static void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }
}
