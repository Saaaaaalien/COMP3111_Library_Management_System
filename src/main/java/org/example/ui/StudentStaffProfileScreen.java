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
import org.example.db.UserDao;
import org.example.domain.User;
import org.example.security.PasswordHasher;
import org.example.util.ValidationException;
import org.example.util.Validators;

import java.sql.SQLException;

/**
 * Student/Staff profile: full name and optional password change.
 */
public final class StudentStaffProfileScreen {

    private StudentStaffProfileScreen() {}

    public static Scene create(Navigator navigator, User user) {
        Label title = new Label("My Profile");
        title.getStyleClass().add("screen-title");

        TextField nameField = new TextField(user.getFullName());
        nameField.setMaxWidth(320);

        PasswordField currentPw = new PasswordField();
        currentPw.setPromptText("Current password (required to save any changes)");
        currentPw.setMaxWidth(320);
        PasswordField pw1 = new PasswordField();
        pw1.setPromptText("New password (leave blank to keep current)");
        pw1.setMaxWidth(320);
        PasswordField pw2 = new PasswordField();
        pw2.setPromptText("Confirm new password");
        pw2.setMaxWidth(320);

        Label hint = new Label("Password must meet strength rules if you change it. "
                + "You must re-enter your current password to save any profile or password change.");
        hint.setWrapText(true);
        hint.setMaxWidth(360);

        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setOnAction(e -> {
            try {
                String newName = nameField.getText().trim();
                String np = pw1.getText();
                boolean profileChanged = !newName.equals(user.getFullName().trim());
                boolean passwordChangeRequested = np != null && !np.isBlank();

                if (!profileChanged && !passwordChangeRequested) {
                    new Alert(Alert.AlertType.INFORMATION, "No changes to save.").showAndWait();
                    return;
                }

                String cur = currentPw.getText();
                if (cur == null || cur.isBlank()) {
                    throw new ValidationException("Re-enter your current password to confirm these changes.");
                }
                if (!PasswordHasher.verify(cur, user.getPasswordSalt(), user.getPasswordHash())) {
                    throw new ValidationException("Current password is incorrect.");
                }

                if (profileChanged) {
                    Validators.validateFullName(nameField.getText());
                    UserDao.updateFullName(user.getId(), newName);
                }

                if (passwordChangeRequested) {
                    Validators.validatePasswordStrength(np);
                    if (!np.equals(pw2.getText())) {
                        throw new ValidationException("New password and confirmation do not match.");
                    }
                    String salt = PasswordHasher.generateSalt();
                    String hash = PasswordHasher.hash(np, salt);
                    UserDao.updatePassword(user.getId(), hash, salt);
                    new Alert(Alert.AlertType.INFORMATION, "Password changed. You have been logged out — please sign in again.").showAndWait();
                    navigator.showStudentStaffPortal();
                    return;
                }

                new Alert(Alert.AlertType.INFORMATION, "Profile saved.").showAndWait();
                User refreshed = UserDao.findById(user.getId()).orElse(user);
                navigator.showAvailableBooks(refreshed);
            } catch (ValidationException ex) {
                new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not save profile.").showAndWait();
            }
        });

        Button backBtn = new Button("Back");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> {
            try {
                User refreshed = UserDao.findById(user.getId()).orElse(user);
                navigator.showAvailableBooks(refreshed);
            } catch (SQLException ex) {
                navigator.showAvailableBooks(user);
            }
        });

        VBox form = new VBox(10,
                new Label("Full name"),
                nameField,
                new Label("Change password"),
                currentPw,
                pw1,
                pw2,
                hint,
                saveBtn,
                backBtn);
        form.setAlignment(Pos.CENTER);
        form.setPadding(new Insets(24));

        BorderPane root = new BorderPane();
        root.setCenter(form);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        var css = StudentStaffProfileScreen.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }
}
