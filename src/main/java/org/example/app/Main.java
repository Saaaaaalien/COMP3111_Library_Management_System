package org.example.app;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.service.BorrowService;
import org.example.service.NotificationService;

import java.sql.SQLException;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        try {
            org.example.db.Database.getConnection();
            BorrowService.processDueReturns();
            NotificationService.syncDueReminders();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        Navigator navigator = new Navigator(stage);
        SessionService.tryRestore(navigator);

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