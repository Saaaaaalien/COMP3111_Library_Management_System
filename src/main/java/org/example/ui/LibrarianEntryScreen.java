package org.example.ui;

import org.example.app.Navigator;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 * Librarian portal entry: choose to login or register.
 */
public final class LibrarianEntryScreen {

    private LibrarianEntryScreen() {}

    public static Scene create(Navigator navigator) {
        Label title = new Label("Librarian Portal");
        title.getStyleClass().add("screen-title");

        Button loginBtn = new Button("Login");
        loginBtn.getStyleClass().add("primary-button");
        loginBtn.setMinWidth(150);
        loginBtn.setOnAction(e -> navigator.showLibrarianLogin());

        Button registerBtn = new Button("Register");
        registerBtn.getStyleClass().add("primary-button");
        registerBtn.setMinWidth(150);
        registerBtn.setOnAction(e -> navigator.showLibrarianRegister());

        VBox buttonBox = new VBox(10, loginBtn, registerBtn);
        buttonBox.setAlignment(Pos.CENTER);

        Label hintLbl = new Label("Choose to login with an existing account or register a new one.");
        hintLbl.setWrapText(true);
        hintLbl.setMaxWidth(280);
        hintLbl.getStyleClass().add("login-hint");

        VBox content = new VBox(18, title, hintLbl, buttonBox);
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
