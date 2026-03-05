package org.example.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import org.example.domain.Book;
import org.example.domain.User;
import org.example.service.BorrowService;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Available books list for Student/Staff; borrow action with confirmation.
 */
public final class AvailableBooksScreen {

    private static final int SUMMARY_PREF_WIDTH = 250;

    private AvailableBooksScreen() {}

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

        TableColumn<BookRow, String> colAvailability = new TableColumn<>("Status");
        colAvailability.setCellValueFactory(new PropertyValueFactory<>("availability"));
        colAvailability.setPrefWidth(90);

        TableColumn<BookRow, String> colSummary = new TableColumn<>("Abstract / Summary");
        colSummary.setCellValueFactory(new PropertyValueFactory<>("summary"));
        colSummary.setPrefWidth(SUMMARY_PREF_WIDTH);

        table.getColumns().addAll(List.of(colTitle, colAuthor, colPublishDate, colAvailability, colSummary));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Runnable refresh = () -> {
            items.clear();
            try {
                List<Book> books = org.example.db.BookDao.findAllAvailable();
                for (Book b : books) {
                    items.add(new BookRow(b));
                }
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Error", "Could not load books.");
            }
        };
        refresh.run();

        Button borrowBtn = new Button("Borrow Selected Book");
        borrowBtn.setOnAction(e -> {
            BookRow selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert(Alert.AlertType.WARNING, "No selection", "Please select a book to borrow.");
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Borrow");
            confirm.setHeaderText("Borrow this book?");
            confirm.setContentText("Title: " + selected.getTitle());
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                try {
                    BorrowService.borrow(selected.getBookId(), currentUser.getId());
                    showAlert(Alert.AlertType.INFORMATION, "Success", "You have successfully borrowed the book.");
                    refresh.run();
                } catch (BorrowService.BorrowException ex) {
                    showAlert(Alert.AlertType.ERROR, "Borrow failed", ex.getMessage());
                    refresh.run();
                } catch (SQLException ex) {
                    showAlert(Alert.AlertType.ERROR, "Borrow failed", "A database error occurred.");
                }
            }
        });

        Button logoutBtn = new Button("Logout");
        logoutBtn.setOnAction(e -> navigator.showStudentStaffPortal());

        HBox buttons = new HBox(10, borrowBtn, logoutBtn);
        buttons.setPadding(new Insets(10, 0, 0, 0));

        VBox top = new VBox(10, title, new Label("Logged in as: " + currentUser.getFullName() + " (" + currentUser.getUsername() + ")"));
        VBox center = new VBox(10, table, buttons);
        center.setPadding(new Insets(10));
        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(center);
        root.setPadding(new Insets(20));

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL cssResource = AvailableBooksScreen.class.getResource("/app.css");
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

    /**
     * Table row model for Book (TableView needs getters for property names).
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

        private static String formatPublishDate(String iso) {
            if (iso == null || iso.isEmpty()) return "";
            try {
                return java.time.Instant.parse(iso).toString().substring(0, 10);
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
