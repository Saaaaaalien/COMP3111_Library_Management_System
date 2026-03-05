package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.app.Navigator;
import org.example.domain.Role;
import org.example.service.AuthService;
import org.example.util.ValidationException;
import org.example.util.Validators;

import java.sql.SQLException;

/**
 * Student/Staff registration: username, first name, last name, password (with strength meter), role (radio buttons).
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

        Label firstNameLbl = new Label("First Name:");
        TextField firstNameField = new TextField();
        firstNameField.setMaxWidth(280);

        Label lastNameLbl = new Label("Last Name:");
        TextField lastNameField = new TextField();
        lastNameField.setMaxWidth(280);

        Label passwordLbl = new Label("Password:");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Min 8 chars, 1 uppercase, 1 number, 1 special character");
        passwordField.setMaxWidth(280);

        Label strengthLbl = new Label("");
        strengthLbl.getStyleClass().add("password-strength");
        passwordField.textProperty().addListener((obs, prev, newVal) -> {
            String strength = Validators.getPasswordStrengthLabel(newVal);
            strengthLbl.setText(strength.isEmpty() ? "" : "Strength: " + strength);
        });

        Label roleLbl = new Label("Role:");
        ToggleGroup roleGroup = new ToggleGroup();
        RadioButton studentRadio = new RadioButton("Student");
        studentRadio.setToggleGroup(roleGroup);
        studentRadio.setSelected(true);
        RadioButton staffRadio = new RadioButton("Staff");
        staffRadio.setToggleGroup(roleGroup);
        HBox roleBox = new HBox(10, studentRadio, staffRadio);
        roleBox.setAlignment(Pos.CENTER);

        Button registerBtn = new Button("Register");
        registerBtn.getStyleClass().add("primary-button");
        registerBtn.setOnAction(e -> {
            String username = usernameField.getText();
            String firstName = firstNameField.getText();
            String lastName = lastNameField.getText();
            String password = passwordField.getText();
            Role role = studentRadio.isSelected() ? Role.STUDENT : Role.STAFF;
            try {
                AuthService.registerStudentStaff(username, firstName, lastName, password, role);
                showAlert(Alert.AlertType.INFORMATION, "Registration successful", "You can now log in with your username and password.");
                navigator.showStudentStaffLogin();
            } catch (ValidationException ex) {
                showAlert(Alert.AlertType.ERROR, "Registration failed", ex.getMessage());
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Registration failed", "A database error occurred. Please try again.");
            }
        });

        Button backBtn = new Button("Back");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> navigator.showStudentStaffPortal());

        VBox usernameBox = new VBox(5, usernameLbl, usernameField);
        usernameBox.setAlignment(Pos.CENTER);

        VBox firstNameBox = new VBox(5, firstNameLbl, firstNameField);
        firstNameBox.setAlignment(Pos.CENTER);

        VBox lastNameBox = new VBox(5, lastNameLbl, lastNameField);
        lastNameBox.setAlignment(Pos.CENTER);

        VBox passwordBox = new VBox(5, passwordLbl, passwordField, strengthLbl);
        passwordBox.setAlignment(Pos.CENTER);

        VBox roleBoxContainer = new VBox(5, roleLbl, roleBox);
        roleBoxContainer.setAlignment(Pos.CENTER);

        VBox form = new VBox(12, usernameBox, firstNameBox, lastNameBox, passwordBox, roleBoxContainer);
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
