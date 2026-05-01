package org.example.app;

import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * Shared helper to preserve main stage state across scene switches.
 */
public final class WindowStateKeeper {

    private WindowStateKeeper() {
    }

    public static Snapshot capture(Stage stage) {
        if (stage == null) {
            return Snapshot.empty();
        }
        return new Snapshot(
                stage.isShowing(),
                stage.isFullScreen(),
                stage.isMaximized(),
                stage.getWidth(),
                stage.getHeight(),
                stage.getX(),
                stage.getY()
        );
    }

    public static void applyAfterSceneSwap(Stage stage, Snapshot snapshot) {
        if (stage == null || snapshot == null) {
            return;
        }
        if (!snapshot.wasShowing()) {
            stage.show();
            return;
        }
        applyOnce(stage, snapshot);
        Platform.runLater(() -> applyOnce(stage, snapshot));
    }

    private static void applyOnce(Stage stage, Snapshot snapshot) {
        if (snapshot.wasFullScreen()) {
            stage.setMaximized(false);
            stage.setFullScreen(true);
            return;
        }
        if (snapshot.wasMaximized()) {
            stage.setFullScreen(false);
            stage.setMaximized(true);
            return;
        }
        stage.setFullScreen(false);
        stage.setMaximized(false);
        if (snapshot.width() > 0 && snapshot.height() > 0) {
            stage.setWidth(snapshot.width());
            stage.setHeight(snapshot.height());
        }
        if (Double.isFinite(snapshot.x()) && Double.isFinite(snapshot.y())) {
            stage.setX(snapshot.x());
            stage.setY(snapshot.y());
        }
    }

    public record Snapshot(boolean wasShowing,
                           boolean wasFullScreen,
                           boolean wasMaximized,
                           double width,
                           double height,
                           double x,
                           double y) {
        public static Snapshot empty() {
            return new Snapshot(false, false, false, -1, -1, Double.NaN, Double.NaN);
        }
    }
}
