package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.example.app.Navigator;
import org.example.domain.User;
import org.example.service.AuthException;
import org.example.service.AuthService;

import java.sql.SQLException;

/**
 * Student/Staff login: username and password, then navigate to Available Books.
 */
public final class StudentStaffLoginScreen {

    private StudentStaffLoginScreen() {}

    public static Scene create(Navigator navigator) {
        Label title = new Label("Login (Student / Staff)");
        title.getStyleClass().add("screen-title");

        Label usernameLbl = new Label("Username:");
        TextField usernameField = new TextField();
        usernameField.setMaxWidth(280);

        Label passwordLbl = new Label("Password:");
        PasswordField passwordField = new PasswordField();
        passwordField.setMaxWidth(280);

        Button loginBtn = new Button("Login");
        loginBtn.setOnAction(e -> {
            String username = usernameField.getText();
            String password = passwordField.getText();
            try {
                User user = AuthService.login(username, password);
                if (user.getRole() != org.example.domain.Role.STUDENT && user.getRole() != org.example.domain.Role.STAFF) {
                    showAlert(Alert.AlertType.ERROR, "Login failed", "This portal is for students and staff only.");
                    return;
                }
                navigator.showAvailableBooks(user);
            } catch (AuthException ex) {
                showAlert(Alert.AlertType.ERROR, "Login failed", ex.getMessage());
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Login failed", "A database error occurred. Please try again.");
            }
        });

        Button backBtn = new Button("Back");
        backBtn.setOnAction(e -> navigator.showStudentStaffPortal());

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(usernameLbl, 0, 0);
        form.add(usernameField, 1, 0);
        form.add(passwordLbl, 0, 1);
        form.add(passwordField, 1, 1);

        VBox root = new VBox(20, title, form, loginBtn, backBtn);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL cssResource = StudentStaffLoginScreen.class.getResource("/app.css");
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
