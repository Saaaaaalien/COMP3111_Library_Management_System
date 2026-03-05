package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.example.app.Navigator;
import org.example.util.*;

/**
 * Task2: Author portal entry: placeholder for Login and Register (to be implemented).
 */
public final class AuthorEntryScreen {

    private AuthorEntryScreen() {}

    public static Scene create(Navigator navigator) {
        Label title = new Label("Author Portal");
        title.getStyleClass().add("screen-title");

        Button loginBtn = new Button("Login");
        loginBtn.getStyleClass().add("portal-button");
        loginBtn.setOnAction(e -> navigator.showAuthorLogin());

        Button registerBtn = new Button("Register");
        registerBtn.getStyleClass().add("portal-button");
        registerBtn.setOnAction(e -> navigator.showAuthorRegister());

        Button backBtn = new Button("Back to Welcome");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> navigator.showWelcome());

        VBox root = new VBox(20, title, loginBtn, registerBtn, backBtn);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL cssResource = AuthorEntryScreen.class.getResource("/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }
        return scene;
    }
}