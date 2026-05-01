package org.example.ui;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import org.example.app.Navigator;
import org.example.db.NotificationDao;
import org.example.domain.AppNotification;
import org.example.domain.User;
import org.example.service.NotificationService;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
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
import javafx.util.Pair;

/**
 * Librarian notification board with category filter and search.
 */
public final class LibrarianNotificationBoardScreen {

    private LibrarianNotificationBoardScreen() {}

    private static final List<Pair<String, String>> LIBRARIAN_CATEGORY_FILTERS = List.of(
            new Pair<>("All categories",          "ALL"),
            new Pair<>("New book submissions",    NotificationService.CAT_NEW_SUBMISSION),
            new Pair<>("User registrations",      NotificationService.CAT_USER_REGISTERED),
            new Pair<>("Borrow activity",         NotificationService.CAT_LIB_BORROW_ACTIVITY),
            new Pair<>("Return activity",         NotificationService.CAT_LIB_RETURN_ACTIVITY),
            new Pair<>("User account updates",    NotificationService.CAT_LIB_USER_PROFILE_UPDATED),
            new Pair<>("Overdue borrows",         NotificationService.CAT_OVERDUE_BORROW),
            new Pair<>("Announcements",           NotificationService.CAT_ANNOUNCEMENT)
    );

    public static Scene create(Navigator navigator, User librarian) {
        // Back navigation now provided by global menu; per-screen Back removed.

        Label title = new Label("Notifications");
        title.getStyleClass().add("screen-title");
        Label unreadCountLabel = new Label("Unread: 0");
        unreadCountLabel.getStyleClass().add("info-label");

        ComboBox<Pair<String, String>> category =
                new ComboBox<>(FXCollections.observableArrayList(LIBRARIAN_CATEGORY_FILTERS));
        category.setButtonCell(pairCell());
        category.setCellFactory(lv -> pairCell());
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
                String cat = (sel == null) ? "ALL" : sel.getValue();
                list.setItems(FXCollections.observableArrayList(
                        NotificationDao.findForUserFiltered(librarian.getId(), cat, search.getText(), showArchived.isSelected())
                ));
                int unread = NotificationDao.countUnread(librarian.getId());
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

        Button markReadBtn = new Button("Mark read");
        markReadBtn.getStyleClass().add("secondary-button");
        markReadBtn.setPrefWidth(140);
        markReadBtn.setOnAction(e -> {
            AppNotification n = list.getSelectionModel().getSelectedItem();
            if (n == null) {
                return;
            }
            try {
                NotificationDao.markRead(n.getId(), librarian.getId(), Instant.now().toString());
                refresh.run();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not update.").showAndWait();
            }
        });

        Button markAllReadBtn = new Button("Mark all read");
        markAllReadBtn.getStyleClass().add("secondary-button");
        markAllReadBtn.setPrefWidth(140);
        markAllReadBtn.setOnAction(e -> {
            try {
                int count = NotificationDao.markAllRead(librarian.getId(), Instant.now().toString());
                if (count == 0) {
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
                    NotificationDao.unarchive(n.getId(), librarian.getId());
                } else {
                    NotificationDao.archive(n.getId(), librarian.getId(), Instant.now().toString());
                }
                refresh.run();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not update archive status.").showAndWait();
            }
        });

        HBox back = new HBox(5);
        HBox filters = new HBox(10, new Label("Category:"), category, new Label("Search:"), search, showArchived);
        HBox actions = new HBox(10, markReadBtn, markAllReadBtn, archiveToggleBtn, unreadCountLabel);
        actions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        actions.setPadding(new Insets(16, 0, 0, 0));

        VBox listWrapper = new VBox(list);
        listWrapper.setPadding(new Insets(16, 0, 0, 0));
        VBox.setVgrow(list, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setTop(new VBox(8, back, title, filters, actions));
        root.setCenter(listWrapper);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL css = LibrarianNotificationBoardScreen.class.getResource("/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        return scene;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ListCell<Pair<String, String>> pairCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Pair<String, String> item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getKey());
            }
        };
    }
}
