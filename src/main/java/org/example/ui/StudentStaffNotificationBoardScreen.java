package org.example.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
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
 * Notification board for students and staff.
 */
public final class StudentStaffNotificationBoardScreen {

    private StudentStaffNotificationBoardScreen() {}

    private static final List<Pair<String, String>> STAFF_CATEGORY_FILTERS = List.of(
            new Pair<>("All categories", "ALL"),
            new Pair<>("Due date reminders", NotificationService.CAT_DUE_REMINDER),
            new Pair<>("Book removed from catalog", NotificationService.CAT_BOOK_REMOVED),
            new Pair<>("Announcements", NotificationService.CAT_ANNOUNCEMENT)
    );

    public static Scene create(Navigator navigator, User user) {
        Label title = new Label("Notifications");
        title.getStyleClass().add("screen-title");

        ComboBox<Pair<String, String>> category = new ComboBox<>(FXCollections.observableArrayList(STAFF_CATEGORY_FILTERS));
        category.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Pair<String, String> item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getKey());
            }
        });
        category.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Pair<String, String> item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getKey());
            }
        });
        category.getSelectionModel().selectFirst();

        TextField search = new TextField();
        search.setPromptText("Search title or body...");
        search.setMaxWidth(220);

        CheckBox showArchived = new CheckBox("Show archived");

        ListView<AppNotification> list = new ListView<>();
        list.setCellFactory(lv -> NotificationListCellFactory.create());

        Runnable refresh = () -> {
            try {
                Pair<String, String> sel = category.getSelectionModel().getSelectedItem();
                String cat = sel == null ? "ALL" : sel.getValue();
                var rows = NotificationDao.findForUserFiltered(
                        user.getId(),
                        cat,
                        search.getText(),
                        showArchived.isSelected()
                );
                list.setItems(FXCollections.observableArrayList(rows));
            } catch (SQLException ex) {
                list.setItems(FXCollections.observableArrayList());
            }
        };
        refresh.run();

        category.setOnAction(e -> refresh.run());
        search.textProperty().addListener((a, b, c) -> refresh.run());
        showArchived.setOnAction(e -> refresh.run());

        Button readBtn = new Button("Mark read");
        readBtn.setOnAction(e -> {
            AppNotification n = list.getSelectionModel().getSelectedItem();
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
        archBtn.setOnAction(e -> {
            AppNotification n = list.getSelectionModel().getSelectedItem();
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

        Button backBtn = new Button("Back");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> navigator.showAvailableBooks(user));

        HBox filters = new HBox(10, new Label("Category:"), category, new Label("Search:"), search, showArchived);
        filters.setAlignment(Pos.CENTER_LEFT);
        HBox actions = new HBox(10, readBtn, readAllBtn, archBtn, backBtn);

        VBox top = new VBox(8, title, filters, actions);
        top.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(list);
        root.setPadding(new Insets(10));
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        var css = StudentStaffNotificationBoardScreen.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }
}
