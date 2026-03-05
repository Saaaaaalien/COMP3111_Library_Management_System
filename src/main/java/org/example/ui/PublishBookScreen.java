package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import org.example.app.Navigator;
import org.example.domain.User;
import org.example.service.PublishService;

import java.io.File;

public final class PublishBookScreen {

    private static File selectedBookFile;
    private static Label fileNameLabel;
    private static Label statusLabel;
    private static TextField titleField;
    private static ComboBox<String> genreCombo;
    private static TextArea descriptionArea;

    private PublishBookScreen() {}

    public static Scene create(Navigator navigator, User currentAuthor) {
        // Title
        Label title = new Label("Publish New Book");
        title.getStyleClass().add("screen-title");

        // Create form grid
        GridPane form = new GridPane();
        form.setHgap(15);
        form.setVgap(15);
        form.setAlignment(Pos.CENTER);
        form.setPadding(new Insets(30));

        // Form fields
        Label titleLabel = new Label("Book Title:");
        titleLabel.getStyleClass().add("form-label");
        titleField = new TextField();
        titleField.setPromptText("Enter book title");
        titleField.setPrefWidth(300);

        Label authorLabel = new Label("Author:");
        authorLabel.getStyleClass().add("form-label");
        TextField authorField = new TextField(currentAuthor.getFullName());
        authorField.setEditable(false);
        authorField.setStyle("-fx-background-color: #f0f0f0;");

        Label genreLabel = new Label("Genre:");
        genreLabel.getStyleClass().add("form-label");
        genreCombo = new ComboBox<>();
        genreCombo.getItems().addAll(
                "Fiction", "Non-Fiction", "Science Fiction", "Fantasy",
                "Mystery", "Thriller", "Romance", "Biography",
                "History", "Self-Help", "Technical", "Textbook",
                "Children's", "Poetry", "Other"
        );
        genreCombo.setPromptText("Select genre");
        genreCombo.setPrefWidth(300);
        genreCombo.setEditable(true);

        Label descriptionLabel = new Label("Description/Abstract:");
        descriptionLabel.getStyleClass().add("form-label");
        descriptionArea = new TextArea();
        descriptionArea.setPromptText("Enter book description, abstract, or summary...");
        descriptionArea.setPrefRowCount(6);
        descriptionArea.setPrefWidth(300);
        descriptionArea.setWrapText(true);

        Label fileLabel = new Label("Book File:");
        fileLabel.getStyleClass().add("form-label");

        HBox fileBox = new HBox(10);
        fileBox.setAlignment(Pos.CENTER_LEFT);

        Button chooseFileBtn = new Button("Choose File");
        chooseFileBtn.getStyleClass().add("secondary-button");

        fileNameLabel = new Label("No file selected");
        fileNameLabel.setStyle("-fx-text-fill: #666;");

        fileBox.getChildren().addAll(chooseFileBtn, fileNameLabel);

        // File chooser action
        chooseFileBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Book File");

            FileChooser.ExtensionFilter pdfFilter =
                    new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf");
            FileChooser.ExtensionFilter txtFilter =
                    new FileChooser.ExtensionFilter("Text Files (*.txt)", "*.txt");
            FileChooser.ExtensionFilter docFilter =
                    new FileChooser.ExtensionFilter("Word Documents (*.doc, *.docx)", "*.doc", "*.docx");

            fileChooser.getExtensionFilters().addAll(pdfFilter, txtFilter, docFilter);

            File selectedFile = fileChooser.showOpenDialog(null);
            if (selectedFile != null) {
                selectedBookFile = selectedFile;
                fileNameLabel.setText(selectedFile.getName());
                fileNameLabel.setStyle("-fx-text-fill: green;");
            }
        });

        // Add all to grid
        form.add(titleLabel, 0, 0);
        form.add(titleField, 1, 0);
        form.add(authorLabel, 0, 1);
        form.add(authorField, 1, 1);
        form.add(genreLabel, 0, 2);
        form.add(genreCombo, 1, 2);
        form.add(descriptionLabel, 0, 3);
        form.add(descriptionArea, 1, 3);
        form.add(fileLabel, 0, 4);
        form.add(fileBox, 1, 4);

        // Buttons
        HBox buttonBox = new HBox(20);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(20, 0, 0, 0));

        Button submitBtn = new Button("Submit for Approval");
        submitBtn.getStyleClass().add("primary-button");
        submitBtn.setPrefWidth(200);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-button");
        cancelBtn.setPrefWidth(150);

        buttonBox.getChildren().addAll(submitBtn, cancelBtn);

        // Status label
        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");

        // Submit action - now calls the service
        submitBtn.setOnAction(e -> {
            PublishService.PublishResult result = PublishService.submitBook(
                    currentAuthor,
                    titleField.getText().trim(),
                    genreCombo.getValue(),
                    descriptionArea.getText().trim(),
                    selectedBookFile
            );

            if (result.isSuccess()) {
                showSuccess("Success", result.getMessage());
                clearForm();
            } else {
                showError("Error", result.getMessage());
            }
        });

        // Cancel action: go back to Author portal entry
        cancelBtn.setOnAction(e -> navigator.showAuthorPortal());

        // Main content card with white background, centered like other screens
        VBox content = new VBox(20, title, form, buttonBox, statusLabel);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(20));
        content.setMaxWidth(600);
        content.getStyleClass().add("content-card");

        BorderPane root = new BorderPane();
        root.setCenter(content);
        BorderPane.setAlignment(content, Pos.CENTER);
        root.setPadding(new Insets(40));
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());

        // Add CSS
        java.net.URL cssResource = PublishBookScreen.class.getResource("/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }

        return scene;
    }

    private static void clearForm() {
        titleField.clear();
        genreCombo.setValue(null);
        descriptionArea.clear();
        selectedBookFile = null;
        fileNameLabel.setText("No file selected");
        fileNameLabel.setStyle("-fx-text-fill: #666;");
    }

    private static void showSuccess(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}