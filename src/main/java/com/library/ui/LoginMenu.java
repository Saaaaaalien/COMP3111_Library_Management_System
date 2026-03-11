package com.library.ui;

import com.library.model.User;
import com.library.service.UserService;
import com.library.utils.PasswordValidator;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

public class LoginMenu implements Initializable{
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<String> roleComboBox;
    @FXML private Label messageLabel;
    @FXML private Button loginButton;
    @FXML private Button registerButton;

    // Registration
    @FXML private TextField regUsernameField;
    @FXML private TextField regFullNameField;
    @FXML private PasswordField regPasswordField;
    @FXML private ComboBox<String> regRoleComboBox;
    @FXML private Label bioLabel;
    @FXML private TextArea bioArea;
    @FXML private Label employeeIdLabel;
    @FXML private TextField employeeIdField;
    @FXML private Label passwordHintLabel;
    @FXML private Label regMessageLabel;

    private UserService userService;
    private Stage primaryStage;

    public LoginMenu() {
        userService = new UserService();
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        roleComboBox.getItems().addAll("Student/Staff", "Author", "Librarian");
        roleComboBox.setValue("Student/Staff");

        regRoleComboBox.getItems().addAll("Student/Staff", "Author", "Librarian");
        regRoleComboBox.setValue("Student/Staff");
        regRoleComboBox.setOnAction(e -> handleRoleSelection());
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String role = roleComboBox.getValue();

        if (username.isEmpty() || password.isEmpty()) {
            messageLabel.setText("Please enter username and password");
            return;
        }

        Optional<User> userOpt = userService.login(username, password);

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // Verify role matches
            String expectedRole = convertRoleToModel(role);
            if (!user.getRole().equals(expectedRole)) {
                messageLabel.setText("Invalid role for this user");
                return;
            }

            // Navigate to appropriate dashboard
            navigateToDashboard(user);
        } else {
            messageLabel.setText("Invalid username or password");
        }
    }

    private String convertRoleToModel(String displayRole) {
        switch (displayRole) {
            case "Student/Staff": return "STUDENT_STAFF";
            case "Author": return "AUTHOR";
            case "Librarian": return "LIBRARIAN";
            default: return "";
        }
    }

    private void navigateToDashboard(User user) {
        try {
            FXMLLoader loader;
            Parent root;

            switch (user.getRole()) {
                case "STUDENT_STAFF":
                    loader = new FXMLLoader(getClass().getResource("/fxml/StudentStaffDashboard.fxml"));
                    root = loader.load();
                    StudentStaffMenu studentMenu = loader.getController();
                    studentMenu.setCurrentUser(user);
                    studentMenu.setPrimaryStage(primaryStage);
                    break;

                case "AUTHOR":
                    loader = new FXMLLoader(getClass().getResource("/fxml/AuthorDashboard.fxml"));
                    root = loader.load();
                    AuthorMenu authorMenu = loader.getController();
                    authorMenu.setCurrentUser(user);
                    authorMenu.setPrimaryStage(primaryStage);
                    break;

                case "LIBRARIAN":
                    loader = new FXMLLoader(getClass().getResource("/fxml/LibrarianDashboard.fxml"));
                    root = loader.load();
                    LibrarianMenu librarianController = loader.getController();
                    librarianController.setCurrentUser(user);
                    librarianController.setPrimaryStage(primaryStage);
                    break;

                default:
                    messageLabel.setText("Unknown user role");
                    return;
            }

            Scene scene = new Scene(root);
            primaryStage.setScene(scene);
            primaryStage.setTitle("Library Management System - " + user.getRole());
            primaryStage.show();

        } catch (IOException e) {
            e.printStackTrace();
            messageLabel.setText("Error loading dashboard");
        }
    }

    @FXML
    private void handleRegister() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Register.fxml"));
            Parent root = loader.load();

            LoginMenu registerMenu = loader.getController();
            registerMenu.setPrimaryStage(primaryStage);

            Scene scene = new Scene(root);
            primaryStage.setScene(scene);
            primaryStage.setTitle("Library Management System - Register");
            primaryStage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleRoleSelection() {
        String role = regRoleComboBox.getValue();

        bioLabel.setVisible(false);
        bioArea.setVisible(false);
        employeeIdLabel.setVisible(false);
        employeeIdField.setVisible(false);
        bioLabel.setManaged(false);
        bioArea.setManaged(false);
        employeeIdLabel.setManaged(false);
        employeeIdField.setManaged(false);

        if ("Author".equals(role)) {
            bioLabel.setVisible(true);
            bioArea.setVisible(true);
            bioLabel.setManaged(true);
            bioArea.setManaged(true);
        } else if ("Librarian".equals(role)) {
            employeeIdLabel.setVisible(true);
            employeeIdField.setVisible(true);
            employeeIdLabel.setManaged(true);
            employeeIdField.setManaged(true);
        }
    }

    @FXML
    private void handleRegisterSubmit() {
        String username = regUsernameField.getText().trim();
        String fullName = regFullNameField.getText().trim();
        String password = regPasswordField.getText();
        String role = regRoleComboBox.getValue();

        // Validate inputs
        if (username.isEmpty() || fullName.isEmpty() || password.isEmpty()) {
            regMessageLabel.setText("All fields are required");
            return;
        }

        // Validate password
        if (!PasswordValidator.isValid(password)) {
            regMessageLabel.setText(PasswordValidator.getRequirements());
            return;
        }
        boolean success = false;

        switch (role) {
            case "Student/Staff":
                success = userService.registerStudentStaff(username, fullName, password);
                break;

            case "Author":
                String bio = bioArea.getText();
                success = userService.registerAuthor(username, fullName, password, bio);
                break;

            case "Librarian":
                String employeeId = employeeIdField.getText();
                success = userService.registerLibrarian(username, fullName, password, employeeId);
                break;
        }

        if (success) {
            regMessageLabel.setText("Registration successful! You can now login.");
            regMessageLabel.setStyle("-fx-text-fill: green;");

            // Clear fields
            regUsernameField.clear();
            regFullNameField.clear();
            regPasswordField.clear();
            bioArea.clear();
            employeeIdField.clear();
        } else {
            regMessageLabel.setText("Registration failed. Username might already exist.");
        }
    }

    @FXML
    private void handleBackToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
            Parent root = loader.load();

            LoginMenu loginMenu = loader.getController();
            loginMenu.setPrimaryStage(primaryStage);

            Scene scene = new Scene(root);
            primaryStage.setScene(scene);
            primaryStage.setTitle("Library Management System - Login");
            primaryStage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
