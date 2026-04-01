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
import org.example.service.AuthService;
import org.example.app.DraftService;
import java.util.Map;
import org.example.util.ValidationException;

import java.sql.SQLException;

/**
 * Author registration: username, full name, bio, password.
 * Role is implicitly AUTHOR;
 */
public final class AuthorRegisterScreen {
    private AuthorRegisterScreen() {}

    public static Scene create(Navigator navigator) {
        Label title = new Label("Register (Author)");
        title.getStyleClass().add("screen-title");

        Label usernameLabel = new Label("Username:");
        TextField usernameField = new TextField();
        usernameField.setPromptText("3–50 characters, letters/numbers/underscore");
        usernameField.setMaxWidth(280);

        Label firstNameLabel = new Label("First Name:");
        TextField firstNameField = new TextField();
        firstNameField.setMaxWidth(280);

        Label lastNameLabel = new Label("Last Name:");
        TextField lastNameField = new TextField();
        lastNameField.setMaxWidth(280);

        Label bioLabel = new Label("Bio(optional):");
        TextField bioField = new TextField();
        bioField.setMaxWidth(280);

        // Load draft if present (password is not persisted for security)
        Map<String, String> draft = DraftService.loadDraft("AUTHOR_REGISTER");
        if (draft.containsKey("username")) usernameField.setText(draft.get("username"));
        if (draft.containsKey("firstName")) firstNameField.setText(draft.get("firstName"));
        if (draft.containsKey("lastName")) lastNameField.setText(draft.get("lastName"));
        if (draft.containsKey("bio")) bioField.setText(draft.get("bio"));

        // Persist draft on change (best-effort). Do NOT save passwords.
        usernameField.textProperty().addListener((obs, oldV, newV) ->
                DraftService.saveDraft("AUTHOR_REGISTER", Map.of(
                        "username", newV == null ? "" : newV,
                        "firstName", firstNameField.getText() == null ? "" : firstNameField.getText(),
                        "lastName", lastNameField.getText() == null ? "" : lastNameField.getText(),
                        "bio", bioField.getText() == null ? "" : bioField.getText()
                )));
        firstNameField.textProperty().addListener((obs, oldV, newV) ->
                DraftService.saveDraft("AUTHOR_REGISTER", Map.of(
                        "username", usernameField.getText() == null ? "" : usernameField.getText(),
                        "firstName", newV == null ? "" : newV,
                        "lastName", lastNameField.getText() == null ? "" : lastNameField.getText(),
                        "bio", bioField.getText() == null ? "" : bioField.getText()
                )));
        lastNameField.textProperty().addListener((obs, oldV, newV) ->
                DraftService.saveDraft("AUTHOR_REGISTER", Map.of(
                        "username", usernameField.getText() == null ? "" : usernameField.getText(),
                        "firstName", firstNameField.getText() == null ? "" : firstNameField.getText(),
                        "lastName", newV == null ? "" : newV,
                        "bio", bioField.getText() == null ? "" : bioField.getText()
                )));
        bioField.textProperty().addListener((obs, oldV, newV) ->
                DraftService.saveDraft("AUTHOR_REGISTER", Map.of(
                        "username", usernameField.getText() == null ? "" : usernameField.getText(),
                        "firstName", firstNameField.getText() == null ? "" : firstNameField.getText(),
                        "lastName", lastNameField.getText() == null ? "" : lastNameField.getText(),
                        "bio", newV == null ? "" : newV
                )));

        Label passwordLabel = new Label("Password:");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Min 8 chars, 1 uppercase, 1 number, 1 special character");
        passwordField.setMaxWidth(280);

        Label strengthLbl = new Label("Empty");
        strengthLbl.getStyleClass().add("password-strength");
        passwordField.textProperty().addListener((obs, prev, newVal) -> {
            String strength = org.example.util.Validators.getPasswordStrengthLabel(newVal);
            strengthLbl.setText(strength.isEmpty() ? "Empty" : "Strength: " + strength);
        });

        Button registerBtn = new Button("Register");
        registerBtn.getStyleClass().add("primary-button");
        registerBtn.setOnAction(e -> {
            String username = usernameField.getText();
            String firstName = firstNameField.getText();
            String lastName = lastNameField.getText();
            String bio = bioField.getText();
            String password = passwordField.getText();
            try {
                AuthService.registerAuthor(username, firstName, lastName, password, bio);
                // Clear draft on success
                DraftService.clearDraft("AUTHOR_REGISTER");
                showAlert(Alert.AlertType.INFORMATION, "Registration successful",
                        "You can now log in with your author username and password.");
                navigator.showAuthorLogin();
            } catch (ValidationException ex) {
                showAlert(Alert.AlertType.ERROR, "Registration failed", ex.getMessage());
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Registration failed", "A database error occurred. Please try again.");
            }
        });

        Button backBtn = new Button("Back");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(e -> {
            DraftService.clearDraft("AUTHOR_REGISTER");
            navigator.showAuthorPortal();
        });

        VBox usernameBox = new VBox(5, usernameLabel, usernameField);
        usernameBox.setAlignment(Pos.CENTER);

        VBox firstNameBox = new VBox(5, firstNameLabel, firstNameField);
        firstNameBox.setAlignment(Pos.CENTER);

        VBox lastNameBox = new VBox(5, lastNameLabel, lastNameField);
        lastNameBox.setAlignment(Pos.CENTER);

        VBox bioBox = new VBox(5, bioLabel, bioField);
        bioBox.setAlignment(Pos.CENTER);

        VBox passwordBox = new VBox(5, passwordLabel, passwordField, strengthLbl);
        passwordBox.setAlignment(Pos.CENTER);

        VBox form = new VBox(12, usernameBox, firstNameBox, lastNameBox, bioBox, passwordBox);
        form.setAlignment(Pos.CENTER);

        VBox content = new VBox(18, title, form, registerBtn, backBtn);
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(440);
        content.getStyleClass().add("content-card");

        BorderPane root = new BorderPane();
        root.setCenter(content);
        BorderPane.setAlignment(content, Pos.CENTER);
        root.setPadding(new Insets(40));
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL cssResource = AuthorRegisterScreen.class.getResource("/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }
        return scene;
    }

    private static void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
