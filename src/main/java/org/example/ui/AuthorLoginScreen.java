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

public final class AuthorLoginScreen {
    private AuthorLoginScreen() {}

    public static Scene create(Navigator navigator) {
        Label title = new Label("Login (Author)");
        title.getStyleClass().add("screen-title");

        Label usernameLabel = new Label("Username:");
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
                if (user.getRole() != org.example.domain.Role.AUTHOR) {
                    showLoginError("This portal is for author only.");
                    return;
                }
                navigator.showPublishBooks(user);
            } catch (AuthException ex) {
                showLoginError(ex.getMessage());
            } catch (SQLException ex) {
                showLoginError("A database error occurred. Please try again.");
            }
        });

        Button backBtn = new Button("Back");
        backBtn.setOnAction(e -> navigator.showAuthorPortal());

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(usernameLabel, 0, 0);
        form.add(usernameField, 1, 0);
        form.add(passwordLbl, 0, 1);
        form.add(passwordField, 1, 1);

        VBox root = new VBox(20, title, form, loginBtn, backBtn);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL cssResource = AuthorLoginScreen.class.getResource("/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }
        return scene;
    }

    private static void showLoginError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Login failed");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
