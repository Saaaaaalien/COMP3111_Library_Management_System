package org.example.ui;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import org.example.app.Navigator;
import org.example.db.NotificationDao;
import org.example.domain.AppNotification;
import org.example.domain.User;
import org.example.service.NotificationService;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Notification board for students and staff.
 */
public final class StudentStaffNotificationBoardScreen {

    private StudentStaffNotificationBoardScreen() {}

    public static Scene create(Navigator navigator, User user, boolean returnToBorrowedBooks) {
        Label title = new Label("Notifications");
        title.getStyleClass().add("screen-title");
        Label unreadCountLabel = new Label("Unread: 0");
        unreadCountLabel.getStyleClass().add("info-label");

        ComboBox<String> category = new ComboBox<>(FXCollections.observableArrayList(
                "ALL",
                NotificationService.CAT_DUE_REMINDER,
                NotificationService.CAT_BOOK_REMOVED,
                NotificationService.CAT_ANNOUNCEMENT,
                NotificationService.CAT_BORROW_EVENT,
                NotificationService.CAT_RETURN_EVENT,
                NotificationService.CAT_AUTHOR_REVIEW_REPLY,
                NotificationService.CAT_ACCOUNT_UPDATED,
                NotificationService.CAT_ACCOUNT_STATUS,
                NotificationService.CAT_BOOK_REQUEST
        ));
        category.getSelectionModel().selectFirst();

        TextField search = new TextField();
        search.setPromptText("Search title or body...");
        search.setMaxWidth(220);

        CheckBox showArchived = new CheckBox("Show archived");

        ListView<AppNotification> list = new ListView<>();
        list.setCellFactory(lv -> NotificationListCellFactory.create());

        final Runnable[] syncArchiveBtnLabel = new Runnable[] { () -> {} };

        Runnable refresh = () -> {
            try {
                String cat = category.getSelectionModel().getSelectedItem();
                if (cat == null || cat.isBlank()) {
                    cat = "ALL";
                }
                var rows = NotificationDao.findForUserFiltered(
                        user.getId(),
                        cat,
                        search.getText(),
                        showArchived.isSelected()
                );
                list.setItems(FXCollections.observableArrayList(rows));
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
        readBtn.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
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
        archiveToggleBtn.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        syncArchiveBtnLabel[0] = () -> {
            AppNotification selected = list.getSelectionModel().getSelectedItem();
            archiveToggleBtn.setText(selected != null && selected.isArchived() ? "Unarchive" : "Archive");
        };
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> syncArchiveBtnLabel[0].run());
        archiveToggleBtn.setOnAction(e -> {
            AppNotification n = list.getSelectionModel().getSelectedItem();
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

        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().add("secondary-button");
        deleteBtn.setPrefWidth(140);
        deleteBtn.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        deleteBtn.setOnAction(e -> {
            AppNotification n = list.getSelectionModel().getSelectedItem();
            if (n == null) {
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Delete notification");
            confirm.setHeaderText(null);
            confirm.setContentText("Permanently delete this notification?");
            Optional<ButtonType> ans = confirm.showAndWait();
            if (ans.isEmpty() || ans.get() != ButtonType.OK) {
                return;
            }
            try {
                NotificationDao.deleteById(n.getId(), user.getId());
                refresh.run();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not delete.").showAndWait();
            }
        });

        // Back navigation now provided by global menu; per-screen Back removed.

        HBox filters = new HBox(10, new Label("Category:"), category, new Label("Search:"), search, showArchived);
        filters.setAlignment(Pos.CENTER_LEFT);
        HBox actions = new HBox(10, readBtn, readAllBtn, archiveToggleBtn, deleteBtn, unreadCountLabel);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox top = new VBox(8, title, filters, actions);
        top.setPadding(new Insets(10));

        VBox listWrapper = new VBox(list);
        listWrapper.setPadding(new Insets(8, 0, 0, 0));
        VBox.setVgrow(list, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(listWrapper);
        root.setPadding(new Insets(10));
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        var css = StudentStaffNotificationBoardScreen.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }

    public static Scene create(Navigator navigator, User user) {
        // Default behavior: if no explicit return target is provided,
        // return to Available Books (consistent with typical entry point).
        return create(navigator, user, false);
    }
}
