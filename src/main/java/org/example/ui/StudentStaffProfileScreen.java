package org.example.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;

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
import java.net.URL;

/**
 * Student/Staff profile: full name and optional password change.
 */
public final class StudentStaffProfileScreen {

    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_AVATAR_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif");
    /** Classpath default when the user has no custom avatar (add under {@code src/main/resources/images/}). */
    private static final String DEFAULT_AVATAR_RESOURCE = "/images/empty-pfp.png";

    private StudentStaffProfileScreen() {}

    public static Scene create(Navigator navigator, User user) {
        Label title = new Label("Profile");
        title.getStyleClass().add("screen-title");

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

        Label strengthLbl = new Label("");
        strengthLbl.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #555;");
        Label strengthHintLbl = new Label("Use 8+ chars with uppercase, number, and special character.");
        strengthHintLbl.setStyle("-fx-font-size: 10; -fx-text-fill: #666;");
        pw1.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isBlank()) {
                strengthLbl.setText("");
                strengthLbl.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #555;");
                return;
            }
            String strength = Validators.getPasswordStrengthLabel(newVal);
            strengthLbl.setText("Strength: " + strength);
            strengthLbl.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: " + strengthColor(strength) + ";");
        });

        Label avatarLbl = new Label("Profile picture");
        ImageView avatarView = new ImageView();
        avatarView.setFitWidth(80);
        avatarView.setFitHeight(80);
        avatarView.setPreserveRatio(true);
        avatarView.setStyle("-fx-border-color: #ccc; -fx-border-width: 1;");
        final File[] pendingAvatar = {null};
        loadAvatarInto(avatarView, user.getAvatarPath());

        Label avatarHint = new Label("Allowed: JPG/JPEG/PNG/GIF, max 2MB.");
        avatarHint.getStyleClass().add("login-hint");
        Label avatarStatusLbl = new Label("");
        avatarStatusLbl.setStyle("-fx-font-size: 10; -fx-text-fill: #555;");
        Button chooseAvatarBtn = new Button("Upload Picture");
        chooseAvatarBtn.getStyleClass().add("secondary-button");
        chooseAvatarBtn.setOnAction(ev -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose Profile Picture");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.jpeg", "*.png", "*.gif")
            );
            File picked = chooser.showOpenDialog(navigator.getStage());
            if (picked == null) {
                return;
            }
            String extension = getFileExtension(picked.getName());
            if (extension == null || !ALLOWED_AVATAR_EXTENSIONS.contains(extension)) {
                new Alert(Alert.AlertType.WARNING, "Invalid image format. Please choose a JPG, PNG, or GIF file.").showAndWait();
                return;
            }
            if (picked.length() > MAX_AVATAR_BYTES) {
                new Alert(Alert.AlertType.WARNING, "Image is too large. Maximum allowed size is 2 MB.").showAndWait();
                return;
            }
            Image selectedImage = new Image(picked.toURI().toString(), false);
            if (selectedImage.isError()) {
                new Alert(Alert.AlertType.WARNING,
                        "Selected file could not be loaded as an image. Please choose a valid image file.").showAndWait();
                return;
            }
            pendingAvatar[0] = picked;
            avatarView.setImage(selectedImage);
            avatarStatusLbl.setText("New picture selected (not saved yet): " + picked.getName());
        });

        Label hint = new Label("Current password is required to save any profile, picture, or password changes.");
        hint.setWrapText(true);
        hint.setMaxWidth(360);

        // ── Save button ──────────────────────────────────────────────────────
        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setOnAction(e -> {
            try {
                Validators.validateFullName(nameField.getText());
                String trimmedFullName = nameField.getText().trim();
                String current = currentPw.getText();
                String newPassword = pw1.getText();
                String confirm = pw2.getText();
                boolean profileChanged = !trimmedFullName.equals(user.getFullName().trim());
                boolean passwordChangeRequested = newPassword != null && !newPassword.isBlank();
                boolean avatarChanged = pendingAvatar[0] != null;

                if (!profileChanged && !passwordChangeRequested && !avatarChanged) {
                    new Alert(Alert.AlertType.INFORMATION, "No changes to save.").showAndWait();
                    return;
                }

                if (current == null || current.isBlank()) {
                    throw new ValidationException("Re-enter your current password to confirm these changes.");
                }
                if (!PasswordHasher.verify(current, user.getPasswordSalt(), user.getPasswordHash())) {
                    throw new ValidationException("Current password is incorrect.");
                }

                if (profileChanged) {
                    UserDao.updateFullName(user.getId(), trimmedFullName);
                }

                if (avatarChanged) {
                    File src = pendingAvatar[0];
                    String extension = getFileExtension(src.getName());
                    if (extension == null || !ALLOWED_AVATAR_EXTENSIONS.contains(extension)) {
                        throw new ValidationException("Invalid image format. Please choose a JPG, PNG, or GIF file.");
                    }
                    if (src.length() > MAX_AVATAR_BYTES) {
                        throw new ValidationException("Image is too large. Maximum allowed size is 2 MB.");
                    }
                    Image imageToSave = new Image(src.toURI().toString(), false);
                    if (imageToSave.isError()) {
                        throw new ValidationException("Selected file could not be loaded as an image.");
                    }
                    File avatarDir = new File("avatars");
                    if (!avatarDir.exists() && !avatarDir.mkdirs()) {
                        throw new IOException("Could not create avatar storage directory.");
                    }
                    File dest = new File(avatarDir, "user_" + user.getId() + extension);
                    Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    UserDao.updateAvatarPath(user.getId(), dest.getAbsolutePath());
                    avatarStatusLbl.setText("Profile picture saved.");
                    pendingAvatar[0] = null;
                }

                if (passwordChangeRequested) {
                    if (PasswordHasher.verify(newPassword, user.getPasswordSalt(), user.getPasswordHash())) {
                        throw new ValidationException("New password must be different from current password.");
                    }
                    Validators.validatePasswordStrength(newPassword);
                    if (!newPassword.equals(confirm)) {
                        throw new ValidationException("New passwords do not match.");
                    }
                    String salt = PasswordHasher.generateSalt();
                    String hash = PasswordHasher.hash(newPassword, salt);
                    UserDao.updatePassword(user.getId(), hash, salt);
                    new Alert(Alert.AlertType.INFORMATION, "Password changed. You have been logged out — please sign in again.").showAndWait();
                    navigator.showStudentStaffPortal();
                    return;
                }

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

        HBox avatarRow = new HBox(12, avatarView, new VBox(6, chooseAvatarBtn, avatarStatusLbl));
        avatarRow.setAlignment(Pos.CENTER_LEFT);

        VBox form = new VBox(10,
                title,
                new javafx.scene.control.Separator(),
                new Label("Full name"),
                nameField,
                avatarLbl,
                avatarRow,
                avatarHint,
                new javafx.scene.control.Separator(),
                new Label("Change password"),
                currentPw,
                pw1,
                pw2,
                strengthLbl,
                strengthHintLbl,
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
                    Image candidate = new Image(f.toURI().toString(), false);
                    if (!candidate.isError()) {
                        image = candidate;
                    }
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

    private static String getFileExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(lastDot).toLowerCase(Locale.ROOT);
    }

    private static String strengthColor(String strengthLabel) {
        if ("Weak".equals(strengthLabel)) return "#e74c3c";
        if ("Medium".equals(strengthLabel)) return "#e67e22";
        if ("Strong".equals(strengthLabel)) return "#27ae60";
        return "#555";
    }
}
