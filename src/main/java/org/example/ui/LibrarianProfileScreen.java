package org.example.ui;

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
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Task 3.5 – Librarian "Manage Own Profile" screen.
 * Allows the currently-logged-in librarian to update:
 *   • Full Name  (required)
 *   • Employee ID (optional)
 *   • Password   (optional; requires current password for confirmation)
 * All changes are validated before saving and confirmed via a dialog.
 */
public final class LibrarianProfileScreen {

    private LibrarianProfileScreen() {}

    public static Scene create(Navigator navigator, User librarian) {

        // ── Header ───────────────────────────────────────────────────────────
        Label titleLbl = new Label("My Profile");
        titleLbl.getStyleClass().add("screen-title");

        Label subLbl = new Label("Update your personal details below.");
        subLbl.getStyleClass().add("info-label");

        Label usernameLbl = new Label("Username: " + librarian.getUsername());
        usernameLbl.setStyle("-fx-font-size: 12; -fx-text-fill: #666;");

        VBox headerBox = new VBox(6, titleLbl, subLbl, usernameLbl);
        headerBox.setPadding(new Insets(20, 20, 0, 20));
        headerBox.setStyle("-fx-border-color: #f0f0f0; -fx-border-width: 0 0 1 0;");

        // ── Personal Details section ──────────────────────────────────────────
        Label detailsSectionLbl = new Label("Personal Details");
        detailsSectionLbl.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Label fullNameLbl = new Label("Full Name *");
        fullNameLbl.setStyle("-fx-font-size: 12;");
        TextField fullNameField = new TextField(librarian.getFullName() != null ? librarian.getFullName() : "");
        fullNameField.setPromptText("Enter your full name");
        fullNameField.setMaxWidth(320);

        Label employeeIdLbl = new Label("Employee ID");
        employeeIdLbl.setStyle("-fx-font-size: 12;");
        TextField employeeIdField = new TextField(librarian.getEmployeeId() != null ? librarian.getEmployeeId() : "");
        employeeIdField.setPromptText("Enter employee ID (optional)");
        employeeIdField.setMaxWidth(320);

        Label detailsErrorLbl = new Label("");
        detailsErrorLbl.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11;");
        detailsErrorLbl.setWrapText(true);
        detailsErrorLbl.setMaxWidth(360);

        Button saveDetailsBtn = new Button("Save Details");
        saveDetailsBtn.getStyleClass().add("primary-button");
        saveDetailsBtn.setOnAction(e ->
            handleSaveDetails(fullNameField.getText(), employeeIdField.getText(),
                              detailsErrorLbl, librarian, navigator));

        GridPane detailsGrid = new GridPane();
        detailsGrid.setHgap(16);
        detailsGrid.setVgap(10);
        detailsGrid.add(fullNameLbl,    0, 0);
        detailsGrid.add(fullNameField,  1, 0);
        detailsGrid.add(employeeIdLbl,  0, 1);
        detailsGrid.add(employeeIdField, 1, 1);

        VBox detailsSection = new VBox(10,
            detailsSectionLbl,
            detailsGrid,
            detailsErrorLbl,
            saveDetailsBtn
        );
        detailsSection.setPadding(new Insets(20));
        detailsSection.setStyle(
            "-fx-background-color: #ffffff;" +
            "-fx-border-color: #e0e0e0;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;"
        );

        // ── Password Change section ───────────────────────────────────────────
        Label pwSectionLbl = new Label("Change Password");
        pwSectionLbl.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Label pwHintLbl = new Label(
            "Leave all fields blank to keep your current password.\n" +
            "Password rules: min 8 chars, 1 uppercase, 1 digit, 1 special character."
        );
        pwHintLbl.setStyle("-fx-text-fill: #777; -fx-font-size: 11;");
        pwHintLbl.setWrapText(true);
        pwHintLbl.setMaxWidth(380);

        Label currentPwLbl = new Label("Current Password *");
        currentPwLbl.setStyle("-fx-font-size: 12;");
        PasswordField currentPwField = new PasswordField();
        currentPwField.setPromptText("Required to change password");
        currentPwField.setMaxWidth(320);

        Label newPwLbl = new Label("New Password *");
        newPwLbl.setStyle("-fx-font-size: 12;");
        PasswordField newPwField = new PasswordField();
        newPwField.setPromptText("New password");
        newPwField.setMaxWidth(320);

        Label confirmPwLbl = new Label("Confirm New Password *");
        confirmPwLbl.setStyle("-fx-font-size: 12;");
        PasswordField confirmPwField = new PasswordField();
        confirmPwField.setPromptText("Re-enter new password");
        confirmPwField.setMaxWidth(320);

        // Live password-strength indicator
        Label strengthLbl = new Label("");
        strengthLbl.setStyle("-fx-font-size: 11; -fx-font-weight: bold;");
        newPwField.textProperty().addListener((obs, old, val) -> {
            if (val == null || val.isBlank()) {
                strengthLbl.setText("");
            } else {
                String level = Validators.getPasswordStrengthLabel(val);
                strengthLbl.setText("Strength: " + level);
                strengthLbl.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: "
                    + ("Weak".equals(level) ? "#e74c3c" : "Medium".equals(level) ? "#e67e22" : "#27ae60") + ";");
            }
        });

        Label pwErrorLbl = new Label("");
        pwErrorLbl.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11;");
        pwErrorLbl.setWrapText(true);
        pwErrorLbl.setMaxWidth(360);

        Button savePwBtn = new Button("Change Password");
        savePwBtn.getStyleClass().add("primary-button");
        savePwBtn.setOnAction(e ->
            handleChangePassword(currentPwField.getText(), newPwField.getText(),
                                 confirmPwField.getText(), pwErrorLbl,
                                 currentPwField, newPwField, confirmPwField,
                                 librarian, navigator));

        GridPane pwGrid = new GridPane();
        pwGrid.setHgap(16);
        pwGrid.setVgap(10);
        pwGrid.add(currentPwLbl,   0, 0);
        pwGrid.add(currentPwField, 1, 0);
        pwGrid.add(newPwLbl,       0, 1);
        pwGrid.add(newPwField,     1, 1);
        pwGrid.add(new Label(""),  0, 2);
        pwGrid.add(strengthLbl,    1, 2);
        pwGrid.add(confirmPwLbl,   0, 3);
        pwGrid.add(confirmPwField, 1, 3);

        VBox pwSection = new VBox(10,
            pwSectionLbl,
            pwHintLbl,
            pwGrid,
            pwErrorLbl,
            savePwBtn
        );
        pwSection.setPadding(new Insets(20));
        pwSection.setStyle(
            "-fx-background-color: #ffffff;" +
            "-fx-border-color: #e0e0e0;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;"
        );

        // ── Center content ────────────────────────────────────────────────────
        VBox centerContent = new VBox(16, detailsSection, pwSection);
        centerContent.setPadding(new Insets(20));

        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(centerContent);
        scrollPane.setFitToWidth(true);

        // ── Footer ────────────────────────────────────────────────────────────
        Button backBtn = new Button("← Back to Dashboard");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> navigator.showLibrarianApproval(librarian));

        HBox footerBox = new HBox(backBtn);
        footerBox.setPadding(new Insets(15, 20, 15, 20));
        footerBox.setAlignment(Pos.CENTER_RIGHT);

        // ── Root layout ───────────────────────────────────────────────────────
        BorderPane root = new BorderPane();
        root.setTop(headerBox);
        root.setCenter(scrollPane);
        root.setBottom(footerBox);
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL css = LibrarianProfileScreen.class.getResource("/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        return scene;
    }

    // ── Save personal details ─────────────────────────────────────────────────

    private static void handleSaveDetails(String fullName, String employeeId,
                                          Label errorLbl, User librarian, Navigator navigator) {
        errorLbl.setText("");
        String trimmedName = fullName == null ? "" : fullName.trim();
        String trimmedEmpId = employeeId == null ? "" : employeeId.trim();

        // Validate
        try {
            Validators.validateFullName(trimmedName);
        } catch (ValidationException ex) {
            errorLbl.setText(ex.getMessage());
            return;
        }

        // Confirmation dialog
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Save changes to your personal details?",
            ButtonType.YES, ButtonType.CANCEL);
        confirm.setTitle("Confirm Changes");
        confirm.setHeaderText("Update Personal Details");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try {
                UserDao.updateProfile(librarian.getId(), trimmedName, trimmedEmpId, librarian.getBio());
                User refreshed = UserDao.findById(librarian.getId()).orElse(librarian);
                Alert success = new Alert(Alert.AlertType.INFORMATION,
                    "Personal details updated successfully.");
                success.setTitle("Success");
                success.setHeaderText(null);
                success.showAndWait();
                // Navigate back with the updated user object
                navigator.showLibrarianProfile(refreshed);
            } catch (SQLException ex) {
                Alert err = new Alert(Alert.AlertType.ERROR,
                    "Failed to save changes: " + ex.getMessage());
                err.setTitle("Error");
                err.setHeaderText(null);
                err.showAndWait();
            }
        });
    }

    // ── Change password ───────────────────────────────────────────────────────

    private static void handleChangePassword(String currentPw, String newPw, String confirmPw,
                                             Label errorLbl,
                                             PasswordField currentPwField,
                                             PasswordField newPwField,
                                             PasswordField confirmPwField,
                                             User librarian, Navigator navigator) {
        errorLbl.setText("");

        // If all blank — nothing to do
        if ((currentPw == null || currentPw.isBlank())
                && (newPw == null || newPw.isBlank())
                && (confirmPw == null || confirmPw.isBlank())) {
            errorLbl.setText("Enter your current password and a new password to change it.");
            return;
        }

        // Validate current password provided
        if (currentPw == null || currentPw.isBlank()) {
            errorLbl.setText("Current password is required.");
            return;
        }

        // Verify current password
        if (!PasswordHasher.verify(currentPw, librarian.getPasswordSalt(), librarian.getPasswordHash())) {
            errorLbl.setText("Current password is incorrect.");
            return;
        }

        // Validate new password strength
        try {
            Validators.validatePasswordStrength(newPw);
        } catch (ValidationException ex) {
            errorLbl.setText(ex.getMessage());
            return;
        }

        // Confirm passwords match
        if (!newPw.equals(confirmPw)) {
            errorLbl.setText("New password and confirmation do not match.");
            return;
        }

        // Must differ from current
        if (PasswordHasher.verify(newPw, librarian.getPasswordSalt(), librarian.getPasswordHash())) {
            errorLbl.setText("New password must be different from your current password.");
            return;
        }

        // Confirmation dialog
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Are you sure you want to change your password?\nYou will be signed out after the change.",
            ButtonType.YES, ButtonType.CANCEL);
        confirm.setTitle("Confirm Password Change");
        confirm.setHeaderText("Change Password");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try {
                String salt = PasswordHasher.generateSalt();
                String hash = PasswordHasher.hash(newPw, salt);
                UserDao.updatePassword(librarian.getId(), hash, salt);
                Alert success = new Alert(Alert.AlertType.INFORMATION,
                    "Password changed successfully. Please sign in again.");
                success.setTitle("Password Changed");
                success.setHeaderText(null);
                success.showAndWait();
                navigator.showLibrarianPortal();
            } catch (SQLException ex) {
                Alert err = new Alert(Alert.AlertType.ERROR,
                    "Failed to update password: " + ex.getMessage());
                err.setTitle("Error");
                err.setHeaderText(null);
                err.showAndWait();
            }
        });
    }
}
