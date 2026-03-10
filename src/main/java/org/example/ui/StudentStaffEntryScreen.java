package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.example.app.Navigator;

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

        Button backBtn = new Button("Back to Welcome");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> navigator.showWelcome());

        VBox content = new VBox(20, title, loginBtn, registerBtn, backBtn);
        content.setAlignment(Pos.CENTER);

        VBox root = new VBox(content);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL cssResource = StudentStaffEntryScreen.class.getResource("/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }
        return scene;
    }
}
