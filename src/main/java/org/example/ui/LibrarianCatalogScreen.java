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
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.app.Navigator;
import org.example.db.BookDao;
import org.example.domain.Book;
import org.example.domain.User;
import org.example.service.BookRemovalService;

import java.sql.SQLException;
import java.util.List;

/**
 * Librarian view of all catalog books with removal (notifies borrowers and returns loans).
 */
public final class LibrarianCatalogScreen {

    private LibrarianCatalogScreen() {}

    public static class Row {
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

    public static Scene create(Navigator navigator, User librarian) {
        Label title = new Label("Catalog maintenance");
        title.getStyleClass().add("screen-title");

        TableView<Row> table = new TableView<>();
        var items = FXCollections.<Row>observableArrayList();

        TableColumn<Row, String> c1 = new TableColumn<>("Title");
        c1.setCellValueFactory(new PropertyValueFactory<>("title"));
        TableColumn<Row, String> c2 = new TableColumn<>("Author");
        c2.setCellValueFactory(new PropertyValueFactory<>("author"));
        TableColumn<Row, String> c3 = new TableColumn<>("Genre");
        c3.setCellValueFactory(new PropertyValueFactory<>("genre"));
        TableColumn<Row, String> c4 = new TableColumn<>("Status");
        c4.setCellValueFactory(new PropertyValueFactory<>("availability"));
        table.getColumns().addAll(List.of(c1, c2, c3, c4));
        table.setItems(items);

        Runnable refresh = () -> {
            items.clear();
            try {
                for (Book b : BookDao.findAll()) {
                    items.add(new Row(b));
                }
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not load books.").showAndWait();
            }
        };
        refresh.run();

        Button removeBtn = new Button("Remove selected from catalog");
        removeBtn.getStyleClass().add("primary-button");
        removeBtn.setOnAction(e -> {
            Row sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) {
                new Alert(Alert.AlertType.WARNING, "Select a book.").showAndWait();
                return;
            }
            Alert c = new Alert(Alert.AlertType.CONFIRMATION);
            c.setContentText("Remove \"" + sel.getTitle() + "\"? Active borrowers will be notified and loans closed.");
            c.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
                try {
                    BookRemovalService.removePublishedBook(sel.getId());
                    new Alert(Alert.AlertType.INFORMATION, "Book removed.").showAndWait();
                    refresh.run();
                } catch (SQLException ex) {
                    new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                }
            });
        });

        Button approvalBtn = new Button("Pending approvals");
        approvalBtn.setOnAction(e -> navigator.showLibrarianApproval(librarian));

        Button backBtn = new Button("Logout to portal");
        backBtn.setOnAction(e -> navigator.showLibrarianPortal());

        HBox bar = new HBox(10, removeBtn, approvalBtn, backBtn);
        bar.setPadding(new Insets(10, 0, 0, 0));

        VBox top = new VBox(8, title, bar);
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
}
