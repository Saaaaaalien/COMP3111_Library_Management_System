package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.example.app.Navigator;
import org.example.db.UserDao;
import org.example.domain.User;
import org.example.security.PasswordHasher;
import org.example.util.ValidationException;
import org.example.util.Validators;

import java.sql.SQLException;

/**
 * Author profile: name, bio, optional password (requires current password).
 */
public final class AuthorProfileScreen {

    private AuthorProfileScreen() {}

    public static Scene create(Navigator navigator, User user) {
        Label title = new Label("Author profile");
        title.getStyleClass().add("screen-title");

        TextField nameField = new TextField(user.getFullName());
        nameField.setMaxWidth(360);
        TextArea bioArea = new TextArea(user.getBio() != null ? user.getBio() : "");
        bioArea.setPromptText("Bio");
        bioArea.setPrefRowCount(4);
        bioArea.setMaxWidth(360);

        PasswordField currentPw = new PasswordField();
        currentPw.setPromptText("Current password (required if changing password)");
        currentPw.setMaxWidth(360);
        PasswordField pw1 = new PasswordField();
        pw1.setPromptText("New password (optional)");
        pw1.setMaxWidth(360);
        PasswordField pw2 = new PasswordField();
        pw2.setPromptText("Confirm new password");
        pw2.setMaxWidth(360);

        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setOnAction(e -> {
            try {
                Validators.validateFullName(nameField.getText());
                UserDao.updateFullNameAndBio(user.getId(), nameField.getText().trim(),
                        Validators.trimOptional(bioArea.getText()));

                String np = pw1.getText();
                if (np != null && !np.isBlank()) {
                    Validators.validatePasswordStrength(np);
                    if (!np.equals(pw2.getText())) {
                        throw new ValidationException("New passwords do not match.");
                    }
                    String cur = currentPw.getText();
                    if (cur == null || cur.isBlank()) {
                        throw new ValidationException("Enter your current password to set a new one.");
                    }
                    if (!PasswordHasher.verify(cur, user.getPasswordSalt(), user.getPasswordHash())) {
                        throw new ValidationException("Current password is incorrect.");
                    }
                    String salt = PasswordHasher.generateSalt();
                    String hash = PasswordHasher.hash(np, salt);
                    UserDao.updatePassword(user.getId(), hash, salt);
                    new Alert(Alert.AlertType.INFORMATION, "Password changed. Please sign in again.").showAndWait();
                    navigator.showAuthorPortal();
                    return;
                }

                new Alert(Alert.AlertType.INFORMATION, "Profile saved.").showAndWait();
                User refreshed = UserDao.findById(user.getId()).orElse(user);
                navigator.showAuthorDashboard(refreshed);
            } catch (ValidationException ex) {
                new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not save.").showAndWait();
            }
        });

        Button backBtn = new Button("Back");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setPrefWidth(140);
        backBtn.setOnAction(e -> {
            try {
                navigator.showAuthorDashboard(UserDao.findById(user.getId()).orElse(user));
            } catch (SQLException ex) {
                navigator.showAuthorDashboard(user);
            }
        });

        VBox form = new VBox(10,
                title,
                new Label("Full name"), nameField,
                new Label("Bio"), bioArea,
                new Label("Password"), currentPw, pw1, pw2,
                saveBtn, backBtn);
        form.setAlignment(Pos.CENTER);
        form.setPadding(new Insets(24));

        BorderPane root = new BorderPane();
        root.setCenter(form);
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        var css = AuthorProfileScreen.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }
}
