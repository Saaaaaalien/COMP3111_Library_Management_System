package org.example.ui;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
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

/**
 * Librarian profile: full name, employee ID, optional avatar and password.
 */
public final class LibrarianProfileScreen {
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_AVATAR_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif");
    private static final String DEFAULT_AVATAR_RESOURCE = "/images/empty-pfp.png";

    private LibrarianProfileScreen() {}

    public static Scene create(Navigator navigator, User librarian) {
        Label title = new Label("Profile");
        title.getStyleClass().add("screen-title");

        TextField fullNameField = new TextField(librarian.getFullName() != null ? librarian.getFullName() : "");
        fullNameField.setPromptText("Full name");
        fullNameField.setMaxWidth(360);

        TextField employeeIdField = new TextField(librarian.getEmployeeId() != null ? librarian.getEmployeeId() : "");
        employeeIdField.setPromptText("Employee ID (optional)");
        employeeIdField.setMaxWidth(360);

        ImageView avatarView = new ImageView();
        avatarView.setFitWidth(80);
        avatarView.setFitHeight(80);
        avatarView.setPreserveRatio(true);
        avatarView.setStyle("-fx-border-color: #ccc; -fx-border-width: 1;");
        final File[] pendingAvatar = {null};
        loadAvatarInto(avatarView, librarian.getAvatarPath());

        Label avatarHintLbl = new Label("Allowed: JPG/JPEG/PNG/GIF, max 2MB.");
        avatarHintLbl.setStyle("-fx-font-size: 10; -fx-text-fill: #666;");
        Label avatarStatusLbl = new Label("");
        avatarStatusLbl.setStyle("-fx-font-size: 10; -fx-text-fill: #555;");

        Button uploadAvatarBtn = new Button("Upload Picture");
        uploadAvatarBtn.getStyleClass().add("secondary-button");
        uploadAvatarBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose Profile Picture");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Images (JPG, PNG, GIF)", "*.jpg", "*.jpeg", "*.png", "*.gif"));
            File selected = chooser.showOpenDialog(navigator.getStage());
            if (selected == null) return;
            String extension = getFileExtension(selected.getName());
            if (extension == null || !ALLOWED_AVATAR_EXTENSIONS.contains(extension)) {
                new Alert(Alert.AlertType.WARNING, "Invalid image format. Please choose a JPG, PNG, or GIF file.").showAndWait();
                return;
            }
            if (selected.length() > MAX_AVATAR_BYTES) {
                new Alert(Alert.AlertType.WARNING, "Image is too large. Maximum allowed size is 2 MB.").showAndWait();
                return;
            }
            Image selectedImage = new Image(selected.toURI().toString(), false);
            if (selectedImage.isError()) {
                new Alert(Alert.AlertType.WARNING,
                        "Selected file could not be loaded as an image. Please choose a valid image file.").showAndWait();
                return;
            }
            pendingAvatar[0] = selected;
            avatarView.setImage(selectedImage);
            avatarStatusLbl.setText("New picture selected (not saved yet): " + selected.getName());
        });

        HBox avatarRow = new HBox(12, avatarView, new VBox(6, uploadAvatarBtn, avatarStatusLbl));
        avatarRow.setAlignment(Pos.CENTER_LEFT);

        PasswordField currentPw = new PasswordField();
        currentPw.setPromptText("Current password (required to save any changes)");
        currentPw.setMaxWidth(360);
        PasswordField pw1 = new PasswordField();
        pw1.setPromptText("New password (optional)");
        pw1.setMaxWidth(360);
        PasswordField pw2 = new PasswordField();
        pw2.setPromptText("Confirm new password");
        pw2.setMaxWidth(360);

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
            String level = Validators.getPasswordStrengthLabel(newVal);
            strengthLbl.setText("Strength: " + level);
            strengthLbl.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: " + strengthColor(level) + ";");
        });

        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setOnAction(e -> {
            try {
                String newName = fullNameField.getText().trim();
                String newEmployeeId = Validators.trimOptional(employeeIdField.getText());
                String oldEmployeeId = Validators.trimOptional(librarian.getEmployeeId());
                String newPassword = pw1.getText();

                boolean profileChanged = !newName.equals(librarian.getFullName().trim())
                        || !Objects.equals(newEmployeeId, oldEmployeeId);
                boolean avatarChanged = pendingAvatar[0] != null;
                boolean passwordChangeRequested = newPassword != null && !newPassword.isBlank();

                if (!profileChanged && !avatarChanged && !passwordChangeRequested) {
                    new Alert(Alert.AlertType.INFORMATION, "No changes to save.").showAndWait();
                    return;
                }

                String current = currentPw.getText();
                if (current == null || current.isBlank()) {
                    throw new ValidationException("Re-enter your current password to confirm these changes.");
                }
                if (!PasswordHasher.verify(current, librarian.getPasswordSalt(), librarian.getPasswordHash())) {
                    throw new ValidationException("Current password is incorrect.");
                }

                if (profileChanged) {
                    Validators.validateFullName(fullNameField.getText());
                    String currentBio = UserDao.findById(librarian.getId()).map(User::getBio).orElse(librarian.getBio());
                    UserDao.updateProfile(librarian.getId(), newName, newEmployeeId, currentBio);
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
                    File dest = new File(avatarDir, "user_" + librarian.getId() + extension);
                    Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    UserDao.updateAvatarPath(librarian.getId(), dest.getAbsolutePath());
                    avatarStatusLbl.setText("Profile picture saved.");
                    pendingAvatar[0] = null;
                }

                if (passwordChangeRequested) {
                    if (PasswordHasher.verify(newPassword, librarian.getPasswordSalt(), librarian.getPasswordHash())) {
                        throw new ValidationException("New password must be different from current password.");
                    }
                    Validators.validatePasswordStrength(newPassword);
                    if (!newPassword.equals(pw2.getText())) {
                        throw new ValidationException("New passwords do not match.");
                    }
                    String salt = PasswordHasher.generateSalt();
                    String hash = PasswordHasher.hash(newPassword, salt);
                    UserDao.updatePassword(librarian.getId(), hash, salt);
                    new Alert(Alert.AlertType.INFORMATION, "Password changed. You have been logged out — please sign in again.").showAndWait();
                    navigator.showLibrarianPortal();
                    return;
                }

                new Alert(Alert.AlertType.INFORMATION, "Profile saved successfully.").showAndWait();
                User refreshed = UserDao.findById(librarian.getId()).orElse(librarian);
                navigator.showLibrarianProfile(refreshed);
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
                User refreshed = UserDao.findById(librarian.getId()).orElse(librarian);
                navigator.showLibrarianApproval(refreshed);
            } catch (SQLException ex) {
                navigator.showLibrarianApproval(librarian);
            }
        });

        VBox form = new VBox(12,
                title,
                new javafx.scene.control.Separator(),
                new Label("Full name"), fullNameField,
                new Label("Employee ID"), employeeIdField,
                new javafx.scene.control.Separator(),
                new Label("Profile Picture"), avatarRow, avatarHintLbl,
                new javafx.scene.control.Separator(),
                new Label("Password"),
                currentPw, pw1, strengthLbl, strengthHintLbl, pw2,
                saveBtn, backBtn);
        form.setAlignment(Pos.CENTER);
        form.setPadding(new Insets(24));

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);

        BorderPane root = new BorderPane();
        root.setCenter(scroll);
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        URL css = LibrarianProfileScreen.class.getResource("/app.css");
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
                URL res = LibrarianProfileScreen.class.getResource(DEFAULT_AVATAR_RESOURCE);
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
