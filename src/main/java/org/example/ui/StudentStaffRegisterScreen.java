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

/**
 * Student/Staff registration: username, full name, password, role.
 */
public final class StudentStaffRegisterScreen {

    private StudentStaffRegisterScreen() {}

    public static Scene create(Navigator navigator) {
        Label title = new Label("Register (Student / Staff)");
        title.getStyleClass().add("screen-title");

        Label usernameLbl = new Label("Username:");
        TextField usernameField = new TextField();
        usernameField.setPromptText("3–50 characters, letters/numbers/underscore");
        usernameField.setMaxWidth(280);

        Label fullNameLbl = new Label("Full Name:");
        TextField fullNameField = new TextField();
        fullNameField.setMaxWidth(280);

        Label passwordLbl = new Label("Password:");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("At least 8 characters");
        passwordField.setMaxWidth(280);

        Label roleLbl = new Label("Role:");
        ComboBox<Role> roleCombo = new ComboBox<>();
        roleCombo.getItems().setAll(Role.STUDENT, Role.STAFF);
        roleCombo.setValue(Role.STUDENT);
        roleCombo.setMaxWidth(200);

        Button registerBtn = new Button("Register");
        registerBtn.setOnAction(e -> {
            String username = usernameField.getText();
            String fullName = fullNameField.getText();
            String password = passwordField.getText();
            Role role = roleCombo.getValue();
            try {
                AuthService.registerStudentStaff(username, fullName, password, role);
                showAlert(Alert.AlertType.INFORMATION, "Registration successful", "You can now log in with your username and password.");
                navigator.showStudentStaffLogin();
            } catch (ValidationException ex) {
                showAlert(Alert.AlertType.ERROR, "Registration failed", ex.getMessage());
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Registration failed", "A database error occurred. Please try again.");
            }
        });

        Button backBtn = new Button("Back");
        backBtn.setOnAction(e -> navigator.showStudentStaffPortal());

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(usernameLbl, 0, 0);
        form.add(usernameField, 1, 0);
        form.add(fullNameLbl, 0, 1);
        form.add(fullNameField, 1, 1);
        form.add(passwordLbl, 0, 2);
        form.add(passwordField, 1, 2);
        form.add(roleLbl, 0, 3);
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
