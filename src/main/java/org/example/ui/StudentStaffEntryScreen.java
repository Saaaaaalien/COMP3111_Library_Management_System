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
 */
public final class StudentStaffEntryScreen {

    private StudentStaffEntryScreen() {}

    public static Scene create(Navigator navigator) {
        Label title = new Label("Student / Staff Portal");
        title.getStyleClass().add("screen-title");

        Button loginBtn = new Button("Login");
        loginBtn.getStyleClass().add("portal-button");
        loginBtn.setOnAction(e -> navigator.showStudentStaffLogin());

        Button registerBtn = new Button("Register");
        registerBtn.getStyleClass().add("portal-button");
        registerBtn.setOnAction(e -> navigator.showStudentStaffRegister());

        Button backBtn = new Button("Back to Welcome");
        backBtn.setOnAction(e -> navigator.showWelcome());

        VBox root = new VBox(20, title, loginBtn, registerBtn, backBtn);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL cssResource = StudentStaffEntryScreen.class.getResource("/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }
        return scene;
    }
}
