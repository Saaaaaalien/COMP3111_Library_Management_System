package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
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

        Label hintLbl = new Label("Choose to login with an existing account or register a new one.");
        hintLbl.setWrapText(true);
        hintLbl.setMaxWidth(280);
        hintLbl.getStyleClass().add("login-hint");


        VBox content = new VBox(18, title, hintLbl, loginBtn, registerBtn);
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(440);
        content.getStyleClass().add("content-card");

        BorderPane root = new BorderPane();
        root.setCenter(content);
        BorderPane.setAlignment(content, Pos.CENTER);
        root.setPadding(new Insets(40));
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL cssResource = LibrarianEntryScreen.class.getResource("/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }
        return scene;
    }
}