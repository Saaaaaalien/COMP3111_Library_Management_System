package org.example.ui;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
 * Notification board for librarians.
 * <p>
 * Displays system-level notifications: new book submissions, user registrations,
 * overdue borrow alerts, and general announcements.  All notifications are
 * scoped to the signed-in librarian's userId and use the shared
 * {@link NotificationListCellFactory} renderer.
 * <p>
 * On every open the screen seeds pending-submission and overdue-borrow
 * notifications (idempotent via dedupe keys) so that existing DB data
 * surfaces even if the triggering event pre-dates this implementation.
 */
public final class LibrarianNotificationBoardScreen {

    private static final Logger LOG = Logger.getLogger(LibrarianNotificationBoardScreen.class.getName());

    private LibrarianNotificationBoardScreen() {}

    /** Category filter items shown in the ComboBox. */
    private static final List<Pair<String, String>> CATEGORY_FILTERS = List.of(
            new Pair<>("All categories",          "ALL"),
            new Pair<>("New book submissions",    NotificationService.CAT_NEW_SUBMISSION),
            new Pair<>("User registrations",      NotificationService.CAT_USER_REGISTERED),
            new Pair<>("Overdue borrows",          NotificationService.CAT_OVERDUE_BORROW),
            new Pair<>("Announcements",            NotificationService.CAT_ANNOUNCEMENT)
    );

    public static Scene create(Navigator navigator, User librarian) {

        // ── Seed notifications from current DB state ──────────────────────────
        try {
            NotificationService.syncPendingSubmissionNotifications();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to sync pending submission notifications", e);
        }
        try {
            NotificationService.syncOverdueBorrowNotificationsForLibrarians();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to sync overdue borrow notifications", e);
        }

        // ── Header ────────────────────────────────────────────────────────────
        Label titleLbl = new Label("Notification Board");
        titleLbl.getStyleClass().add("screen-title");

        Label subLbl = new Label("Librarian: " + librarian.getFullName());
        subLbl.setStyle("-fx-font-size: 12; -fx-text-fill: #666;");

        VBox headerBox = new VBox(4, titleLbl, subLbl);
        headerBox.setPadding(new Insets(20, 20, 0, 20));
        headerBox.setStyle("-fx-border-color: #f0f0f0; -fx-border-width: 0 0 1 0;");

        // ── Filter bar ────────────────────────────────────────────────────────
        ComboBox<Pair<String, String>> categoryBox =
                new ComboBox<>(FXCollections.observableArrayList(CATEGORY_FILTERS));
        categoryBox.setButtonCell(pairCell());
        categoryBox.setCellFactory(lv -> pairCell());
        categoryBox.getSelectionModel().selectFirst();
        categoryBox.setPrefWidth(210);

        TextField searchField = new TextField();
        searchField.setPromptText("Search title or body…");
        searchField.setMaxWidth(220);

        CheckBox showArchivedBox = new CheckBox("Show archived");
        CheckBox urgentOnlyBox   = new CheckBox("⚠ Urgent only");
        urgentOnlyBox.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold;");

        HBox filterBar = new HBox(10,
                new Label("Category:"), categoryBox,
                new Label("Search:"), searchField,
                showArchivedBox, urgentOnlyBox);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(10, 20, 6, 20));

        // ── Unread-count badge ────────────────────────────────────────────────
        Label unreadBadge = new Label();
        unreadBadge.setStyle("-fx-font-size: 11; -fx-text-fill: #c0392b; -fx-font-weight: bold;");
        refreshUnreadBadge(unreadBadge, librarian.getId());

        // ── Notification list ─────────────────────────────────────────────────
        ListView<AppNotification> list = new ListView<>();
        list.setCellFactory(lv -> NotificationListCellFactory.create());
        VBox.setVgrow(list, Priority.ALWAYS);

        Runnable refresh = () -> {
            try {
                Pair<String, String> sel = categoryBox.getSelectionModel().getSelectedItem();
                String cat = (sel == null) ? "ALL" : sel.getValue();
                List<AppNotification> rows = NotificationDao.findForUserFiltered(
                        librarian.getId(),
                        cat,
                        searchField.getText(),
                        showArchivedBox.isSelected()
                );
                // Apply urgent-only filter in-memory (priority field already loaded)
                if (urgentOnlyBox.isSelected()) {
                    rows = rows.stream()
                            .filter(NotificationService::isUrgentHighlight)
                            .collect(Collectors.toList());
                }
                list.setItems(FXCollections.observableArrayList(rows));
                refreshUnreadBadge(unreadBadge, librarian.getId());
            } catch (SQLException ex) {
                LOG.log(Level.WARNING, "Failed to load librarian notifications", ex);
                list.setItems(FXCollections.observableArrayList());
            }
        };
        refresh.run();

        categoryBox.setOnAction(e -> refresh.run());
        searchField.textProperty().addListener((obs, old, val) -> refresh.run());
        showArchivedBox.setOnAction(e -> refresh.run());
        urgentOnlyBox.setOnAction(e -> refresh.run());

        // ── Action buttons ────────────────────────────────────────────────────
        Button markReadBtn = new Button("Mark read");
        markReadBtn.setOnAction(e -> {
            AppNotification n = list.getSelectionModel().getSelectedItem();
            if (n == null) {
                new Alert(Alert.AlertType.INFORMATION, "Please select a notification first.").showAndWait();
                return;
            }
            try {
                NotificationDao.markRead(n.getId(), librarian.getId(), Instant.now().toString());
                refresh.run();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not mark notification as read.").showAndWait();
            }
        });

        Button markAllReadBtn = new Button("Mark all read");
        markAllReadBtn.setOnAction(e -> {
            try {
                int count = NotificationDao.markAllRead(librarian.getId(), Instant.now().toString());
                if (count == 0) {
                    new Alert(Alert.AlertType.INFORMATION, "No unread notifications.").showAndWait();
                }
                refresh.run();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not mark all notifications as read.").showAndWait();
            }
        });

        Button archiveBtn = new Button("Archive");
        archiveBtn.getStyleClass().add("secondary-button");
        archiveBtn.setOnAction(e -> {
            AppNotification n = list.getSelectionModel().getSelectedItem();
            if (n == null) {
                new Alert(Alert.AlertType.INFORMATION, "Please select a notification first.").showAndWait();
                return;
            }
            try {
                NotificationDao.archive(n.getId(), librarian.getId(), Instant.now().toString());
                refresh.run();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not archive notification.").showAndWait();
            }
        });

        Button refreshBtn = new Button("⟳ Refresh");
        refreshBtn.setOnAction(e -> {
            // Re-seed from DB, then repopulate list
            try { NotificationService.syncPendingSubmissionNotifications(); } catch (SQLException ignored) {}
            try { NotificationService.syncOverdueBorrowNotificationsForLibrarians(); } catch (SQLException ignored) {}
            refresh.run();
        });

        // ── Footer ────────────────────────────────────────────────────────────
        Button backBtn = new Button("← Back to Dashboard");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> navigator.showLibrarianApproval(librarian));

        // Spacer so unread badge sits on the right side of the action row
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actionRow = new HBox(10, markReadBtn, markAllReadBtn, archiveBtn, refreshBtn, spacer, unreadBadge);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        actionRow.setPadding(new Insets(0, 20, 0, 20));

        HBox footerBox = new HBox(backBtn);
        footerBox.setAlignment(Pos.CENTER_RIGHT);
        footerBox.setPadding(new Insets(15, 20, 15, 20));

        // ── Root layout ───────────────────────────────────────────────────────
        VBox topSection = new VBox(0, headerBox, filterBar, actionRow);

        BorderPane root = new BorderPane();
        root.setTop(topSection);
        root.setCenter(list);
        root.setBottom(footerBox);
        root.getStyleClass().add("app-root");
        BorderPane.setMargin(list, new Insets(6, 20, 0, 20));

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL css = LibrarianNotificationBoardScreen.class.getResource("/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        return scene;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Creates a ListCell that renders only the display name of a Pair. */
    private static ListCell<Pair<String, String>> pairCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Pair<String, String> item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getKey());
            }
        };
    }

    /** Updates the unread-count badge label. */
    private static void refreshUnreadBadge(Label badge, long userId) {
        try {
            int unread = NotificationDao.countUnread(userId);
            badge.setText(unread > 0 ? unread + " unread" : "All read");
        } catch (SQLException e) {
            badge.setText("");
        }
    }
}
