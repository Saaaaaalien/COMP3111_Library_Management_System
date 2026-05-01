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
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.example.app.Navigator;
import org.example.db.NotificationDao;
import org.example.domain.AppNotification;
import org.example.domain.User;
import org.example.service.NotificationService;

import java.sql.SQLException;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Notification board for students and staff.
 */
public final class StudentStaffNotificationBoardScreen {

    private StudentStaffNotificationBoardScreen() {}

    public static Scene create(Navigator navigator, User user, boolean returnToBorrowedBooks) {
        Label title = new Label("Notification Board");
        title.getStyleClass().add("screen-title");
        Label subLbl = new Label("Student/Staff: " + user.getFullName());
        subLbl.setStyle("-fx-font-size: 12; -fx-text-fill: #666;");
        VBox headerBox = new VBox(4, title, subLbl);
        headerBox.setPadding(new Insets(20, 20, 0, 20));
        headerBox.setStyle("-fx-border-color: #f0f0f0; -fx-border-width: 0 0 1 0;");

        ComboBox<String> category = new ComboBox<>(FXCollections.observableArrayList(
                "ALL",
                NotificationService.CAT_DUE_REMINDER,
                NotificationService.CAT_BOOK_REMOVED,
                NotificationService.CAT_ANNOUNCEMENT,
                NotificationService.CAT_BORROW_EVENT,
                NotificationService.CAT_RETURN_EVENT,
                NotificationService.CAT_AUTHOR_REVIEW_REPLY
        ));
        category.getSelectionModel().selectFirst();

        TextField search = new TextField();
        search.setPromptText("Search title or body...");
        search.setMaxWidth(220);

        CheckBox showArchived = new CheckBox("Show archived");
        CheckBox urgentOnly = new CheckBox("⚠ Urgent only");
        urgentOnly.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold;");

        Label unreadBadge = new Label();
        unreadBadge.setStyle("-fx-font-size: 11; -fx-text-fill: #c0392b; -fx-font-weight: bold;");
        refreshUnreadBadge(unreadBadge, user.getId());

        ListView<AppNotification> list = new ListView<>();
        list.setCellFactory(lv -> NotificationListCellFactory.create());

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
                if (urgentOnly.isSelected()) {
                    rows = rows.stream()
                            .filter(NotificationService::isUrgentHighlight)
                            .collect(Collectors.toList());
                }
                list.setItems(FXCollections.observableArrayList(rows));
                refreshUnreadBadge(unreadBadge, user.getId());
            } catch (SQLException ex) {
                list.setItems(FXCollections.observableArrayList());
            }
        };
        refresh.run();

        category.setOnAction(e -> refresh.run());
        search.textProperty().addListener((a, b, c) -> refresh.run());
        showArchived.setOnAction(e -> refresh.run());
        urgentOnly.setOnAction(e -> refresh.run());

        Button readBtn = new Button("Mark read");
        readBtn.getStyleClass().add("primary-button");
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
        archBtn.getStyleClass().add("secondary-button");
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

        Button refreshBtn = new Button("⟳ Refresh");
        refreshBtn.setOnAction(e -> refresh.run());

        Button backBtn = new Button("Back");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> {
            if (returnToBorrowedBooks) {
                navigator.showMyBorrowedBooks(user);
            } else {
                navigator.showAvailableBooks(user);
            }
        });

        HBox filters = new HBox(10, new Label("Category:"), category, new Label("Search:"), search, showArchived, urgentOnly);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.setPadding(new Insets(10, 20, 6, 20));

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, readBtn, readAllBtn, archBtn, refreshBtn, spacer, unreadBadge);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(0, 20, 0, 20));

        HBox footer = new HBox(backBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(15, 20, 15, 20));

        VBox top = new VBox(0, headerBox, filters, actions);

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(list);
        root.setBottom(footer);
        root.getStyleClass().add("app-root");
        BorderPane.setMargin(list, new Insets(6, 20, 0, 20));

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

    private static void refreshUnreadBadge(Label badge, long userId) {
        try {
            int unread = NotificationDao.countUnread(userId);
            badge.setText(unread > 0 ? unread + " unread" : "All read");
        } catch (SQLException ex) {
            badge.setText("");
        }
    }
}
