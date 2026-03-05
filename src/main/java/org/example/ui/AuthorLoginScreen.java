package org.example.ui;

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
import org.example.app.Navigator;
import org.example.domain.User;
import org.example.service.AuthException;
import org.example.service.AuthService;

import java.sql.SQLException;

/**
 * Author login: username and password, then navigate to Publish Book screen.
 * Styled consistently with the Student/Staff login screen.
 */
public final class AuthorLoginScreen {
    private AuthorLoginScreen() {}

    public static Scene create(Navigator navigator) {
        Label title = new Label("Login (Author)");
        title.getStyleClass().add("screen-title");

        Label usernameLbl = new Label("Username:");
        TextField usernameField = new TextField();
        usernameField.setMaxWidth(280);

        Label passwordLbl = new Label("Password:");
        PasswordField passwordField = new PasswordField();
        passwordField.setMaxWidth(280);

        Button loginBtn = new Button("Login");
        loginBtn.getStyleClass().add("primary-button");
        loginBtn.setOnAction(e -> {
            String username = usernameField.getText();
            String password = passwordField.getText();
            try {
                User user = AuthService.login(username, password);
                if (user.getRole() != org.example.domain.Role.AUTHOR) {
                    showLoginError("This portal is for authors only.");
                    return;
                }
                navigator.showAuthorDashboard(user);
            } catch (AuthException ex) {
                showLoginError(ex.getMessage());
            } catch (SQLException ex) {
                showLoginError("A database error occurred. Please try again.");
            }
        });

        Button backBtn = new Button("Back");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> navigator.showAuthorPortal());

        VBox usernameBox = new VBox(5, usernameLbl, usernameField);
        usernameBox.setAlignment(Pos.CENTER);

        VBox passwordBox = new VBox(5, passwordLbl, passwordField);
        passwordBox.setAlignment(Pos.CENTER);

        VBox form = new VBox(12, usernameBox, passwordBox);
        form.setAlignment(Pos.CENTER);

        Label hintLbl = new Label("Use your author account credentials. Username is case-sensitive (e.g. author1 ≠ Author1).");
        hintLbl.setWrapText(true);
        hintLbl.setMaxWidth(280);
        hintLbl.getStyleClass().add("login-hint");

        VBox content = new VBox(18, title, form, hintLbl, loginBtn, backBtn);
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(440);
        content.getStyleClass().add("content-card");

        BorderPane root = new BorderPane();
        root.setCenter(content);
        BorderPane.setAlignment(content, Pos.CENTER);
        root.setPadding(new Insets(40));
        root.getStyleClass().add("app-root");

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
