package org.example.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;

import org.example.app.Navigator;
import org.example.db.UserDao;
import org.example.domain.User;
import org.example.security.PasswordHasher;
import org.example.util.ValidationException;
import org.example.util.Validators;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Student/Staff profile: full name and optional password change.
 */
public final class StudentStaffProfileScreen {

    private StudentStaffProfileScreen() {}

    public static Scene create(Navigator navigator, User user) {
        Label title = new Label("My Profile");
        title.getStyleClass().add("screen-title");

        // ── Profile picture ──────────────────────────────────────────────────
        ImageView avatarView = new ImageView();
        avatarView.setFitWidth(80);
        avatarView.setFitHeight(80);
        avatarView.setPreserveRatio(true);
        avatarView.setStyle("-fx-border-color: #ccc; -fx-border-width: 1;");
        if (user.getAvatarPath() != null && new File(user.getAvatarPath()).exists()) {
            avatarView.setImage(new Image(new File(user.getAvatarPath()).toURI().toString()));
        }

        // Holds the pending (not-yet-saved) avatar file chosen this session
        final File[] pendingAvatar = {null};

        Label avatarStatusLbl = new Label("");
        avatarStatusLbl.setStyle("-fx-font-size: 10; -fx-text-fill: #555;");

        Button uploadAvatarBtn = new Button("\uD83D\uDDBC Upload Picture");
        uploadAvatarBtn.getStyleClass().add("secondary-button");
        uploadAvatarBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Choose Profile Picture");
            fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Images (JPG, PNG, GIF)", "*.jpg", "*.jpeg", "*.png", "*.gif"));
            File chosen = fc.showOpenDialog(null);
            if (chosen == null) return;
            // Validate size (max 2 MB)
            if (chosen.length() > 2 * 1024 * 1024) {
                new Alert(Alert.AlertType.WARNING,
                        "Image is too large. Maximum allowed size is 2 MB.").showAndWait();
                return;
            }
            pendingAvatar[0] = chosen;
            avatarView.setImage(new Image(chosen.toURI().toString()));
            avatarStatusLbl.setText("New picture selected (not saved yet): " + chosen.getName());
        });

        HBox avatarRow = new HBox(12, avatarView,
                new VBox(6, uploadAvatarBtn, avatarStatusLbl));
        avatarRow.setAlignment(Pos.CENTER_LEFT);

        // ── Name field ───────────────────────────────────────────────────────
        TextField nameField = new TextField(user.getFullName());
        nameField.setMaxWidth(320);

        // ── Password fields ──────────────────────────────────────────────────
        PasswordField currentPw = new PasswordField();
        currentPw.setPromptText("Current password (required to save any changes)");
        currentPw.setMaxWidth(320);
        PasswordField pw1 = new PasswordField();
        pw1.setPromptText("New password (leave blank to keep current)");
        pw1.setMaxWidth(320);
        PasswordField pw2 = new PasswordField();
        pw2.setPromptText("Confirm new password");
        pw2.setMaxWidth(320);

        // ── Password strength meter ──────────────────────────────────────────
        Label strengthLbl = new Label("");
        strengthLbl.setStyle("-fx-font-size: 11; -fx-font-weight: bold;");
        pw1.textProperty().addListener((obs, old, val) -> {
            if (val == null || val.isBlank()) {
                strengthLbl.setText("");
            } else {
                String level = Validators.getPasswordStrengthLabel(val);
                strengthLbl.setText("Strength: " + level);
                String color = "Weak".equals(level) ? "#e74c3c"
                             : "Medium".equals(level) ? "#e67e22" : "#27ae60";
                strengthLbl.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
            }
        });

        Label hint = new Label("Password must meet strength rules if you change it. "
                + "You must re-enter your current password to save any profile or password change.");
        hint.setWrapText(true);
        hint.setMaxWidth(360);

        // ── Save button ──────────────────────────────────────────────────────
        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setOnAction(e -> {
            try {
                String newName = nameField.getText().trim();
                String np = pw1.getText();
                boolean profileChanged = !newName.equals(user.getFullName().trim());
                boolean passwordChangeRequested = np != null && !np.isBlank();
                boolean avatarChanged = pendingAvatar[0] != null;

                if (!profileChanged && !passwordChangeRequested && !avatarChanged) {
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

                if (avatarChanged) {
                    File src = pendingAvatar[0];
                    // Store avatars in a local avatars/ directory next to the data folder
                    File avatarDir = new File("avatars");
                    avatarDir.mkdirs();
                    String ext = src.getName().substring(src.getName().lastIndexOf('.'));
                    File dest = new File(avatarDir, "user_" + user.getId() + ext);
                    Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    UserDao.updateAvatarPath(user.getId(), dest.getAbsolutePath());
                    avatarStatusLbl.setText("Profile picture saved.");
                    pendingAvatar[0] = null;
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
            } catch (IOException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not save profile picture: " + ex.getMessage()).showAndWait();
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

        // ── Password grid ────────────────────────────────────────────────────
        GridPane pwGrid = new GridPane();
        pwGrid.setHgap(12);
        pwGrid.setVgap(8);
        pwGrid.add(new Label("Current password"), 0, 0);
        pwGrid.add(currentPw, 1, 0);
        pwGrid.add(new Label("New password"), 0, 1);
        pwGrid.add(pw1, 1, 1);
        pwGrid.add(new Label(""), 0, 2);
        pwGrid.add(strengthLbl, 1, 2);
        pwGrid.add(new Label("Confirm password"), 0, 3);
        pwGrid.add(pw2, 1, 3);

        // ── Layout ───────────────────────────────────────────────────────────
        VBox form = new VBox(12,
                title,
                new javafx.scene.control.Separator(),
                new Label("Profile Picture"),
                avatarRow,
                new javafx.scene.control.Separator(),
                new Label("Full name"),
                nameField,
                new javafx.scene.control.Separator(),
                new Label("Change password"),
                pwGrid,
                hint,
                saveBtn,
                backBtn);
        form.setAlignment(Pos.CENTER);
        form.setPadding(new Insets(24));

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);

        BorderPane root = new BorderPane();
        root.setCenter(scroll);
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
