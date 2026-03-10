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
 * Librarian registration: username, first name, last name, password (with strength meter), optional employee ID.
 * Role is implicitly LIBRARIAN. Same rules and layout as Student/Staff.
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

        Label firstNameLabel = new Label("First Name:");
        TextField firstNameField = new TextField();
        firstNameField.setMaxWidth(280);

        Label lastNameLabel = new Label("Last Name:");
        TextField lastNameField = new TextField();
        lastNameField.setMaxWidth(280);

        Label employeeIdLabel = new Label("Employee ID (optional):");
        TextField employeeIdField = new TextField();
        employeeIdField.setMaxWidth(280);

        Label passwordLabel = new Label("Password:");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Min 8 chars, 1 uppercase, 1 number, 1 special character");
        passwordField.setMaxWidth(280);

        Label strengthLbl = new Label("");
        strengthLbl.getStyleClass().add("password-strength");
        passwordField.textProperty().addListener((obs, prev, newVal) -> {
            String strength = org.example.util.Validators.getPasswordStrengthLabel(newVal);
            strengthLbl.setText(strength.isEmpty() ? "" : "Strength: " + strength);
        });

        Button registerBtn = new Button("Register");
        registerBtn.getStyleClass().add("primary-button");
        registerBtn.setOnAction(e -> {
            String username = usernameField.getText();
            String firstName = firstNameField.getText();
            String lastName = lastNameField.getText();
            String employeeId = employeeIdField.getText();
            String password = passwordField.getText();
            try {
                AuthService.registerLibrarian(username, firstName, lastName, password, employeeId);
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

        VBox firstNameBox = new VBox(5, firstNameLabel, firstNameField);
        firstNameBox.setAlignment(Pos.CENTER);

        VBox lastNameBox = new VBox(5, lastNameLabel, lastNameField);
        lastNameBox.setAlignment(Pos.CENTER);

        VBox employeeIdBox = new VBox(5, employeeIdLabel, employeeIdField);
        employeeIdBox.setAlignment(Pos.CENTER);

        VBox passwordBox = new VBox(5, passwordLabel, passwordField, strengthLbl);
        passwordBox.setAlignment(Pos.CENTER);

        VBox form = new VBox(12, usernameBox, firstNameBox, lastNameBox, employeeIdBox, passwordBox);
        form.setAlignment(Pos.CENTER);

        VBox content = new VBox(18, title, form, registerBtn, backBtn);
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(520);
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
