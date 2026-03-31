package org.example.ui;

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
import javafx.scene.layout.VBox;
import org.example.app.Navigator;
import org.example.db.NotificationDao;
import org.example.domain.AppNotification;
import org.example.domain.User;
import org.example.service.NotificationService;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Author notification board with category filter and search.
 */
public final class AuthorNotificationsScreen {

    private AuthorNotificationsScreen() {}

    public static Scene create(Navigator navigator, User user) {
        Label title = new Label("Author notifications");
        title.getStyleClass().add("screen-title");

        ComboBox<String> category = new ComboBox<>(FXCollections.observableArrayList(
                "ALL",
                NotificationService.CAT_AUTHOR_APPROVED,
                NotificationService.CAT_AUTHOR_REJECTED,
                NotificationService.CAT_ANNOUNCEMENT
        ));
        category.getSelectionModel().selectFirst();

        TextField search = new TextField();
        search.setPromptText("Search...");
        search.setMaxWidth(200);
        CheckBox showArchived = new CheckBox("Show archived");

        ListView<AppNotification> list = new ListView<>();
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(AppNotification n, boolean empty) {
                super.updateItem(n, empty);
                if (empty || n == null) {
                    setText(null);
                } else {
                    String ts = n.getCreatedAt();
                    String when = ts;
                    try {
                        when = Instant.parse(ts).atZone(ZoneId.systemDefault()).toLocalDateTime().toString();
                    } catch (Exception ignored) {}
                    String status = n.isRead() ? "" : " (NEW)";
                    setText("[" + n.getCategory() + "]" + status + " \n" + n.getTitle() + "\n" + n.getBody() + "\n" + when + "  [P" + n.getPriority() + "]");
                }
            }
        });

        Runnable refresh = () -> {
            try {
                String cat = category.getSelectionModel().getSelectedItem();
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

        Button archBtn = new Button("Archive");
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

        Button backBtn = new Button("Back");
        backBtn.setOnAction(e -> navigator.showAuthorDashboard(user));

        HBox filters = new HBox(10, new Label("Category:"), category, search, showArchived);
        HBox actions = new HBox(10, readBtn, archBtn, backBtn);

        BorderPane root = new BorderPane();
        root.setTop(new VBox(8, title, filters, actions));
        root.setCenter(list);
        root.setPadding(new Insets(12));
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        var css = AuthorNotificationsScreen.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }
}
