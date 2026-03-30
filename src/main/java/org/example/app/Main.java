package org.example.app;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.service.BorrowService;
import org.example.service.NotificationService;
import org.example.util.ResourceSetup;

import java.sql.SQLException;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        // Ensure resources are set up
        ResourceSetup.ensureIconExists();
        
        try {
            org.example.db.Database.getConnection();
            BorrowService.processDueReturns();
            NotificationService.syncDueReminders();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        Navigator navigator = new Navigator(stage);
        SessionService.RestoreResult restore = SessionService.tryRestore(navigator);
        if (restore.restored() || restore.fallbackToWelcome()) {
            Platform.runLater(() -> {
                Alert.AlertType type = restore.restored() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING;
                Alert alert = new Alert(type);
                alert.setTitle("Session Recovery");
                alert.setHeaderText(null);
                alert.setContentText(restore.message());
                alert.showAndWait();
            });
        }

        Timeline maintenance = new Timeline(new KeyFrame(Duration.minutes(3), ev -> {
            try {
                BorrowService.processDueReturns();
                NotificationService.syncDueReminders();
            } catch (SQLException ignored) {
            }
        }));
        maintenance.setCycleCount(Timeline.INDEFINITE);
        maintenance.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}