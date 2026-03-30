package org.example.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.app.Navigator;
import org.example.db.BookDao;
import org.example.db.BorrowDao;
import org.example.db.PendingDao;
import org.example.domain.Book;
import org.example.domain.PendingBook;
import org.example.domain.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

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

        PendingRow(PendingBook p) {
            this.id = p.getId();
            this.title = p.getTitle();
            this.genre = p.getGenre();
            this.status = p.getStatus();
        }

        public long getId() { return id; }
        public String getTitle() { return title; }
        public String getGenre() { return genre; }
        public String getStatus() { return status; }
    }

    public static class BookRow {
        private final long id;
        private final String title;
        private final String genre;
        private final String availability;

        BookRow(Book b) {
            this.id = b.getId();
            this.title = b.getTitle();
            this.genre = b.getGenre();
            this.availability = b.getAvailability().name();
        }

        public long getId() { return id; }
        public String getTitle() { return title; }
        public String getGenre() { return genre; }
        public String getAvailability() { return availability; }
    }

    public static Scene create(Navigator navigator, User user) {
        Label head = new Label("My submissions & published books");
        head.getStyleClass().add("screen-title");

        Label lp = new Label("Pending / reviewed submissions");
        TableView<PendingRow> pendingTable = new TableView<>();
        var pItems = FXCollections.<PendingRow>observableArrayList();
        TableColumn<PendingRow, String> pc1 = new TableColumn<>("Title");
        pc1.setCellValueFactory(new PropertyValueFactory<>("title"));
        TableColumn<PendingRow, String> pc2 = new TableColumn<>("Genre");
        pc2.setCellValueFactory(new PropertyValueFactory<>("genre"));
        TableColumn<PendingRow, String> pc3 = new TableColumn<>("Status");
        pc3.setCellValueFactory(new PropertyValueFactory<>("status"));
        pendingTable.getColumns().addAll(List.of(pc1, pc2, pc3));
        pendingTable.setItems(pItems);
        pendingTable.setPrefHeight(180);

        Label lb = new Label("Published in catalog");
        TableView<BookRow> bookTable = new TableView<>();
        var bItems = FXCollections.<BookRow>observableArrayList();
        TableColumn<BookRow, String> bc1 = new TableColumn<>("Title");
        bc1.setCellValueFactory(new PropertyValueFactory<>("title"));
        TableColumn<BookRow, String> bc2 = new TableColumn<>("Genre");
        bc2.setCellValueFactory(new PropertyValueFactory<>("genre"));
        TableColumn<BookRow, String> bc3 = new TableColumn<>("Availability");
        bc3.setCellValueFactory(new PropertyValueFactory<>("availability"));
        bookTable.getColumns().addAll(List.of(bc1, bc2, bc3));
        bookTable.setItems(bItems);
        bookTable.setPrefHeight(200);

        Runnable refresh = () -> {
            pItems.clear();
            bItems.clear();
            try {
                for (PendingBook p : PendingDao.findAllByAuthorUserId(user.getId())) {
                    pItems.add(new PendingRow(p));
                }
                for (Book b : BookDao.findByAuthorUserId(user.getId())) {
                    bItems.add(new BookRow(b));
                }
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not load data.").showAndWait();
            }
        };
        refresh.run();

        Button editPendingBtn = new Button("Edit pending");
        editPendingBtn.setOnAction(e -> {
            PendingRow r = pendingTable.getSelectionModel().getSelectedItem();
            if (r == null || !"PENDING".equalsIgnoreCase(r.getStatus())) {
                new Alert(Alert.AlertType.WARNING, "Select a row with status PENDING.").showAndWait();
                return;
            }
            try {
                Optional<PendingBook> opt = PendingDao.findById(r.getId());
                if (opt.isEmpty()) {
                    return;
                }
                PendingBook p = opt.get();
                TextField tTitle = new TextField(p.getTitle());
                TextField tGenre = new TextField(p.getGenre());
                TextArea tSum = new TextArea(p.getSummary());
                tSum.setPrefRowCount(5);
                GridPane g = new GridPane();
                g.setHgap(8);
                g.setVgap(8);
                g.addRow(0, new Label("Title"), tTitle);
                g.addRow(1, new Label("Genre"), tGenre);
                g.addRow(2, new Label("Summary"), tSum);
                Alert form = new Alert(Alert.AlertType.CONFIRMATION);
                form.setTitle("Edit pending book");
                form.getDialogPane().setContent(g);
                form.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                    try {
                        PendingDao.updatePendingSubmission(
                                p.getId(), user.getId(),
                                tTitle.getText().trim(),
                                tGenre.getText().trim(),
                                tSum.getText().trim(),
                                p.getFileName(), p.getFilePath(), p.getFileSize(), p.getFileType(),
                                p.getCoverPath()
                        );
                        refresh.run();
                    } catch (SQLException ex) {
                        new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                    }
                });
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
            }
        });

        Button delPendingBtn = new Button("Delete pending");
        delPendingBtn.setOnAction(e -> {
            PendingRow r = pendingTable.getSelectionModel().getSelectedItem();
            if (r == null || !"PENDING".equalsIgnoreCase(r.getStatus())) {
                new Alert(Alert.AlertType.WARNING, "Select a PENDING submission to delete.").showAndWait();
                return;
            }
            new Alert(Alert.AlertType.CONFIRMATION, "Delete this pending submission?")
                    .showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                        try {
                            PendingDao.deletePending(r.getId(), user.getId());
                            refresh.run();
                        } catch (SQLException ex) {
                            new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                        }
                    });
        });

        Button editBookBtn = new Button("Edit published (metadata)");
        editBookBtn.setOnAction(e -> {
            BookRow r = bookTable.getSelectionModel().getSelectedItem();
            if (r == null) {
                return;
            }
            try {
                if (BorrowDao.countActiveBorrowsForBook(r.getId()) > 0) {
                    new Alert(Alert.AlertType.WARNING,
                            "Cannot edit while someone has this book borrowed.").showAndWait();
                    return;
                }
                Optional<Book> opt = BookDao.findById(r.getId());
                if (opt.isEmpty()) {
                    return;
                }
                Book bk = opt.get();
                TextField tTitle = new TextField(bk.getTitle());
                TextField tGenre = new TextField(bk.getGenre());
                TextArea tSum = new TextArea(bk.getSummary());
                tSum.setPrefRowCount(5);
                GridPane g = new GridPane();
                g.setHgap(8);
                g.setVgap(8);
                g.addRow(0, new Label("Title"), tTitle);
                g.addRow(1, new Label("Genre"), tGenre);
                g.addRow(2, new Label("Summary"), tSum);
                Alert form = new Alert(Alert.AlertType.CONFIRMATION);
                form.setTitle("Edit published book");
                form.getDialogPane().setContent(g);
                form.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                    try {
                        BookDao.updateAuthorMetadata(
                                bk.getId(), user.getId(), user.getFullName(),
                                tTitle.getText().trim(),
                                tGenre.getText().trim(),
                                tSum.getText().trim()
                        );
                        refresh.run();
                    } catch (SQLException ex) {
                        new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                    }
                });
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
            }
        });

        Button delBookBtn = new Button("Delete published");
        delBookBtn.setOnAction(e -> {
            BookRow r = bookTable.getSelectionModel().getSelectedItem();
            if (r == null) {
                return;
            }
            try {
                if (BorrowDao.countActiveBorrowsForBook(r.getId()) > 0) {
                    new Alert(Alert.AlertType.WARNING,
                            "Cannot delete while the book is borrowed.").showAndWait();
                    return;
                }
                new Alert(Alert.AlertType.CONFIRMATION, "Remove this book from the catalog permanently?")
                        .showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                            try {
                                BookDao.deleteById(r.getId());
                                refresh.run();
                            } catch (SQLException ex) {
                                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                            }
                        });
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
            }
        });

        Button backBtn = new Button("Back");
        backBtn.setOnAction(e -> navigator.showAuthorDashboard(user));

        HBox pbar = new HBox(8, editPendingBtn, delPendingBtn);
        HBox bbar = new HBox(8, editBookBtn, delBookBtn);

        VBox root = new VBox(12, head, lp, pendingTable, pbar, lb, bookTable, bbar, backBtn);
        root.setPadding(new Insets(16));

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        var css = AuthorPublishedBooksScreen.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }
}
