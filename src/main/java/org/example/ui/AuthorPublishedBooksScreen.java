package org.example.ui;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import org.example.app.Navigator;
import org.example.db.BookDao;
import org.example.db.BorrowDao;
import org.example.db.PendingDao;
import org.example.domain.Book;
import org.example.domain.PendingBook;
import org.example.domain.User;
import org.example.service.BulkBookOperationService;
import org.example.util.BookPreviewUtil;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Author view of pending submissions and published catalog with edit/delete rules.
 */
public final class AuthorPublishedBooksScreen {

    private AuthorPublishedBooksScreen() {}

    @FunctionalInterface
    interface BorrowCounter {
        int count(long bookId) throws SQLException;
    }

    static final class DeletionDecision {
        private final BookRow row;
        private final boolean deletable;
        private final String reason;

        DeletionDecision(BookRow row, boolean deletable, String reason) {
            this.row = row;
            this.deletable = deletable;
            this.reason = reason;
        }

        public BookRow row() { return row; }
        public boolean deletable() { return deletable; }
        public String reason() { return reason; }
    }

    static final class BulkDeletePlan {
        private final List<DeletionDecision> deletable;
        private final List<DeletionDecision> blocked;
        private final LinkedHashMap<String, Integer> blockedCounts;

        BulkDeletePlan(List<DeletionDecision> deletable, List<DeletionDecision> blocked,
                       LinkedHashMap<String, Integer> blockedCounts) {
            this.deletable = deletable;
            this.blocked = blocked;
            this.blockedCounts = blockedCounts;
        }

        public List<DeletionDecision> deletable() { return deletable; }
        public List<DeletionDecision> blocked() { return blocked; }
        public LinkedHashMap<String, Integer> blockedCounts() { return blockedCounts; }
    }

    public static class PendingRow {
        private final long id;
        private final String title;
        private final String genre;
        private final String status;
        private final String coverPath;

        PendingRow(PendingBook p) {
            this.id = p.getId();
            this.title = p.getTitle();
            this.genre = p.getGenre();
            this.status = p.getStatus();
            this.coverPath = p.getCoverPath();
        }

        public long getId() { return id; }
        public String getTitle() { return title; }
        public String getGenre() { return genre; }
        public String getStatus() { return status; }
        public String getCoverPath() { return coverPath; }
    }

    public static class BookRow {
        private final long id; // for published book rows, real book id; for pending rows this will be 0
        private final long pendingId; // for pending rows, the pending_books.id; 0 for published rows
        private final String title;
        private final String genre;
        private final long authorUserId;
        private final String status; // APPROVED or REJECTED
        private final boolean isPending;
        private final String coverPath;
        private final boolean hiddenFromCatalog;
        private final BooleanProperty bulkDeleteSelected = new SimpleBooleanProperty(false);

        BookRow(Book b) {
            this(b, false);
        }

        BookRow(Book b, boolean hiddenFromCatalog) {
            this.id = b.getId();
            this.pendingId = 0;
            this.title = b.getTitle();
            this.genre = b.getGenre();
            this.authorUserId = b.getAuthorUserId();
            this.status = hiddenFromCatalog ? "READY_TO_DELETE" : "APPROVED";
            this.isPending = false;
            this.coverPath = b.getCoverImagePath();
            this.hiddenFromCatalog = hiddenFromCatalog;
        }

        BookRow(PendingBook p) {
            this.id = 0;
            this.pendingId = p.getId();
            this.title = p.getTitle();
            this.genre = p.getGenre();
            this.authorUserId = p.getAuthorUserId();
            this.status = p.getStatus();
            this.isPending = true;
            this.coverPath = p.getCoverPath();
            this.hiddenFromCatalog = false;
        }

        public long getId() { return id; }
        public long getPendingId() { return pendingId; }
        public String getTitle() { return title; }
        public String getGenre() { return genre; }
        public long getAuthorUserId() { return authorUserId; }
        public String getStatus() { return status; }
        public boolean isPending() { return isPending; }
        public String getCoverPath() { return coverPath; }
        public boolean isHiddenFromCatalog() { return hiddenFromCatalog; }
        public BooleanProperty bulkDeleteSelectedProperty() { return bulkDeleteSelected; }
        public boolean isBulkDeleteSelected() { return bulkDeleteSelected.get(); }
        public void setBulkDeleteSelected(boolean selected) { bulkDeleteSelected.set(selected); }
    }

    public static Scene create(Navigator navigator, User user) {
        Label head = new Label("My submissions & published books");
        head.getStyleClass().add("screen-title");

        // Unified book list (shows both published and submissions)
        Label lb = new Label("My books & submissions");
        TableView<BookRow> bookTable = new TableView<>();
        var bItems = FXCollections.<BookRow>observableArrayList();
        TableColumn<BookRow, Boolean> bc0 = new TableColumn<>("Delete?");
        bc0.setCellValueFactory(cd -> cd.getValue().bulkDeleteSelectedProperty());
        bc0.setCellFactory(CheckBoxTableCell.forTableColumn(bc0));
        bc0.setEditable(true);
        bc0.setPrefWidth(80);
        TableColumn<BookRow, BookRow> bc1 = new TableColumn<>("Title");
        bc1.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        bc1.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(BookRow r, boolean empty) {
                super.updateItem(r, empty);
                if (empty || r == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Image img = null;
                    try {
                        if (r.getCoverPath() != null && !r.getCoverPath().isBlank()) {
                            img = new Image("file:" + r.getCoverPath(), 60, 90, true, true);
                        }
                    } catch (Exception ignored) {}
                    if (img == null || img.isError()) {
                        var u = AuthorPublishedBooksScreen.class.getResource("/images/default-cover.png");
                        if (u != null) img = new Image(u.toExternalForm(), 60, 90, true, true);
                    }
                    ImageView iv = new ImageView(img);
                    Label t = new Label(r.getTitle());
                    Label g = new Label(r.getGenre());
                    VBox v = new VBox(2, t, g);
                    HBox h = new HBox(8, iv, v);
                    setText(null);
                    setGraphic(h);
                }
            }
        });
        TableColumn<BookRow, String> bc2 = new TableColumn<>("Genre");
        bc2.setCellValueFactory(new PropertyValueFactory<>("genre"));
        TableColumn<BookRow, String> bc3 = new TableColumn<>("Status");
        bc3.setCellValueFactory(new PropertyValueFactory<>("status"));
        bookTable.getColumns().addAll(List.of(bc0, bc1, bc2, bc3));
        bookTable.setItems(bItems);
        bookTable.setEditable(true);
        // Make the table taller so the submission list can display more rows without scrolling
        bookTable.setPrefHeight(Math.max(400, (int)Navigator.getPreferredHeight() - 240));

        // Pending filters: status and search
        TextField pendingSearchField = new TextField();
        pendingSearchField.setPromptText("Search pending by title...");
        pendingSearchField.setMaxWidth(300);

        ComboBox<String> pendingStatus = new ComboBox<>(FXCollections.observableArrayList("ALL", "PENDING", "APPROVED", "REJECTED"));
        pendingStatus.getSelectionModel().selectFirst();

        HBox pendingFilters = new HBox(8, pendingSearchField, pendingStatus);

        Runnable refresh = () -> {
            bItems.clear();
            try {
                String status = pendingStatus.getSelectionModel().getSelectedItem();
                if (status != null && "ALL".equals(status)) status = null;
                String search = pendingSearchField.getText();
                if (search != null) search = search.trim().toLowerCase();

                // Load pending/submission rows based on search & status
                List<PendingBook> pendings;
                if (status == null) {
                    // 'ALL' selected: include all pending_books rows (PENDING/APPROVED/REJECTED)
                    if (search == null || search.isEmpty()) {
                        pendings = PendingDao.findAll();
                    } else {
                        pendings = PendingDao.searchAndFilter(search, null);
                    }
                } else {
                    pendings = PendingDao.searchAndFilter(search == null || search.isEmpty() ? null : search, status);
                }
                for (PendingBook p : pendings) {
                    if (p.getAuthorUserId() == user.getId()) {
                        // Approved submissions are represented by real rows in books.
                        // Hiding approved pending rows avoids duplicate/stale entries.
                        if ("APPROVED".equalsIgnoreCase(p.getStatus())) {
                            continue;
                        }
                        bItems.add(new BookRow(p));
                    }
                }

                // Load published books by author, apply simple client-side search filtering
                var hiddenBookIds = BookDao.findHiddenBookIdsByAuthor(user.getId());
                for (Book b : BookDao.findByAuthorUserId(user.getId())) {
                    boolean hidden = hiddenBookIds.contains(b.getId());
                    if (hidden) {
                        // Removed/deleted books must not be shown anywhere except borrow record screens.
                        continue;
                    }
                    if (search == null || search.isEmpty() || b.getTitle().toLowerCase().contains(search) || (b.getGenre() != null && b.getGenre().toLowerCase().contains(search))) {
                        // Published books correspond to APPROVED status; only include when status filter allows it
                        if (status == null || "APPROVED".equalsIgnoreCase(status)) {
                            bItems.add(new BookRow(b, hidden));
                        }
                    }
                }
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not load data.").showAndWait();
            }
        };
        refresh.run();
        pendingStatus.setOnAction(e -> refresh.run());
        pendingSearchField.textProperty().addListener((a,b,c) -> refresh.run());






        // pbar removed - unified controls below

        Button editBookBtn = new Button("Edit");
        editBookBtn.getStyleClass().add("secondary-button");
        editBookBtn.setPrefWidth(140);
        editBookBtn.setOnAction(e -> {
            BookRow r = bookTable.getSelectionModel().getSelectedItem();
            if (r == null) return;
            if (r.getAuthorUserId() != user.getId()) {
                new Alert(Alert.AlertType.WARNING, "You can only edit your own books or submissions.").showAndWait();
                return;
            }

            try {
                if (r.isPending()) {
                    // pending submission (could be PENDING or REJECTED)
                    Optional<PendingBook> opt = PendingDao.findById(r.getPendingId());
                    if (opt.isEmpty()) return;
                    PendingBook p = opt.get();
                    if ("APPROVED".equalsIgnoreCase(p.getStatus())) {
                        new Alert(Alert.AlertType.INFORMATION, "This approved submission is already in the catalog. Edit the published book row instead.").showAndWait();
                        return;
                    }

                    TextField tTitle = new TextField(p.getTitle());
                    TextField tGenre = new TextField(p.getGenre());
                    TextArea tSum = new TextArea(p.getSummary());
                    tSum.setPrefRowCount(5);
                    TextField tCover = new TextField(p.getCoverPath() == null ? "" : p.getCoverPath());
                    Button browseCover = new Button("Browse");
                    GridPane g = new GridPane();
                    g.setHgap(8);
                    g.setVgap(8);
                    g.addRow(0, new Label("Title"), tTitle);
                    g.addRow(1, new Label("Genre"), tGenre);
                    g.addRow(2, new Label("Summary"), tSum);
                    g.addRow(3, new Label("Cover"), new HBox(8, tCover, browseCover));
                    browseCover.setOnAction(ev -> {
                        FileChooser fc = new FileChooser();
                        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
                        java.io.File f = fc.showOpenDialog(null);
                        if (f != null) tCover.setText(f.getAbsolutePath());
                    });
                    Alert form = new Alert(Alert.AlertType.CONFIRMATION);
                    form.setTitle("Edit submission");
                    form.getDialogPane().setContent(g);
                    form.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                        String newTitle = tTitle.getText().trim();
                        String newGenre = tGenre.getText().trim();
                        if (newTitle.isEmpty() || newGenre.isEmpty()) {
                            new Alert(Alert.AlertType.WARNING, "Title and Genre must not be empty.").showAndWait();
                            return;
                        }
                        try {
                            // Check for changes before updating
                            String origTitle = p.getTitle() == null ? "" : p.getTitle().trim();
                            String origGenre = p.getGenre() == null ? "" : p.getGenre().trim();
                            String origSummary = p.getSummary() == null ? "" : p.getSummary().trim();
                            String origCover = p.getCoverPath() == null ? null : p.getCoverPath().trim();
                            String newSummary = tSum.getText().trim();
                            String newCover = tCover.getText().trim().isEmpty() ? null : tCover.getText().trim();
                            boolean changed = !newTitle.equals(origTitle)
                                    || !newGenre.equals(origGenre)
                                    || !newSummary.equals(origSummary)
                                    || ((origCover == null && newCover != null) || (origCover != null && !origCover.equals(newCover)));
                            if (!changed) {
                                new Alert(Alert.AlertType.INFORMATION, "No changes detected.").showAndWait();
                                return;
                            }

                            if ("PENDING".equalsIgnoreCase(p.getStatus())) {
                                PendingDao.updatePendingSubmission(
                                        p.getId(), user.getId(),
                                        newTitle, newGenre, newSummary,
                                        p.getFileName(), p.getFilePath(), p.getFileSize(), p.getFileType(),
                                        newCover
                                );
                            } else {
                                // REJECTED - create a fresh pending submission based on edited fields
                                PendingBook np = new PendingBook(
                                        newTitle,
                                        user.getId(),
                                        user.getFullName(),
                                        newGenre,
                                        newSummary,
                                        p.getFileName() != null ? p.getFileName() : "",
                                        p.getFilePath() != null ? p.getFilePath() : "",
                                        p.getFileSize(),
                                        p.getFileType() != null ? p.getFileType() : "pdf"
                                );
                                if (newCover != null) np.setCoverPath(newCover);
                                long newId = PendingDao.insert(np);
                                try {
                                    org.example.service.NotificationService.notifyLibrariansNewSubmission(
                                            newId, np.getTitle(), user.getFullName());
                                } catch (SQLException ex) {
                                    java.util.logging.Logger.getLogger(AuthorPublishedBooksScreen.class.getName())
                                            .log(java.util.logging.Level.WARNING,
                                                    "Could not notify librarians of re-submission", ex);
                                }
                                // optionally remove old rejected row
                                try {
                                    PendingDao.deleteByIdForAuthor(p.getId(), user.getId());
                                } catch (SQLException ex) {
                                    java.util.logging.Logger.getLogger(AuthorPublishedBooksScreen.class.getName())
                                            .log(java.util.logging.Level.WARNING,
                                                    "Could not delete old rejected submission", ex);
                                }
                            }
                            refresh.run();
                        } catch (SQLException ex) {
                            new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                        }
                    });

                } else {
                    // published book — only editable when not borrowed
                    if (BorrowDao.countActiveBorrowsForBook(r.getId()) > 0) {
                        new Alert(Alert.AlertType.WARNING,
                                "You cannot edit published book details while it is borrowed. "
                                        + "Try again after all students/staff have returned it.").showAndWait();
                        return;
                    }
                    Optional<Book> opt = BookDao.findById(r.getId());
                    if (opt.isEmpty()) return;
                    Book bk = opt.get();

                    TextField tTitle = new TextField(bk.getTitle());
                    TextField tGenre = new TextField(bk.getGenre());
                    TextArea tSum = new TextArea(bk.getSummary());
                    tSum.setPrefRowCount(5);
                    TextField tCover = new TextField(bk.getCoverImagePath() == null ? "" : bk.getCoverImagePath());
                    Button browseCover = new Button("Browse");
                    GridPane g = new GridPane();
                    g.setHgap(8);
                    g.setVgap(8);
                    g.addRow(0, new Label("Title"), tTitle);
                    g.addRow(1, new Label("Genre"), tGenre);
                    g.addRow(2, new Label("Summary"), tSum);
                    g.addRow(3, new Label("Cover"), new HBox(8, tCover, browseCover));
                    browseCover.setOnAction(ev -> {
                        FileChooser fc = new FileChooser();
                        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
                        java.io.File f = fc.showOpenDialog(null);
                        if (f != null) tCover.setText(f.getAbsolutePath());
                    });
                    Alert form = new Alert(Alert.AlertType.CONFIRMATION);
                    form.setTitle("Edit published book");
                    form.getDialogPane().setContent(g);
                    form.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                        String newTitle = tTitle.getText().trim();
                        String newGenre = tGenre.getText().trim();
                        String newSummary = tSum.getText().trim();
                        String newCover = tCover.getText().trim().isEmpty() ? null : tCover.getText().trim();
                        if (newTitle.isEmpty() || newGenre.isEmpty()) {
                            new Alert(Alert.AlertType.WARNING, "Title and Genre must not be empty.").showAndWait();
                            return;
                        }
                        try {
                            String origTitle = bk.getTitle() == null ? "" : bk.getTitle().trim();
                            String origGenre = bk.getGenre() == null ? "" : bk.getGenre().trim();
                            String origSummary = bk.getSummary() == null ? "" : bk.getSummary().trim();
                            String origCover = bk.getCoverImagePath() == null ? null : bk.getCoverImagePath().trim();
                            boolean changed = !newTitle.equals(origTitle)
                                    || !newGenre.equals(origGenre)
                                    || !newSummary.equals(origSummary)
                                    || ((origCover == null && newCover != null) || (origCover != null && !origCover.equals(newCover)));
                            if (!changed) {
                                new Alert(Alert.AlertType.INFORMATION, "No changes detected.").showAndWait();
                                return;
                            }
                            BookDao.updatePublishedFieldsIncludingLinked(
                                    bk.getId(),
                                    newTitle,
                                    newGenre,
                                    newSummary,
                                    bk.getFilePath(),
                                    newCover
                            );
                            
                            // Log all changes to version history
                            try {
                                BulkBookOperationService.updateBookWithHistory(
                                        bk.getId(),
                                        newTitle,
                                        newGenre,
                                        newSummary,
                                        user
                                );
                            } catch (SQLException ignored) {
                                // Non-fatal: keep edit successful even if change logging fails
                            }
                            
                            new Alert(Alert.AlertType.INFORMATION, "Book details updated successfully.").showAndWait();
                            refresh.run();
                        } catch (SQLException ex) {
                            new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                        }
                    });
                }
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
            }
        });

        Button delBookBtn = new Button("Delete");
        delBookBtn.getStyleClass().add("secondary-button");
        delBookBtn.setPrefWidth(140);
        delBookBtn.setOnAction(e -> {
            List<BookRow> checkedRows = bItems.stream()
                    .filter(BookRow::isBulkDeleteSelected)
                    .toList();
            List<BookRow> deleteTargets;
            if (!checkedRows.isEmpty()) {
                deleteTargets = checkedRows;
            } else {
                BookRow selectedRow = bookTable.getSelectionModel().getSelectedItem();
                if (selectedRow == null) {
                    new Alert(Alert.AlertType.WARNING,
                            "Select a row or tick checkbox(es) before deleting.").showAndWait();
                    return;
                }
                deleteTargets = List.of(selectedRow);
            }
            try {
                BulkDeletePlan plan = buildBulkDeletePlan(deleteTargets, user.getId(), BorrowDao::countActiveBorrowsForBook);
                if (plan.deletable().isEmpty()) {
                    new Alert(Alert.AlertType.INFORMATION, buildBulkDeleteSummary(plan)).showAndWait();
                    return;
                }
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Confirm Delete");
                confirm.setHeaderText("Delete " + plan.deletable().size() + " item(s)?");
                confirm.setContentText(buildBulkDeleteSummary(plan));
                confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                    int success = 0;
                    List<String> failures = new ArrayList<>();
                    for (DeletionDecision d : plan.deletable()) {
                        try {
                            deleteRowForAuthor(d.row(), user.getId());
                            success++;
                        } catch (SQLException ex) {
                            failures.add(d.row().getTitle() + " (" + ex.getMessage() + ")");
                        }
                    }
                    Alert result = new Alert(Alert.AlertType.INFORMATION);
                    result.setTitle("Delete Result");
                    result.setHeaderText("Deleted " + success + " of " + plan.deletable().size() + " eligible item(s)");
                    result.setContentText(buildBulkDeleteResultSummary(plan, success, failures));
                    result.showAndWait();
                    bItems.forEach(row -> row.setBulkDeleteSelected(false));
                    refresh.run();
                });
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
            }
        });

        Button readBtn = new Button("Read");
        readBtn.getStyleClass().add("secondary-button");
        readBtn.setPrefWidth(140);
        readBtn.setOnAction(e -> {
            BookRow selected = bookTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                new Alert(Alert.AlertType.WARNING, "Please select a row to read.").showAndWait();
                return;
            }
            try {
                if (selected.isPending()) {
                    Optional<PendingBook> pendingOpt = PendingDao.findById(selected.getPendingId());
                    if (pendingOpt.isEmpty()) {
                        new Alert(Alert.AlertType.ERROR, "Submission not found.").showAndWait();
                        return;
                    }
                    PendingBook pending = pendingOpt.get();
                    openFullFile(pending.getFilePath(), pending.getTitle());
                } else {
                    Optional<Book> bookOpt = BookDao.findById(selected.getId());
                    if (bookOpt.isEmpty()) {
                        new Alert(Alert.AlertType.ERROR, "Book not found.").showAndWait();
                        return;
                    }
                    Book book = bookOpt.get();
                    openFullFile(book.getFilePath(), book.getTitle());
                }
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
            }
        });

        editBookBtn.disableProperty().bind(Bindings.size(bookTable.getSelectionModel().getSelectedItems()).isNotEqualTo(1));
        readBtn.disableProperty().bind(Bindings.size(bookTable.getSelectionModel().getSelectedItems()).isNotEqualTo(1));

        HBox bbar = new HBox(10, editBookBtn, delBookBtn, readBtn);
        bbar.setAlignment(Pos.CENTER_LEFT);
        bbar.setPadding(new Insets(16, 0, 0, 0));

        // bottom navigation removed - navigation available via global menu

        HBox searchBox = pendingFilters; // reuse existing search/filter controls

        VBox contentRoot = new VBox(12, head, searchBox, lb, bookTable);
        contentRoot.setPadding(new Insets(16));

        ScrollPane scrollPane = new ScrollPane(contentRoot);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: white;");

        // Buttons stay fixed below the scrollable list so the list can grow taller
        VBox main = new VBox(12, scrollPane, bbar);
        main.setPadding(new Insets(0, 16, 16, 16));

        Scene scene = new Scene(main, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        var css = AuthorPublishedBooksScreen.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }

    public record ReadPreviewData(String content, String statusMessage) {}

    /**
     * Builds a short inline preview for the author's book file path, falling back to the stored summary.
     */
    public static ReadPreviewData buildReadPreviewData(String filePath, String fallbackSummary) {
        String fb = fallbackSummary == null ? "" : fallbackSummary;
        String preview = BookPreviewUtil.readTextPreview(filePath);
        if (preview != null && !preview.isBlank()) {
            return new ReadPreviewData(preview, "Showing extracted file preview.");
        }
        if (filePath != null && !filePath.isBlank()) {
            String lower = filePath.toLowerCase();
            if (lower.endsWith(".txt") || lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".doc")) {
                return new ReadPreviewData(fb, "Showing extracted file preview.");
            }
        }
        return new ReadPreviewData(fb, "Unsupported file format for inline preview.");
    }

    static BulkDeletePlan buildBulkDeletePlan(List<BookRow> selectedRows, long authorId, BorrowCounter borrowCounter)
            throws SQLException {
        List<DeletionDecision> deletable = new ArrayList<>();
        List<DeletionDecision> blocked = new ArrayList<>();
        LinkedHashMap<String, Integer> blockedCounts = new LinkedHashMap<>();
        for (BookRow row : selectedRows) {
            DeletionDecision decision = evaluateDeletionEligibility(row, authorId, borrowCounter);
            if (decision.deletable()) {
                deletable.add(decision);
            } else {
                blocked.add(decision);
                blockedCounts.merge(decision.reason(), 1, Integer::sum);
            }
        }
        return new BulkDeletePlan(deletable, blocked, blockedCounts);
    }

    static DeletionDecision evaluateDeletionEligibility(BookRow row, long authorId, BorrowCounter borrowCounter)
            throws SQLException {
        if (row == null) return new DeletionDecision(null, false, "Invalid row");
        if (row.getAuthorUserId() != authorId) {
            return new DeletionDecision(row, false, "Not owned by current author");
        }
        if (row.isPending()) {
            if (row.getPendingId() <= 0) {
                return new DeletionDecision(row, false, "Invalid pending submission reference");
            }
            return new DeletionDecision(row, true, "");
        }
        if (row.getId() <= 0) {
            return new DeletionDecision(row, false, "Invalid published book reference");
        }
        if (borrowCounter.count(row.getId()) > 0) {
            return new DeletionDecision(row, false, "Published book is currently borrowed");
        }
        return new DeletionDecision(row, true, "");
    }

    private static String buildBulkDeleteSummary(BulkDeletePlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("Selected: ").append(plan.deletable().size() + plan.blocked().size()).append('\n');
        sb.append("Eligible for deletion: ").append(plan.deletable().size()).append('\n');
        sb.append("Blocked: ").append(plan.blocked().size());
        if (!plan.blockedCounts().isEmpty()) {
            sb.append("\n\nBlocked reasons:");
            for (var e : plan.blockedCounts().entrySet()) {
                sb.append("\n- ").append(e.getKey()).append(": ").append(e.getValue());
            }
        }
        if (!plan.deletable().isEmpty()) {
            sb.append("\n\nSample deletable titles:");
            for (int i = 0; i < Math.min(5, plan.deletable().size()); i++) {
                sb.append("\n- ").append(plan.deletable().get(i).row().getTitle());
            }
        }
        return sb.toString();
    }

    private static String buildBulkDeleteResultSummary(BulkDeletePlan plan, int success, List<String> failures) {
        StringBuilder sb = new StringBuilder();
        sb.append("Success: ").append(success).append('\n');
        sb.append("Failed during execution: ").append(failures.size()).append('\n');
        sb.append("Skipped/blocked before execution: ").append(plan.blocked().size());
        if (!plan.blockedCounts().isEmpty()) {
            sb.append("\n\nSkipped reasons:");
            for (var e : plan.blockedCounts().entrySet()) {
                sb.append("\n- ").append(e.getKey()).append(": ").append(e.getValue());
            }
        }
        if (!failures.isEmpty()) {
            sb.append("\n\nExecution failures:");
            for (String f : failures) {
                sb.append("\n- ").append(f);
            }
        }
        return sb.toString();
    }

    private static void deleteRowForAuthor(BookRow row, long authorId) throws SQLException {
        if (row.isPending()) {
            PendingDao.deleteByIdForAuthor(row.getPendingId(), authorId);
            return;
        }
        try {
            PendingDao.deleteByOriginalBookId(row.getId());
        } catch (SQLException ignored) {}
        try {
            PendingDao.deleteUnlinkedApprovedOrRejectedForAuthorTitle(authorId, row.getTitle());
        } catch (SQLException ignored) {}
        BookDao.removeFromCatalogButKeepHistory(row.getId());
    }

    private static void openFullFile(String filePath, String title) {
        if (filePath == null || filePath.isBlank()) {
            new Alert(Alert.AlertType.INFORMATION,
                    "No file path is available for \"" + title + "\".").showAndWait();
            return;
        }
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            new Alert(Alert.AlertType.ERROR, "Book file not found on disk:\n" + filePath).showAndWait();
            return;
        }
        String lowerPath = filePath.toLowerCase();
        if (lowerPath.endsWith(".pdf")) {
            openPdfInPopup(file, title);
            return;
        }
        openTextBasedFileInPopup(filePath, title);
    }

    private static void openPdfInPopup(File file, String title) {
        int pageCount = BookPreviewUtil.getPdfPageCount(file.toPath());
        if (pageCount <= 0) {
            new Alert(Alert.AlertType.ERROR,
                    "Could not load PDF pages for in-app reading.\nPath: " + file.getAbsolutePath()).showAndWait();
            return;
        }
        List<Image> pages = BookPreviewUtil.readPdfPreviewImages(file.getAbsolutePath(), pageCount);
        if (pages.isEmpty()) {
            new Alert(Alert.AlertType.ERROR,
                    "PDF content could not be rendered in-app.\nPath: " + file.getAbsolutePath()).showAndWait();
            return;
        }

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Read — " + title);

        Label info = new Label("Showing full PDF content (" + pages.size() + " page(s)).");
        info.setWrapText(true);

        VBox pdfPagesBox = new VBox(12);
        pdfPagesBox.setPadding(new Insets(8));
        for (int i = 0; i < pages.size(); i++) {
            ImageView imageView = new ImageView(pages.get(i));
            imageView.setPreserveRatio(true);
            imageView.setFitWidth(900);
            Label pageLabel = new Label("Page " + (i + 1));
            pageLabel.setStyle("-fx-font-weight: bold;");
            pdfPagesBox.getChildren().addAll(pageLabel, imageView);
        }
        ScrollPane scrollPane = new ScrollPane(pdfPagesBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setOnAction(e -> stage.close());

        VBox root = new VBox(10, info, scrollPane, closeBtn);
        root.setPadding(new Insets(12));
        root.setPrefSize(980, 720);
        VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);

        Scene scene = new Scene(root, 980, 720);
        var css = AuthorPublishedBooksScreen.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);
        stage.showAndWait();
    }

    private static void openTextBasedFileInPopup(String filePath, String title) {
        String content = BookPreviewUtil.readTextContent(filePath);
        if (content == null || content.isBlank()) {
            new Alert(Alert.AlertType.ERROR,
                    "Could not read file content in-app.\nPath: " + filePath).showAndWait();
            return;
        }

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Read — " + title);

        Label info = new Label("Showing full extracted content from: " + new File(filePath).getName());
        info.setWrapText(true);

        TextArea textArea = new TextArea(content);
        textArea.setWrapText(true);
        textArea.setEditable(false);

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setOnAction(e -> stage.close());

        VBox root = new VBox(10, info, textArea, closeBtn);
        root.setPadding(new Insets(12));
        VBox.setVgrow(textArea, javafx.scene.layout.Priority.ALWAYS);

        Scene scene = new Scene(root, 980, 720);
        var css = AuthorPublishedBooksScreen.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);
        stage.showAndWait();
    }
}
