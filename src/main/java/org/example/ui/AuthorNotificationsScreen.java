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
        Button backBtn = new Button("Back");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setPrefWidth(140);
        backBtn.setOnAction(e -> navigator.showAuthorDashboard(user));



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
                    setGraphic(null);
                } else {
                    String ts = n.getCreatedAt();
                    String when = ts;
                    try {
                        when = Instant.parse(ts).atZone(ZoneId.systemDefault()).toLocalDateTime().toString();
                    } catch (Exception ignored) {}

                    boolean unread = !n.isRead();
                    javafx.scene.shape.Circle dot = null;
                    if (unread) {
                        dot = new javafx.scene.shape.Circle(6, javafx.scene.paint.Color.web("#e74c3c"));
                    }

                    Label title = new Label("[" + n.getCategory() + "] " + n.getTitle());
                    if (unread) {
                        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50;");
                    } else {
                        title.setStyle("-fx-text-fill: #2c3e50;");
                    }
                    Label body = new Label(n.getBody());
                    body.setWrapText(true);
                    Label meta = new Label(when + "  [P" + n.getPriority() + "]");

                    VBox v = new VBox(4, title, body, meta);
                    v.setMaxWidth(Double.MAX_VALUE);

                    HBox h;
                    if (dot != null) {
                        h = new HBox(10, dot, v);
                    } else {
                        h = new HBox(10, v);
                    }
                    h.setStyle("-fx-padding: 8;");
                    javafx.scene.layout.HBox.setHgrow(v, javafx.scene.layout.Priority.ALWAYS);

                    setText(null);
                    setGraphic(h);
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


        HBox back = new HBox(5, backBtn);
        HBox filters = new HBox(5, new Label("Category:"), category, search, showArchived);
        HBox actions = new HBox(5, readBtn, archBtn);
        actions.setPadding(new Insets(16, 0, 0, 0));

        VBox listWrapper = new VBox(list);
        listWrapper.setPadding(new Insets(16, 0, 0, 0));
        javafx.scene.layout.VBox.setVgrow(list, javafx.scene.layout.Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setTop(new VBox(8, back, title, filters, actions));
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
