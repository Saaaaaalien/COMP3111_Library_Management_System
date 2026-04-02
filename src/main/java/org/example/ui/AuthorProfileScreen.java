package org.example.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.Objects;

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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Author profile: name, bio, optional password (requires current password).
 */
public final class AuthorProfileScreen {

    private AuthorProfileScreen() {}

    public static Scene create(Navigator navigator, User user) {
        Label title = new Label("Author profile");
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

        // ── Name and bio ──────────────────────────────────────────────────
        TextField nameField = new TextField(user.getFullName());
        nameField.setMaxWidth(360);
        TextArea bioArea = new TextArea(user.getBio() != null ? user.getBio() : "");
        bioArea.setPromptText("Bio");
        bioArea.setPrefRowCount(4);
        bioArea.setMaxWidth(360);

        // ── Password fields ──────────────────────────────────────────────────
        PasswordField currentPw = new PasswordField();
        currentPw.setPromptText("Current password (required to save any changes)");
        currentPw.setMaxWidth(360);
        PasswordField pw1 = new PasswordField();
        pw1.setPromptText("New password (optional)");
        pw1.setMaxWidth(360);
        PasswordField pw2 = new PasswordField();
        pw2.setPromptText("Confirm new password");
        pw2.setMaxWidth(360);

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

        // ── Save button ──────────────────────────────────────────────────────
        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setOnAction(e -> {
            try {
                String newName = nameField.getText().trim();
                String newBio = Validators.trimOptional(bioArea.getText());
                String oldBio = Validators.trimOptional(user.getBio());
                String np = pw1.getText();
                boolean profileChanged = !newName.equals(user.getFullName().trim())
                        || !Objects.equals(newBio, oldBio);
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
                    UserDao.updateFullNameAndBio(user.getId(), newName, newBio);
                }

                if (avatarChanged) {
                    File src = pendingAvatar[0];
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
                        throw new ValidationException("New passwords do not match.");
                    }
                    String salt = PasswordHasher.generateSalt();
                    String hash = PasswordHasher.hash(np, salt);
                    UserDao.updatePassword(user.getId(), hash, salt);
                    new Alert(Alert.AlertType.INFORMATION, "Password changed. You have been logged out — please sign in again.").showAndWait();
                    navigator.showAuthorPortal();
                    return;
                }

                new Alert(Alert.AlertType.INFORMATION, "Profile saved.").showAndWait();
                User refreshed = UserDao.findById(user.getId()).orElse(user);
                navigator.showAuthorDashboard(refreshed);
            } catch (ValidationException ex) {
                new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait();
            } catch (IOException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not save profile picture: " + ex.getMessage()).showAndWait();
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
                new Label("Full name"), nameField,
                new Label("Bio"), bioArea,
                new javafx.scene.control.Separator(),
                new Label("Password"), pwGrid,
                saveBtn, backBtn);
        form.setAlignment(Pos.CENTER);
        form.setPadding(new Insets(24));

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);

        BorderPane root = new BorderPane();
        root.setCenter(scroll);
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        var css = AuthorProfileScreen.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }
}
