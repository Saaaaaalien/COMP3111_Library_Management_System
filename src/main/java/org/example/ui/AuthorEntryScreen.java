package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.example.app.Navigator;

/**
 * Author portal entry: placeholder for Login and Register (to be implemented).
 */
public final class AuthorEntryScreen {

    private AuthorEntryScreen() {}

    public static Scene create(Navigator navigator) {
        Label title = new Label("Author Portal");
        title.getStyleClass().add("screen-title");

        Button backBtn = new Button("Back to Welcome");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> navigator.showWelcome());

        VBox content = new VBox(20, title, backBtn);
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("content-card");

        VBox root = new VBox(content);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL cssResource = AuthorEntryScreen.class.getResource("/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }
        return scene;
    }
}
