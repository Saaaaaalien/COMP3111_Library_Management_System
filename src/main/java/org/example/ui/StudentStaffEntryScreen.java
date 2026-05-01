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
 * Student/Staff portal entry: Login or Register.
 * <p>
 * First screen after choosing "Student / Staff" from the welcome screen; offers
 * buttons to go to {@link StudentStaffLoginScreen} or {@link StudentStaffRegisterScreen},
 * or back to the welcome screen.
 */
public final class StudentStaffEntryScreen {

    private StudentStaffEntryScreen() {}

    /**
     * Builds the Student/Staff entry scene with Login, Register, and Back buttons.
     *
     * @param navigator application navigator for screen transitions
     * @return the configured JavaFX {@link javafx.scene.Scene}
     */
    public static Scene create(Navigator navigator) {
        Label title = new Label("Student / Staff Portal");
        title.getStyleClass().add("screen-title");

        Button loginBtn = new Button("Login");
        loginBtn.getStyleClass().addAll("portal-button", "primary-button");
        loginBtn.setOnAction(e -> navigator.showStudentStaffLogin());

        Button registerBtn = new Button("Register");
        registerBtn.getStyleClass().addAll("portal-button", "primary-button");
        registerBtn.setOnAction(e -> navigator.showStudentStaffRegister());
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
