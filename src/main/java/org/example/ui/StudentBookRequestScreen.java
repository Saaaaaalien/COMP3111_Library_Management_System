package org.example.ui;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import org.example.app.Navigator;
import org.example.db.BookRequestDao;
import org.example.domain.BookRequest;
import org.example.domain.BookRequest.RequestStatus;
import org.example.domain.User;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Separator;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Student/staff: request a new book and view request status history (Task 1.10).
 */
public final class StudentBookRequestScreen {

    private static final List<String> GENRES = List.of(
            "Fiction", "Non-Fiction", "Mystery", "Fantasy", "Science Fiction",
            "Biography", "History", "Self-Help", "Education", "Technology", "Romance", "Other"
    );

    private StudentBookRequestScreen() {}

    public static Scene create(Navigator navigator, User user) {
        Label title = new Label("Request a New Book");
        title.getStyleClass().add("screen-title");

        TextField titleField = new TextField();
        titleField.setPromptText("Book title");
        TextField authorField = new TextField();
        authorField.setPromptText("Author name");
        ComboBox<String> genreBox = new ComboBox<>(FXCollections.observableArrayList(GENRES));
        genreBox.getSelectionModel().selectFirst();
        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Why should the library acquire this book? (optional but helpful for librarians)");
        reasonArea.setPrefRowCount(5);
        reasonArea.setWrapText(true);
        reasonArea.setMinHeight(96);
        reasonArea.setPrefHeight(120);

        TableView<RequestRow> history = new TableView<>();
        TableColumn<RequestRow, String> colTitle = new TableColumn<>("Title");
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colTitle.setPrefWidth(160);
        TableColumn<RequestRow, String> colAuthor = new TableColumn<>("Author");
        colAuthor.setCellValueFactory(new PropertyValueFactory<>("author"));
        TableColumn<RequestRow, String> colGenre = new TableColumn<>("Genre");
        colGenre.setCellValueFactory(new PropertyValueFactory<>("genre"));
        TableColumn<RequestRow, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        TableColumn<RequestRow, String> colCreated = new TableColumn<>("Submitted");
        colCreated.setCellValueFactory(new PropertyValueFactory<>("createdDisplay"));
        colCreated.setPrefWidth(110);
        TableColumn<RequestRow, String> colProcessed = new TableColumn<>("Processed");
        colProcessed.setCellValueFactory(new PropertyValueFactory<>("processedDisplay"));
        colProcessed.setPrefWidth(110);
        TableColumn<RequestRow, String> colNotes = new TableColumn<>("Notes");
        colNotes.setCellValueFactory(new PropertyValueFactory<>("notesSnippet"));
        colNotes.setPrefWidth(200);
        history.getColumns().addAll(List.of(colTitle, colAuthor, colGenre, colStatus, colCreated, colProcessed, colNotes));
        history.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Runnable loadHistory = () -> {
            try {
                List<BookRequest> rows = BookRequestDao.findByRequestedByUserId(user.getId());
                history.setItems(FXCollections.observableArrayList(rows.stream().map(RequestRow::new).toList()));
            } catch (SQLException e) {
                history.setItems(FXCollections.observableArrayList());
            }
        };
        loadHistory.run();

        Button submitBtn = new Button("Submit request");
        submitBtn.getStyleClass().add("primary-button");
        submitBtn.setOnAction(e -> {
            String t = titleField.getText() == null ? "" : titleField.getText().trim();
            String a = authorField.getText() == null ? "" : authorField.getText().trim();
            if (t.isEmpty() || a.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Title and author are required.").showAndWait();
                return;
            }
            String genre = genreBox.getSelectionModel().getSelectedItem();
            String desc = reasonArea.getText() == null ? "" : reasonArea.getText().trim();
            try {
                if (BookRequestDao.hasSimilarPendingRequest(user.getId(), t, a)) {
                    Alert dup = new Alert(Alert.AlertType.CONFIRMATION);
                    dup.setTitle("Possible duplicate");
                    dup.setHeaderText(null);
                    dup.setContentText("You already have a pending request with the same title and author. Submit anyway?");
                    if (dup.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                        return;
                    }
                } else {
                    int prior = BookRequestDao.countSameTitleAuthorForUser(user.getId(), t, a);
                    if (prior > 0) {
                        Alert again = new Alert(Alert.AlertType.CONFIRMATION);
                        again.setTitle("Similar request in your history");
                        again.setHeaderText(null);
                        again.setContentText("You already submitted " + prior + " request(s) for this title and author "
                                + "(any status). Submit another entry?");
                        if (again.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                            return;
                        }
                    }
                }
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not verify duplicates.").showAndWait();
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm request");
            confirm.setHeaderText(null);
            confirm.setContentText("Submit this request to the librarian?\n\nTitle: " + t + "\nAuthor: " + a);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
            try {
                BookRequest br = new BookRequest(
                        0,
                        user.getId(),
                        user.getFullName(),
                        t,
                        a,
                        desc.isEmpty() ? null : desc,
                        genre,
                        RequestStatus.PENDING,
                        null,
                        null,
                        null,
                        Instant.now().toString(),
                        null
                );
                long id = BookRequestDao.insert(br);
                new Alert(Alert.AlertType.INFORMATION,
                        "Request submitted (reference #" + id + "). You will be notified when it is processed.")
                        .showAndWait();
                titleField.clear();
                authorField.clear();
                reasonArea.clear();
                genreBox.getSelectionModel().selectFirst();
                loadHistory.run();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Submit failed: " + ex.getMessage()).showAndWait();
            }
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.getStyleClass().add("form-grid");
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(Region.USE_PREF_SIZE);
        labelCol.setHgrow(Priority.NEVER);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(labelCol, fieldCol);

        int r = 0;
        form.add(new Label("Title *"), 0, r);
        form.add(titleField, 1, r++);
        form.add(new Label("Author *"), 0, r);
        form.add(authorField, 1, r++);
        form.add(new Label("Genre"), 0, r);
        form.add(genreBox, 1, r++);
        form.add(new Label("Reason"), 0, r);
        form.add(reasonArea, 1, r);
        GridPane.setHgrow(titleField, Priority.ALWAYS);
        GridPane.setHgrow(authorField, Priority.ALWAYS);
        GridPane.setHgrow(genreBox, Priority.ALWAYS);
        GridPane.setHgrow(reasonArea, Priority.ALWAYS);
        GridPane.setValignment(reasonArea, VPos.TOP);
        genreBox.setMaxWidth(Double.MAX_VALUE);

        Label hint = new Label(
                "Submit a title you would like the library to consider. Your past submissions and statuses appear below.");
        hint.getStyleClass().add("login-hint");
        hint.setWrapText(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(12, spacer, submitBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("button-bar");

        VBox formCard = new VBox(12, form, actions);
        formCard.getStyleClass().add("content-card");
        formCard.setMaxWidth(Double.MAX_VALUE);

        Label histLbl = new Label("Your request history");
        histLbl.getStyleClass().add("section-heading");

        VBox tableShell = new VBox(history);
        tableShell.getStyleClass().add("table-container");
        VBox.setVgrow(history, Priority.ALWAYS);
        history.setMinHeight(200);
        tableShell.setMaxWidth(Double.MAX_VALUE);

        VBox page = new VBox(16, title, hint, formCard, new Separator(), histLbl, tableShell);
        page.setPadding(new Insets(8, 4, 8, 4));
        VBox.setVgrow(tableShell, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setCenter(page);
        root.setPadding(new Insets(12, 16, 16, 16));
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        var css = StudentBookRequestScreen.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }

    public static class RequestRow {
        private final String title;
        private final String author;
        private final String genre;
        private final String status;
        private final String createdDisplay;
        private final String processedDisplay;
        private final String notesSnippet;

        RequestRow(BookRequest q) {
            this.title = q.getTitle();
            this.author = q.getAuthorName();
            this.genre = q.getGenre() != null ? q.getGenre() : "—";
            this.status = q.getStatus().name();
            this.createdDisplay = shortIso(q.getCreatedAt());
            this.processedDisplay = q.getProcessedAt() == null || q.getProcessedAt().isBlank()
                    ? "—" : shortIso(q.getProcessedAt());
            String n = q.getApprovalNotes();
            if (n == null || n.isBlank()) {
                this.notesSnippet = "—";
            } else {
                this.notesSnippet = n.length() > 80 ? n.substring(0, 77) + "…" : n;
            }
        }

        private static String shortIso(String iso) {
            if (iso == null || iso.length() < 10) return iso == null ? "" : iso;
            try {
                return iso.substring(0, 10);
            } catch (Exception e) {
                return iso;
            }
        }

        public String getTitle() { return title; }
        public String getAuthor() { return author; }
        public String getGenre() { return genre; }
        public String getStatus() { return status; }
        public String getCreatedDisplay() { return createdDisplay; }
        public String getProcessedDisplay() { return processedDisplay; }
        public String getNotesSnippet() { return notesSnippet; }
    }
}
