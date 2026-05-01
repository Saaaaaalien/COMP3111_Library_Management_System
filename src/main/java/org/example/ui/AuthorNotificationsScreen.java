package org.example.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Pair;
import org.example.app.Navigator;
import org.example.db.NotificationDao;
import org.example.domain.AppNotification;
import org.example.domain.User;
import org.example.service.NotificationService;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/**
 * Author notification board with category filter and search.
 */
public final class AuthorNotificationsScreen {

    private AuthorNotificationsScreen() {}

    private static final List<Pair<String, String>> AUTHOR_CATEGORY_FILTERS = List.of(
            new Pair<>("All categories", "ALL"),
            new Pair<>("Book accepted", NotificationService.CAT_AUTHOR_APPROVED),
            new Pair<>("Book rejected", NotificationService.CAT_AUTHOR_REJECTED),
            new Pair<>("Book removed (librarian)", NotificationService.CAT_AUTHOR_BOOK_REMOVED),
            new Pair<>("Review flagged confirmations", NotificationService.CAT_AUTHOR_REVIEW_FLAGGED),
            new Pair<>("Announcements", NotificationService.CAT_ANNOUNCEMENT)
    );

    public static Scene create(Navigator navigator, User user) {
        // Navigation handled by global menu; removed per-screen Back button



        Label title = new Label("Author notifications");
        title.getStyleClass().add("screen-title");

        ComboBox<Pair<String, String>> category = new ComboBox<>(FXCollections.observableArrayList(AUTHOR_CATEGORY_FILTERS));
        category.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Pair<String, String> item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getKey());
            }
        });
        category.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Pair<String, String> item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getKey());
            }
        });
        category.getSelectionModel().selectFirst();

        TextField search = new TextField();
        search.setPromptText("Search...");
        search.setMaxWidth(200);
        CheckBox showArchived = new CheckBox("Show archived");

        ListView<AppNotification> list = new ListView<>();
        list.setCellFactory(lv -> NotificationListCellFactory.create());

        Runnable refresh = () -> {
            try {
                Pair<String, String> sel = category.getSelectionModel().getSelectedItem();
                String cat = sel == null ? "ALL" : sel.getValue();
                list.setItems(FXCollections.observableArrayList(
                        NotificationDao.findForUserFiltered(user.getId(), cat, search.getText(), showArchived.isSelected())
                ));
            } catch (SQLException ex) {
                list.setItems(FXCollections.observableArrayList());
            }
        };
        refresh.run();
        category.setOnAction(e -> refresh.run());
        search.textProperty().addListener((a, b, c) -> refresh.run());
        showArchived.setOnAction(e -> refresh.run());

        Button readBtn = new Button("Mark read");
        readBtn.getStyleClass().add("secondary-button");
        readBtn.setPrefWidth(140);
        readBtn.setOnAction(e -> {
            var n = list.getSelectionModel().getSelectedItem();
            if (n == null) {
                return;
            }
            try {
                NotificationDao.markRead(n.getId(), user.getId(), Instant.now().toString());
                refresh.run();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not update.").showAndWait();
            }
        });

        Button readAllBtn = new Button("Mark all read");
        readAllBtn.getStyleClass().add("secondary-button");
        readAllBtn.setPrefWidth(140);
        readAllBtn.setOnAction(e -> {
            try {
                int n = NotificationDao.markAllRead(user.getId(), Instant.now().toString());
                if (n == 0) {
                    new Alert(Alert.AlertType.INFORMATION, "No unread notifications to mark.").showAndWait();
                }
                refresh.run();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not update all notifications.").showAndWait();
            }
        });

        Button archBtn = new Button("Archive");
        archBtn.getStyleClass().add("secondary-button");
        archBtn.setPrefWidth(140);
        archBtn.setOnAction(e -> {
            var n = list.getSelectionModel().getSelectedItem();
            if (n == null) {
                return;
            }
            try {
                NotificationDao.archive(n.getId(), user.getId(), Instant.now().toString());
                refresh.run();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not archive.").showAndWait();
            }
        });


        HBox filters = new HBox(5, new Label("Category:"), category, search, showArchived);
        HBox actions = new HBox(5, readBtn, readAllBtn, archBtn);
        actions.setPadding(new Insets(16, 0, 0, 0));

        VBox listWrapper = new VBox(list);
        listWrapper.setPadding(new Insets(16, 0, 0, 0));
        javafx.scene.layout.VBox.setVgrow(list, javafx.scene.layout.Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setTop(new VBox(8, title, filters, actions));
        root.setCenter(listWrapper);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        var css = AuthorNotificationsScreen.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }
}
