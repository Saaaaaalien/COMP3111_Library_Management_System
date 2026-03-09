package org.example.ui;

import java.sql.SQLException;

import org.example.app.Navigator;
import org.example.service.AuthService;
import org.example.util.ValidationException;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 * Librarian registration: username, full name, password, and optional employee ID.
 * Role is implicitly LIBRARIAN.
 */
public final class LibrarianRegisterScreen {
    private LibrarianRegisterScreen() {}

    public static Scene create(Navigator navigator) {
        Label title = new Label("Register (Librarian)");
        title.getStyleClass().add("screen-title");

        Label usernameLabel = new Label("Username:");
        TextField usernameField = new TextField();
        usernameField.setPromptText("3–50 characters, letters/numbers/underscore");
        usernameField.setMaxWidth(280);

        Label fullNameLabel = new Label("Full Name:");
        TextField fullNameField = new TextField();
        fullNameField.setMaxWidth(280);

        Label employeeIdLabel = new Label("Employee ID (optional):");
        TextField employeeIdField = new TextField();
        employeeIdField.setMaxWidth(280);

        Label passwordLabel = new Label("Password:");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Minimum 8 characters");
        passwordField.setMaxWidth(280);

        Label strengthLbl = new Label("Empty");
        strengthLbl.getStyleClass().add("password-strength");
        passwordField.textProperty().addListener((obs, prev, newVal) -> {
            String strength = org.example.util.Validators.getPasswordStrengthLabel(newVal);
            strengthLbl.setText(strength.isEmpty() ? "Empty" : "Strength: " + strength);
        });

        Button registerBtn = new Button("Register");
        registerBtn.getStyleClass().add("primary-button");
        registerBtn.setOnAction(e -> {
            String username = usernameField.getText();
            String fullName = fullNameField.getText();
            String employeeId = employeeIdField.getText();
            String password = passwordField.getText();
            try {
                AuthService.registerLibrarian(username, fullName, password, employeeId);
                showAlert(Alert.AlertType.INFORMATION, "Registration successful",
                        "You can now log in with your librarian username and password.");
                navigator.showLibrarianPortal();
            } catch (ValidationException ex) {
                showAlert(Alert.AlertType.ERROR, "Registration failed", ex.getMessage());
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Registration failed", "A database error occurred. Please try again.");
            }
        });

        Button backBtn = new Button("Back");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> navigator.showLibrarianPortal());

        VBox usernameBox = new VBox(5, usernameLabel, usernameField);
        usernameBox.setAlignment(Pos.CENTER);

        VBox fullNameBox = new VBox(5, fullNameLabel, fullNameField);
        fullNameBox.setAlignment(Pos.CENTER);

        VBox employeeIdBox = new VBox(5, employeeIdLabel, employeeIdField);
        employeeIdBox.setAlignment(Pos.CENTER);

        VBox passwordBox = new VBox(5, passwordLabel, passwordField, strengthLbl);
        passwordBox.setAlignment(Pos.CENTER);

        VBox form = new VBox(12, usernameBox, fullNameBox, employeeIdBox, passwordBox);
        form.setAlignment(Pos.CENTER);

        VBox content = new VBox(18, title, form, registerBtn, backBtn);
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(440);
        content.getStyleClass().add("content-card");

        BorderPane root = new BorderPane();
        root.setCenter(content);
        BorderPane.setAlignment(content, Pos.CENTER);
        root.setPadding(new Insets(40));
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL cssResource = LibrarianRegisterScreen.class.getResource("/app.css");
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
