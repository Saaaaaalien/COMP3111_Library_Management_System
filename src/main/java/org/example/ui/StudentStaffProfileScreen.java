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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.example.app.Navigator;
import org.example.db.UserDao;
import org.example.domain.User;
import org.example.security.PasswordHasher;
import org.example.util.ValidationException;
import org.example.util.Validators;

import java.io.File;
import java.net.URL;
import java.sql.SQLException;

/**
 * Student/Staff profile: full name and optional password change.
 */
public final class StudentStaffProfileScreen {

    private static final long MAX_AVATAR_SIZE_BYTES = 2L * 1024L * 1024L;
    /** Classpath default when the user has no custom avatar (add under {@code src/main/resources/images/}). */
    private static final String DEFAULT_AVATAR_RESOURCE = "/images/empty-pfp.png";

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

        Label strengthLbl = new Label("Strength: Empty");
        strengthLbl.getStyleClass().add("password-strength");
        pw1.textProperty().addListener((obs, oldVal, newVal) -> {
            String strength = Validators.getPasswordStrengthLabel(newVal);
            strengthLbl.setText("Strength: " + (strength == null || strength.isBlank() ? "Empty" : strength));
        });

        Label avatarLbl = new Label("Profile picture (optional)");
        ImageView avatarView = new ImageView();
        avatarView.setFitWidth(80);
        avatarView.setFitHeight(80);
        avatarView.setPreserveRatio(true);
        String[] selectedAvatarPath = new String[] { user.getAvatarPath() };
        loadAvatarInto(avatarView, selectedAvatarPath[0]);

        Label avatarHint = new Label("Allowed: JPG/JPEG/PNG, max 2MB. Default: empty-pfp.png");
        avatarHint.getStyleClass().add("login-hint");
        Button chooseAvatarBtn = new Button("Choose picture");
        chooseAvatarBtn.getStyleClass().add("secondary-button");
        chooseAvatarBtn.setOnAction(ev -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose Profile Picture");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.jpeg", "*.png")
            );
            File picked = chooser.showOpenDialog(navigator.getStage());
            if (picked == null) {
                return;
            }
            String lower = picked.getName().toLowerCase();
            if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png"))) {
                new Alert(Alert.AlertType.WARNING, "Invalid image format. Please select JPG/JPEG/PNG.").showAndWait();
                return;
            }
            if (picked.length() > MAX_AVATAR_SIZE_BYTES) {
                new Alert(Alert.AlertType.WARNING, "Image too large. Maximum allowed size is 2MB.").showAndWait();
                return;
            }
            selectedAvatarPath[0] = picked.getAbsolutePath();
            loadAvatarInto(avatarView, selectedAvatarPath[0]);
        });

        Label hint = new Label("Password must meet strength rules if you change it. Your current password is required to change it.");
        hint.setWrapText(true);
        hint.setMaxWidth(360);

        // ── Save button ──────────────────────────────────────────────────────
        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setOnAction(e -> {
            try {
                Validators.validateFullName(nameField.getText());
                String trimmedFullName = nameField.getText().trim();
                String avatarPath = selectedAvatarPath[0];
                String current = currentPw.getText();
                String newPassword = pw1.getText();
                String confirm = pw2.getText();

                boolean anyPasswordFieldEntered =
                        (current != null && !current.isBlank())
                                || (newPassword != null && !newPassword.isBlank())
                                || (confirm != null && !confirm.isBlank());

                // Password change is optional, but if the user starts entering password fields,
                // we validate that the change request is well-formed.
                if (anyPasswordFieldEntered) {
                    if (newPassword == null || newPassword.isBlank()) {
                        throw new ValidationException("Enter a new password to update it.");
                    }
                    if (confirm == null || confirm.isBlank()) {
                        throw new ValidationException("Confirm new password is required.");
                    }

                    Validators.validatePasswordStrength(newPassword);
                    if (!newPassword.equals(confirm)) {
                        throw new ValidationException("New password and confirmation do not match.");
                    }

                    if (current == null || current.isBlank()) {
                        throw new ValidationException("Enter your current password to set a new one.");
                    }
                    if (!PasswordHasher.verify(current, user.getPasswordSalt(), user.getPasswordHash())) {
                        throw new ValidationException("Current password is incorrect.");
                    }

                    String salt = PasswordHasher.generateSalt();
                    String hash = PasswordHasher.hash(newPassword, salt);
                    UserDao.updatePassword(user.getId(), hash, salt);
                    // Only update name/avatar after password change validation succeeds.
                    UserDao.updateFullName(user.getId(), trimmedFullName);
                    UserDao.updateAvatarPath(user.getId(), avatarPath);
                    new Alert(Alert.AlertType.INFORMATION, "Password updated. Please sign in again.").showAndWait();
                    navigator.showStudentStaffPortal();
                    return;
                }

                // No password change requested; safe to persist name/avatar now.
                UserDao.updateFullName(user.getId(), trimmedFullName);
                UserDao.updateAvatarPath(user.getId(), avatarPath);
                new Alert(Alert.AlertType.INFORMATION, "Profile saved successfully.").showAndWait();
                User refreshed = UserDao.findById(user.getId()).orElse(user);
                navigator.showAvailableBooks(refreshed);
            } catch (ValidationException ex) {
                new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
            } catch (IOException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not save profile picture: " + ex.getMessage()).showAndWait();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Update failed: " + ex.getMessage()).showAndWait();
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

        VBox avatarActions = new VBox(chooseAvatarBtn);
        avatarActions.setAlignment(Pos.CENTER);

        VBox form = new VBox(10,
                new Label("Full name"),
                nameField,
                avatarLbl,
                avatarView,
                avatarActions,
                avatarHint,
                new Label("Change password"),
                currentPw,
                pw1,
                pw2,
                strengthLbl,
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

    private static void loadAvatarInto(ImageView target, String avatarPath) {
        Image image = null;
        if (avatarPath != null && !avatarPath.isBlank()) {
            try {
                File f = new File(avatarPath);
                if (f.isFile()) {
                    image = new Image(f.toURI().toString(), true);
                }
            } catch (Exception ignored) {
            }
        }
        if (image == null) {
            try {
                URL res = StudentStaffProfileScreen.class.getResource(DEFAULT_AVATAR_RESOURCE);
                if (res != null) {
                    image = new Image(res.toExternalForm(), true);
                }
            } catch (Exception ignored) {
            }
        }
        target.setImage(image);
    }
}
