package org.example.ui;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.example.app.Navigator;
import org.example.db.BookDao;
import org.example.db.BorrowDao;
import org.example.db.PendingDao;
import org.example.domain.Book;
import org.example.domain.PendingBook;
import org.example.domain.User;

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
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Author view of pending submissions and published catalog with edit/delete rules.
 */
public final class AuthorPublishedBooksScreen {

    private AuthorPublishedBooksScreen() {}

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
    }

    public static Scene create(Navigator navigator, User user) {
        Label head = new Label("My submissions & published books");
        head.getStyleClass().add("screen-title");

        // Unified book list (shows both published and submissions)
        Label lb = new Label("My books & submissions");
        TableView<BookRow> bookTable = new TableView<>();
        var bItems = FXCollections.<BookRow>observableArrayList();
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
        bookTable.getColumns().addAll(List.of(bc1, bc2, bc3));
        bookTable.setItems(bItems);
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






        Button backBtn = new Button("Back");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setPrefWidth(120);
        backBtn.setOnAction(e -> navigator.showAuthorDashboard(user));

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
                                } catch (Exception ignored) {}
                                // optionally remove old rejected row
                                try { PendingDao.deleteByIdForAuthor(p.getId(), user.getId()); } catch (Exception ignored) {}
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
            BookRow r = bookTable.getSelectionModel().getSelectedItem();
            if (r == null) return;
            if (r.getAuthorUserId() != user.getId()) {
                new Alert(Alert.AlertType.WARNING, "You can only delete your own books.").showAndWait();
                return;
            }

            try {
                if (r.isPending()) {
                    // allow delete of pending (PENDING) or rejected
                    new Alert(Alert.AlertType.CONFIRMATION, "Delete this submission permanently?").showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                        try {
                            PendingDao.deleteByIdForAuthor(r.getPendingId(), user.getId());
                            refresh.run();
                        } catch (SQLException ex) {
                            new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                        }
                    });
                    return;
                }

                // published book deletion — only when not borrowed
                if (BorrowDao.countActiveBorrowsForBook(r.getId()) > 0) {
                    new Alert(Alert.AlertType.WARNING,
                            "Cannot delete this book while it is borrowed by a student or staff member.")
                            .showAndWait();
                    return;
                }
                new Alert(Alert.AlertType.CONFIRMATION, "Remove this book from the catalog?")
                        .showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                            try {
                                // Remove any pending edits that reference this book to avoid re-creating it later
                                try {
                                    PendingDao.deleteByOriginalBookId(r.getId());
                                } catch (SQLException ignored) {}
                                // Also remove legacy/unlinked reviewed submissions for this title+author
                                // so the librarian can't approve them later and recreate the catalog row.
                                try {
                                    PendingDao.deleteUnlinkedApprovedOrRejectedForAuthorTitle(user.getId(), r.getTitle());
                                } catch (SQLException ignored) {}
                                // Soft-remove so student/staff borrow history stays visible.
                                BookDao.removeFromCatalogButKeepHistory(r.getId());
                                refresh.run();
                            } catch (SQLException ex) {
                                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                            }
                        });
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
            }
        });

        HBox bbar = new HBox(10, editBookBtn, delBookBtn);
        bbar.setAlignment(Pos.CENTER_LEFT);
        bbar.setPadding(new Insets(16, 0, 0, 0));

        HBox bottomBar = new HBox(10, backBtn);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(16, 0, 0, 0));

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
        VBox main = new VBox(12, scrollPane, bbar, bottomBar);
        main.setPadding(new Insets(0, 16, 16, 16));

        Scene scene = new Scene(main, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        var css = AuthorPublishedBooksScreen.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }
}
