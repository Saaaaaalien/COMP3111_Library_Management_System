package org.example.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.example.domain.AppNotification;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Shared list cell for in-app notifications: red dot + left accent for unread; muted read rows.
 */
public final class NotificationListCellFactory {

    private NotificationListCellFactory() {}

    public static ListCell<AppNotification> create() {
        return new ListCell<>() {
            @Override
            protected void updateItem(AppNotification n, boolean empty) {
                super.updateItem(n, empty);
                if (empty || n == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                    return;
                }

                String ts = n.getCreatedAt();
                String when = ts;
                try {
                    when = Instant.parse(ts).atZone(ZoneId.systemDefault()).toLocalDateTime().toString();
                } catch (Exception ignored) {
                }

                boolean unread = !n.isRead();

                Circle dot = unread ? new Circle(6, Color.web("#e74c3c")) : null;

                Label catLbl = new Label("[" + n.getCategory() + "] ");
                catLbl.setStyle("-fx-text-fill: #7f8c8d;");

                Label titleLbl = new Label(n.getTitle());
                titleLbl.setStyle(unread
                        ? "-fx-font-weight: bold; -fx-text-fill: #2c3e50;"
                        : "-fx-font-weight: normal; -fx-text-fill: #616161;");

                HBox titleBox = new HBox(8, catLbl, titleLbl);
                titleBox.setAlignment(Pos.CENTER_LEFT);

                Label body = new Label(n.getBody());
                body.setWrapText(true);
                body.setStyle(unread ? "-fx-text-fill: #34495e;" : "-fx-text-fill: #757575;");

                Label meta = new Label(when + "  [P" + n.getPriority() + "]");
                meta.setStyle(unread ? "-fx-text-fill: #7f8c8d;" : "-fx-text-fill: #9e9e9e;");

                VBox v = new VBox(4, titleBox, body, meta);
                v.setMaxWidth(Double.MAX_VALUE);

                HBox h = dot != null ? new HBox(10, dot, v) : new HBox(10, v);
                HBox.setHgrow(v, Priority.ALWAYS);

                if (unread) {
                    h.setStyle("-fx-padding: 8; -fx-background-color: #f4f9ff; "
                            + "-fx-border-color: #e74c3c; -fx-border-width: 0 0 0 4; "
                            + "-fx-background-radius: 4;");
                    setStyle("-fx-background-color: #f4f9ff;");
                } else {
                    h.setStyle("-fx-padding: 8; -fx-background-color: #fafafa; "
                            + "-fx-border-color: #e0e0e0; -fx-border-width: 0 0 0 4; "
                            + "-fx-background-radius: 4;");
                    setStyle("-fx-background-color: #fafafa;");
                }

                setText(null);
                setGraphic(h);
            }
        };
    }
}
