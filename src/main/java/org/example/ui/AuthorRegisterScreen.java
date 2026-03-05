package org.example.ui;


import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.example.app.Navigator;
import org.example.domain.Role;
import org.example.service.AuthService;
import org.example.util.ValidationException;

import java.sql.SQLException;


public final class AuthorRegisterScreen {
    private AuthorRegisterScreen() {}

    public static Scene create(Navigator navigator) {
        Label title = new Label("Register (Author)");
        title.getStyleClass().add("screen-title");

        Label usernameLabel = new Label("Username:");
        TextField usernameField = new TextField();
        usernameField.setPromptText("3–50 characters, letters/numbers/underscore");
        usernameField.setMaxWidth(280);

        Label fullNameLabel = new Label("Full Name:");
        TextField fullNameField = new TextField();
        fullNameField.setMaxWidth(280);

        Label passwordLabel = new Label("Password:");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("At least 8 characters");
        passwordField.setMaxWidth(280);

        Label bioLabel = new Label("Bio:");
        TextField bioField = new TextField();

        ComboBox<Role> roleCombo = new ComboBox<>();
        roleCombo.getItems().setAll(Role.AUTHOR);
        roleCombo.setValue(Role.AUTHOR);
        roleCombo.setMaxWidth(200);

        Button registerBtn = new Button("Register");
        registerBtn.setOnAction(e -> {
            String username = usernameField.getText();
            String fullName = fullNameField.getText();
            String password = passwordField.getText();
            String bio = bioField.getText();
            try {
                AuthService.registerAuthor(username, fullName, password, bio);
                showAlert(Alert.AlertType.INFORMATION, "Registration successful", "You can now log in with your username and password.");
                navigator.showAuthorLogin();
            } catch (ValidationException ex) {
                showAlert(Alert.AlertType.ERROR, "Registration failed", ex.getMessage());
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Registration failed", "A database error occurred. Please try again.");
            }
        });

        Button backBtn = new Button("Back");
        backBtn.setOnAction(e -> navigator.showAuthorPortal());

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(usernameLabel, 0, 0);
        form.add(usernameField, 1, 0);
        form.add(fullNameLabel, 0, 1);
        form.add(fullNameField, 1, 1);
        form.add(passwordLabel, 0, 2);
        form.add(passwordField, 1, 2);
        form.add(bioLabel, 0, 3);
        form.add(roleCombo, 1, 3);

        VBox root = new VBox(20, title, form, registerBtn, backBtn);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL cssResource = StudentStaffRegisterScreen.class.getResource("/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }
        return scene;
    }

    private static void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
