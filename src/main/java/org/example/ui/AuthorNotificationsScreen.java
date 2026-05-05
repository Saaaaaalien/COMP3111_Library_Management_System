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
            new Pair<>("Book updated (librarian)", NotificationService.CAT_AUTHOR_BOOK_UPDATED),
            new Pair<>("Review flagged confirmations", NotificationService.CAT_AUTHOR_REVIEW_FLAGGED),
            new Pair<>("Account updates", NotificationService.CAT_ACCOUNT_UPDATED),
            new Pair<>("Account status", NotificationService.CAT_ACCOUNT_STATUS),
            new Pair<>("Announcements", NotificationService.CAT_ANNOUNCEMENT)
    );

    public static Scene create(Navigator navigator, User user) {
        // Navigation handled by global menu; removed per-screen Back button
    Label title = new Label("Notifications");
        title.getStyleClass().add("screen-title");
    Label unreadCountLabel = new Label("Unread: 0");
    unreadCountLabel.getStyleClass().add("info-label");

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

        final Runnable[] syncArchiveBtnLabel = new Runnable[] { () -> {} };

        Runnable refresh = () -> {
            try {
                Pair<String, String> sel = category.getSelectionModel().getSelectedItem();
                String cat = sel == null ? "ALL" : sel.getValue();
                list.setItems(FXCollections.observableArrayList(
                        NotificationDao.findForUserFiltered(user.getId(), cat, search.getText(), showArchived.isSelected())
                ));
                int unread = NotificationDao.countUnread(user.getId());
                unreadCountLabel.setText("Unread: " + unread);
                title.setText(unread > 0 ? ("Notifications (" + unread + ")") : "Notifications");
            } catch (SQLException ex) {
                list.setItems(FXCollections.observableArrayList());
                unreadCountLabel.setText("Unread: --");
            }
            syncArchiveBtnLabel[0].run();
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

        Button archiveToggleBtn = new Button("Archive");
        archiveToggleBtn.getStyleClass().add("secondary-button");
        archiveToggleBtn.setPrefWidth(140);
        syncArchiveBtnLabel[0] = () -> {
            var selected = list.getSelectionModel().getSelectedItem();
            archiveToggleBtn.setText(selected != null && selected.isArchived() ? "Unarchive" : "Archive");
        };
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> syncArchiveBtnLabel[0].run());
        archiveToggleBtn.setOnAction(e -> {
            var n = list.getSelectionModel().getSelectedItem();
            if (n == null) {
                return;
            }
            try {
                if (n.isArchived()) {
                    NotificationDao.unarchive(n.getId(), user.getId());
                } else {
                    NotificationDao.archive(n.getId(), user.getId(), Instant.now().toString());
                }
                refresh.run();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not update archive status.").showAndWait();
            }
        });


        HBox filters = new HBox(10, new Label("Category:"), category, new Label("Search:"), search, showArchived);
        HBox actions = new HBox(10, readBtn, readAllBtn, archiveToggleBtn, unreadCountLabel);
        actions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
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
