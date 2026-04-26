package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.animation.PauseTransition;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.example.app.Navigator;
import org.example.db.PublishDraftDao;
import org.example.domain.User;
import org.example.service.BookSummaryService;
import org.example.service.PublishService;
import org.example.util.BookPreviewUtil;

import java.io.File;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

public final class PublishBookScreen {

    private static File selectedBookFile;
    private static File selectedCoverFile;
    private static Label fileNameLabel;
    private static TextField titleField;
    private static ListView<String> genreListView;
    private static TextArea descriptionArea;
    private static User currentUser;
    private static Navigator navigator;

    // Display components for selections
    private static Label selectedGenresLabel;
    private static Label fileDisplayLabel;
    private static Label coverPathDisplay;
    private static Label coverNameLabel;
    private static Label summaryStatusLabel;
    private static Button generateSummaryButton;

    private static final List<String> AVAILABLE_GENRES = List.of(
            "Fiction", "Non-Fiction", "Science Fiction", "Fantasy",
            "Mystery", "Thriller", "Romance", "Biography",
            "History", "Self-Help", "Technical", "Textbook",
            "Children's", "Poetry", "Horror", "Adventure",
            "Young Adult", "Classic", "Philosophy", "Religion",
            "Science", "Art", "Music", "Travel", "Cooking"
    );

    private PublishBookScreen() {}

    public static Scene create(Navigator nav, User user) {
        navigator = nav;
        currentUser = user;

        // Title
        Label title = new Label("Publish New Book");
        title.getStyleClass().add("screen-title");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));

        // Main form
        VBox formBox = createForm();

        // Buttons
        HBox buttonBox = new HBox(20);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(20, 0, 30, 0));
        buttonBox.getStyleClass().add("button-bar");

        Button submitBtn = new Button("Submit for Approval");
        submitBtn.getStyleClass().add("primary-button");
        submitBtn.setPrefWidth(200);

        Button backBtn = new Button("Back");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setPrefWidth(150);
        backBtn.setOnAction(e -> {
            persistDraftQuietly();
            navigator.showAuthorDashboard(currentUser);
        });

        buttonBox.getChildren().addAll(submitBtn, backBtn);

        // Submit action
        submitBtn.setOnAction(e -> {
            if (!validateForm()) {
                return;
            }

            // Show confirmation dialog with full preview
            boolean confirmed = showPreviewDialog();
            if (!confirmed) {
                return;
            }

            // Convert selected genres to comma-separated string
            String genres = String.join(", ", genreListView.getSelectionModel().getSelectedItems());

            PublishService.PublishResult result = PublishService.submitBook(
                    currentUser,
                    titleField.getText().trim(),
                    genres,
                    descriptionArea.getText().trim(),
                    selectedBookFile,
                    selectedCoverFile
            );

            if (result.success()) {
                showSuccess(result.message());
                clearForm();
            } else {
                showError("Error", result.message());
            }
        });

        // Main content container
        VBox mainContent = new VBox(20, title, formBox, buttonBox);
        mainContent.setAlignment(Pos.TOP_CENTER);
        mainContent.setPadding(new Insets(30));
        mainContent.setMaxWidth(800);
        mainContent.getStyleClass().add("content-card");

        // Wrap in ScrollPane to make it scrollable
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(mainContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: #f5f5f5; -fx-background-color: #f5f5f5;");

        BorderPane root = new BorderPane();
        root.setCenter(scrollPane);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("app-root");

        // Create scene with default size - let the stage handle fullscreen
        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());

        // Add listener to handle full-screen properly
        scene.windowProperty().addListener((obs, oldWindow, newWindow) -> {
            if (newWindow != null) {
                newWindow.widthProperty().addListener((wObs, oldW, newW) -> {
                    // Adjust content if needed when window resizes
                    double width = newW.doubleValue();
                    if (width > 1000) {
                        mainContent.setMaxWidth(800);
                    } else {
                        mainContent.setMaxWidth(width - 100);
                    }
                });
                // Persist draft when the window is being closed so cover/book selection isn't lost
                newWindow.setOnCloseRequest(ev -> persistDraftQuietly());
            }
        });

        // Add CSS
        java.net.URL cssResource = PublishBookScreen.class.getResource("/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }

        try {
            PublishDraftDao.findByAuthor(currentUser.getId()).ifPresent(d -> {
                if (d.title() != null) {
                    titleField.setText(d.title());
                }
                if (d.summary() != null) {
                    descriptionArea.setText(d.summary());
                }
                if (d.filePath() != null && !d.filePath().isBlank()) {
                    File draftFile = new File(d.filePath());
                    if (draftFile.exists() && draftFile.canRead()) {
                        selectedBookFile = draftFile;
                        fileNameLabel.setText(draftFile.getName());
                        fileNameLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                        fileDisplayLabel.setText(draftFile.getName() + " (" +
                                formatFileSize(draftFile.length()) + ")");
                        fileDisplayLabel.setStyle("-fx-text-fill: #27ae60;");
                    }
                }
                if (d.genre() != null && !d.genre().isBlank()) {
                    genreListView.getSelectionModel().clearSelection();
                    for (String part : d.genre().split(",")) {
                        String g = part.trim();
                        int idx = AVAILABLE_GENRES.indexOf(g);
                        if (idx >= 0) {
                            genreListView.getSelectionModel().select(idx);
                        }
                    }
                    updateSelectedGenresDisplay();
                }
                if (d.coverPath() != null && !d.coverPath().isBlank()) {
                    File draftCover = new File(d.coverPath());
                    if (draftCover.exists() && draftCover.canRead()) {
                        selectedCoverFile = draftCover;
                        if (coverPathDisplay != null) {
                            coverPathDisplay.setText(draftCover.getName() + " (" + formatFileSize(draftCover.length()) + ")");
                            coverPathDisplay.setStyle("-fx-text-fill: #27ae60;");
                        }
                        if (coverNameLabel != null) {
                            coverNameLabel.setText(draftCover.getName());
                            coverNameLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                        }
                    }
                }
            });
        } catch (SQLException ignored) {
        }

        PauseTransition draftDebounce = new PauseTransition(Duration.seconds(1.2));
        draftDebounce.setOnFinished(ev -> {
            persistDraftQuietly();
        });
        Runnable bumpDraft = () -> draftDebounce.playFromStart();
        titleField.textProperty().addListener((a, b, c) -> bumpDraft.run());
        descriptionArea.textProperty().addListener((a, b, c) -> bumpDraft.run());
        genreListView.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<String>) c -> bumpDraft.run());

        return scene;
    }

    private static VBox createForm() {
        VBox formBox = new VBox(20);
        formBox.setPadding(new Insets(20));
        formBox.setPrefWidth(600);
        formBox.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                "-fx-border-radius: 10; -fx-border-color: #d0d7e2; -fx-border-width: 1;");

        Label formTitle = new Label("Book Details");
        formTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        formTitle.setStyle("-fx-text-fill: #2c3e50;");
        formTitle.setPadding(new Insets(0, 0, 10, 0));
        formTitle.setStyle(formTitle.getStyle() + "-fx-border-width: 0 0 1 0; -fx-border-color: #e0e4ec;");

        // Title field
        Label titleLabel = new Label("Title *");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #34495e;");
        titleField = new TextField();
        titleField.setPromptText("Enter book title");
        titleField.setPrefWidth(550);
        titleField.getStyleClass().add("text-field");

        // Author field (read-only)
        Label authorLabel = new Label("Author");
        authorLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #34495e;");
        TextField authorField = new TextField(currentUser.getFullName());
        authorField.setEditable(false);
        authorField.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #d0d7e2; -fx-border-radius: 5;");
        authorField.setPrefWidth(550);

        // Multi-genre selection - FIXED LIST VISIBILITY
        Label genreLabel = new Label("Genres * (to select multiple: Ctrl/ Command + Click)");
        genreLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #34495e;");

        // Create ListView with explicit size and ensure it's visible
        genreListView = new ListView<>();
        genreListView.getItems().addAll(AVAILABLE_GENRES);
        genreListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        genreListView.setPrefHeight(200);
        genreListView.setMinHeight(200);
        genreListView.setMaxHeight(200);
        genreListView.setPrefWidth(550);
        genreListView.setMinWidth(550);
        genreListView.setVisible(true);
        genreListView.setManaged(true);
        genreListView.getStyleClass().add("list-view");

        genreListView.setStyle("-fx-border-color: #3498db; -fx-border-width: 1;");

        // Selection info and clear button
        HBox genreControls = new HBox(15);
        genreControls.setAlignment(Pos.CENTER_LEFT);
        genreControls.setPadding(new Insets(5, 0, 5, 0));

        Label selectedGenresHeader = new Label("Selected genres:");
        selectedGenresHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d; -fx-font-size: 12px;");

        // Clear selection button
        Button clearGenreSelectionBtn = new Button("Clear All");
        clearGenreSelectionBtn.getStyleClass().add("secondary-button");
        clearGenreSelectionBtn.setPrefWidth(100);
        clearGenreSelectionBtn.setPrefHeight(30);
        clearGenreSelectionBtn.setOnAction(e -> {
            genreListView.getSelectionModel().clearSelection();
            updateSelectedGenresDisplay();
            persistDraftQuietly();
        });

        // Spacer to push button to the right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        genreControls.getChildren().addAll(selectedGenresHeader, spacer, clearGenreSelectionBtn);

        // Selected genres display
        selectedGenresLabel = new Label("None selected");
        selectedGenresLabel.setStyle("-fx-text-fill: #666; -fx-font-style: italic;");
        selectedGenresLabel.setWrapText(true);
        selectedGenresLabel.setPadding(new Insets(5, 0, 10, 0));

        // Add listeners to update display when selection changes
        genreListView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            updateSelectedGenresDisplay();
        });

        // Also update on mouse click to ensure display updates
        genreListView.setOnMouseClicked(e -> {
            updateSelectedGenresDisplay();
        });

        // Create a dedicated VBox for the genre section with proper spacing
        VBox genreBox = new VBox(10);
        genreBox.setPadding(new Insets(0, 0, 10, 0));
        genreBox.setFillWidth(true);
        genreBox.getChildren().addAll(genreListView, genreControls, selectedGenresLabel);

        // Description field
        Label descriptionLabel = new Label("Description/Abstract *");
        descriptionLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #34495e;");
        descriptionArea = new TextArea();
        descriptionArea.setPromptText("Enter book description, abstract, or summary...");
        descriptionArea.setPrefRowCount(10);
        descriptionArea.setPrefWidth(550);
        descriptionArea.setMinHeight(200);
        descriptionArea.setWrapText(true);
        descriptionArea.getStyleClass().add("text-area");

        generateSummaryButton = new Button("Generate Summary");
        generateSummaryButton.getStyleClass().add("secondary-button");
        generateSummaryButton.setPrefWidth(160);
        generateSummaryButton.setOnAction(e -> onGenerateSummary());

        summaryStatusLabel = new Label("Summary status: Draft");
        summaryStatusLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic;");

        HBox summaryActionBox = new HBox(10, generateSummaryButton);
        summaryActionBox.setAlignment(Pos.CENTER_LEFT);
        VBox summaryControlsBox = new VBox(8, summaryActionBox, summaryStatusLabel);

        // File selection - FIXED BUTTON
        Label fileLabel = new Label("Book File *");
        fileLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #34495e;");

        HBox fileBox = new HBox(10);
        fileBox.setAlignment(Pos.CENTER_LEFT);

        Button chooseFileBtn = new Button("Choose File");
        chooseFileBtn.getStyleClass().add("secondary-button");
        chooseFileBtn.setPrefWidth(120);

        fileNameLabel = new Label("No file selected");
        fileNameLabel.setStyle("-fx-text-fill: #666;");
        fileNameLabel.setPadding(new Insets(0, 0, 0, 5));

        fileBox.getChildren().addAll(chooseFileBtn, fileNameLabel);

// File display with clear button
        HBox fileDisplayBox = new HBox(15);
        fileDisplayBox.setAlignment(Pos.CENTER_LEFT);
        fileDisplayBox.setPadding(new Insets(5, 0, 5, 0));

        Label selectedFileHeader = new Label("Selected file:");
        selectedFileHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d; -fx-font-size: 12px;");

        fileDisplayLabel = new Label("None");
        fileDisplayLabel.setStyle("-fx-text-fill: #666; -fx-font-style: italic;");
        fileDisplayLabel.setWrapText(true);
        HBox.setHgrow(fileDisplayLabel, Priority.ALWAYS);

        Button clearFileBtn = new Button("Clear");
        clearFileBtn.getStyleClass().add("secondary-button");
        clearFileBtn.setPrefWidth(80);
        clearFileBtn.setPrefHeight(30);
        clearFileBtn.setOnAction(e -> {
            selectedBookFile = null;
            fileNameLabel.setText("No file selected");
            fileNameLabel.setStyle("-fx-text-fill: #666;");
            fileDisplayLabel.setText("None");
            fileDisplayLabel.setStyle("-fx-text-fill: #666; -fx-font-style: italic;");
            persistDraftQuietly();
        });

        fileDisplayBox.getChildren().addAll(selectedFileHeader, fileDisplayLabel, clearFileBtn);

// FIXED: File chooser action with proper owner window
        chooseFileBtn.setOnAction(e -> {
            try {
                // Get the current stage/window to use as owner
                Stage ownerStage = (Stage) chooseFileBtn.getScene().getWindow();

                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Select Book File");

                // Set initial directory to user's home
                String userHome = System.getProperty("user.home");
                File initialDir = new File(userHome);
                if (initialDir.exists() && initialDir.canRead()) {
                    fileChooser.setInitialDirectory(initialDir);
                }

                // Add extension filters
                FileChooser.ExtensionFilter pdfFilter =
                        new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf");
                FileChooser.ExtensionFilter txtFilter =
                        new FileChooser.ExtensionFilter("Text Files (*.txt)", "*.txt");
                FileChooser.ExtensionFilter docFilter =
                        new FileChooser.ExtensionFilter("Word Documents (*.doc, *.docx)", "*.doc", "*.docx");
                FileChooser.ExtensionFilter allFilter =
                        new FileChooser.ExtensionFilter("All Supported Files", "*.pdf", "*.txt", "*.doc", "*.docx");

                fileChooser.getExtensionFilters().addAll(pdfFilter, txtFilter, docFilter, allFilter);

                // Show the file chooser dialog with owner
                File selectedFile = fileChooser.showOpenDialog(ownerStage);

                if (selectedFile != null) {
                    // Validate file size
                    if (selectedFile.length() > 10 * 1024 * 1024) { // 10MB
                        showError("File Too Large", "File size must be less than 10MB. Your file: " +
                                formatFileSize(selectedFile.length()));
                        return;
                    }

                    // Validate file extension
                    String extension = getFileExtension(selectedFile);
                    if (!isValidFileType(extension)) {
                        showError("Invalid File Type",
                                "Please upload PDF, TXT, or DOC/DOCX files. Got: " + extension);
                        return;
                    }

                    selectedBookFile = selectedFile;
                    fileNameLabel.setText(selectedFile.getName());
                    fileNameLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");

                    // Update file display with size
                    fileDisplayLabel.setText(selectedFile.getName() + " (" +
                            formatFileSize(selectedFile.length()) + ")");
                    fileDisplayLabel.setStyle("-fx-text-fill: #27ae60;");
                    persistDraftQuietly();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                showError("Error", "Could not open file chooser: " + ex.getMessage());
            }
        });

        VBox fileSelectionBox = new VBox(8, fileBox, fileDisplayBox);

        Label coverLabel = new Label("Cover image (optional, JPG/PNG ≤ 2MB)");
        coverLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #34495e;");

        // Short name next to the button (like fileNameLabel)
        Button coverBtn = new Button("Choose cover");
        coverBtn.getStyleClass().add("secondary-button");
        coverBtn.setPrefWidth(120);

        coverNameLabel = new Label("No file selected");
        coverNameLabel.setStyle("-fx-text-fill: #666;");
        coverNameLabel.setPadding(new Insets(0, 0, 0, 5));

        HBox coverRow = new HBox(10, coverBtn, coverNameLabel);
        coverRow.setAlignment(Pos.CENTER_LEFT);

        // Detailed display with clear button (matches file display)
        HBox coverDisplayBox = new HBox(15);
        coverDisplayBox.setAlignment(Pos.CENTER_LEFT);
        coverDisplayBox.setPadding(new Insets(5, 0, 5, 0));

        Label selectedCoverHeader = new Label("Selected cover:");
        selectedCoverHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d; -fx-font-size: 12px;");

        Label coverDisplayLabel = new Label("None");
        coverDisplayLabel.setStyle("-fx-text-fill: #666; -fx-font-style: italic;");
        coverDisplayLabel.setWrapText(true);
        HBox.setHgrow(coverDisplayLabel, Priority.ALWAYS);

        // Keep the shared reference used elsewhere pointing to the detailed display
        coverPathDisplay = coverDisplayLabel;

        Button clearCoverBtn = new Button("Clear");
        clearCoverBtn.getStyleClass().add("secondary-button");
        clearCoverBtn.setPrefWidth(80);
        clearCoverBtn.setPrefHeight(30);
        clearCoverBtn.setOnAction(e -> {
            selectedCoverFile = null;
            if (coverNameLabel != null) {
                coverNameLabel.setText("No file selected");
                coverNameLabel.setStyle("-fx-text-fill: #666;");
            }
            coverDisplayLabel.setText("None");
            coverDisplayLabel.setStyle("-fx-text-fill: #666; -fx-font-style: italic;");
            persistDraftQuietly();
        });

        coverDisplayBox.getChildren().addAll(selectedCoverHeader, coverDisplayLabel, clearCoverBtn);

        // File chooser action with proper owner window and validation
        coverBtn.setOnAction(e -> {
            try {
                Stage ownerStage = (Stage) coverBtn.getScene().getWindow();
                FileChooser fc = new FileChooser();
                fc.setTitle("Cover image");
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
                File f = fc.showOpenDialog(ownerStage);
                if (f != null) {
                    if (f.length() > 2L * 1024 * 1024) {
                        showError("Too large", "Cover must be at most 2MB.");
                        return;
                    }
                    selectedCoverFile = f;
                    coverNameLabel.setText(f.getName());
                    coverNameLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    coverDisplayLabel.setText(f.getName() + " (" + formatFileSize(f.length()) + ")");
                    coverDisplayLabel.setStyle("-fx-text-fill: #27ae60;");
                    persistDraftQuietly();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                showError("Error", "Could not open file chooser: " + ex.getMessage());
            }
        });

        VBox coverBox = new VBox(6, coverLabel, coverRow, coverDisplayBox);
        // Required fields note
        Label requiredNote = new Label("* Required fields");
        requiredNote.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");
        requiredNote.setPadding(new Insets(10, 0, 0, 0));

        formBox.getChildren().addAll(
                formTitle,
                titleLabel, titleField,
                authorLabel, authorField,
                genreLabel, genreBox,
                fileLabel, fileSelectionBox,
                descriptionLabel, descriptionArea,
                summaryControlsBox,
                coverBox,
                requiredNote
        );

        return formBox;
    }

    private static void updateSelectedGenresDisplay() {
        var selectedGenres = genreListView.getSelectionModel().getSelectedItems();
        if (selectedGenres == null || selectedGenres.isEmpty()) {
            selectedGenresLabel.setText("None selected");
            selectedGenresLabel.setStyle("-fx-text-fill: #666; -fx-font-style: italic;");
        } else {
            int count = selectedGenres.size();
            if (count == 1) {
                selectedGenresLabel.setText("1 genre selected: " + selectedGenres.getFirst());
            } else {
                String genresText = String.join(", ", selectedGenres);
                selectedGenresLabel.setText(count + " genres selected: " + genresText);
            }
            selectedGenresLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: normal;");
        }
    }

    private static boolean validateForm() {
        List<String> errors = new java.util.ArrayList<>();

        String title = titleField.getText();
        if (title == null || title.trim().isEmpty()) {
            errors.add("Book title is required");
        }

        var selectedGenres = genreListView.getSelectionModel().getSelectedItems();
        if (selectedGenres == null || selectedGenres.isEmpty()) {
            errors.add("Please select at least one genre");
        }

        String description = descriptionArea.getText();
        if (description == null || description.trim().isEmpty()) {
            errors.add("Description is required");
        }

        if (selectedBookFile == null) {
            errors.add("Please select a book file");
        }

        if (!errors.isEmpty()) {
            showError("Validation Error", String.join("\n", errors));
            return false;
        }

        return true;
    }

    private static boolean showPreviewDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Confirm Book Submission");

        VBox dialogContent = new VBox(20);
        dialogContent.setPadding(new Insets(25));
        dialogContent.setStyle("-fx-background-color: #f5f7fa;");

        Label header = new Label("📖 Review Your Book");
        header.setFont(Font.font("System", FontWeight.BOLD, 20));
        header.setStyle("-fx-text-fill: #2c3e50;");

        VBox previewBox = new VBox(15);
        previewBox.setPadding(new Insets(20));
        previewBox.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                "-fx-border-color: #d0d7e2; -fx-border-width: 1;");

        // Title row
        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label titleLbl = new Label("Title:");
        titleLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d; -fx-min-width: 70;");
        Label titleVal = new Label(titleField.getText().trim());
        titleVal.setStyle("-fx-text-fill: #2c3e50; -fx-font-weight: bold;");
        titleVal.setWrapText(true);
        titleRow.getChildren().addAll(titleLbl, titleVal);

        // Author row
        HBox authorRow = new HBox(10);
        authorRow.setAlignment(Pos.CENTER_LEFT);
        Label authorLbl = new Label("Author:");
        authorLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d; -fx-min-width: 70;");
        Label authorVal = new Label(currentUser.getFullName());
        authorVal.setStyle("-fx-text-fill: #2c3e50;");
        authorVal.setWrapText(true);
        authorRow.getChildren().addAll(authorLbl, authorVal);

        // Genres row - use VBox for better multi-line display
        VBox genresBox = new VBox(5);
        Label genresLbl = new Label("Genres:");
        genresLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
        String genresText = String.join(", ", genreListView.getSelectionModel().getSelectedItems());
        Label genresVal = new Label(genresText);
        genresVal.setStyle("-fx-text-fill: #2c3e50;");
        genresVal.setWrapText(true);
        genresBox.getChildren().addAll(genresLbl, genresVal);

        // File row
        HBox fileRow = new HBox(10);
        fileRow.setAlignment(Pos.CENTER_LEFT);
        Label fileLbl = new Label("File:");
        fileLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d; -fx-min-width: 70;");
        Label fileVal = new Label(selectedBookFile.getName() + " (" +
                formatFileSize(selectedBookFile.length()) + ")");
        fileVal.setStyle("-fx-text-fill: #2c3e50;");
        fileVal.setWrapText(true);
        fileRow.getChildren().addAll(fileLbl, fileVal);

        // Description
        Label descHeader = new Label("Description:");
        descHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d;");

        TextArea descPreview = new TextArea(descriptionArea.getText().trim());
        descPreview.setEditable(false);
        descPreview.setWrapText(true);
        descPreview.setPrefRowCount(5);
        descPreview.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #d0d7e2;");

        // Build preview area with cover image on the left
        VBox details = new VBox(10, titleRow, authorRow, genresBox, fileRow, descHeader, descPreview);
        details.setPrefWidth(420);

        Image coverImg = null;
        try {
            if (selectedCoverFile != null) {
                coverImg = new Image(selectedCoverFile.toURI().toString(), 120, 180, true, true);
            }
        } catch (Exception ignored) {}
        if (coverImg == null || coverImg.isError()) {
            var u = PublishBookScreen.class.getResource("/images/default-cover.png");
            if (u != null) coverImg = new Image(u.toExternalForm(), 120, 180, true, true);
        }
        ImageView coverView = new ImageView(coverImg);

        HBox previewWithCover = new HBox(20, coverView, details);
        previewBox.getChildren().add(previewWithCover);

        Label confirmMsg = new Label("Are you sure you want to submit this book for approval?");
        confirmMsg.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold; -fx-font-size: 14px;");
        confirmMsg.setAlignment(Pos.CENTER);
        confirmMsg.setWrapText(true);

        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);

        Button submitBtn = new Button("✅ Yes, Submit");
        submitBtn.getStyleClass().add("primary-button");
        submitBtn.setOnAction(e -> {
            dialog.setUserData(true);
            dialog.close();
        });

        Button cancelBtn = new Button("❌ Back");
        cancelBtn.getStyleClass().add("secondary-button");
        cancelBtn.setOnAction(e -> {
            dialog.setUserData(false);
            dialog.close();
        });

        buttonBox.getChildren().addAll(submitBtn, cancelBtn);

        dialogContent.getChildren().addAll(header, previewBox, confirmMsg, buttonBox);

        // Make dialog scrollable
        ScrollPane dialogScrollPane = new ScrollPane();
        dialogScrollPane.setContent(dialogContent);
        dialogScrollPane.setFitToWidth(true);
        dialogScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        dialogScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Scene dialogScene = new Scene(dialogScrollPane, 600, 700);
        dialog.setScene(dialogScene);
        dialog.showAndWait();

        return dialog.getUserData() != null && (boolean) dialog.getUserData();
    }

    private static void clearForm() {
        titleField.clear();
        genreListView.getSelectionModel().clearSelection();
        descriptionArea.clear();
        selectedBookFile = null;
        fileNameLabel.setText("No file selected");
        fileNameLabel.setStyle("-fx-text-fill: #666;");
        fileDisplayLabel.setText("None");
        fileDisplayLabel.setStyle("-fx-text-fill: #666; -fx-font-style: italic;");
        selectedGenresLabel.setText("None selected");
        selectedGenresLabel.setStyle("-fx-text-fill: #666; -fx-font-style: italic;");
        selectedCoverFile = null;
        if (coverNameLabel != null) {
            coverNameLabel.setText("No file selected");
            coverNameLabel.setStyle("-fx-text-fill: #666;");
        }
        if (coverPathDisplay != null) {
            coverPathDisplay.setText("None");
            coverPathDisplay.setStyle("-fx-text-fill: #666; -fx-font-style: italic;");
        }
        if (summaryStatusLabel != null) {
            summaryStatusLabel.setText("Summary status: Draft");
            summaryStatusLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic;");
        }
    }

    private static void persistDraftQuietly() {
        try {
            String title = titleField != null ? titleField.getText() : null;
            String genres = genreListView != null
                    ? String.join(", ", genreListView.getSelectionModel().getSelectedItems())
                    : null;
            String summary = descriptionArea != null ? descriptionArea.getText() : null;
            String filePath = selectedBookFile != null ? selectedBookFile.getAbsolutePath() : null;
            String coverPath = selectedCoverFile != null ? selectedCoverFile.getAbsolutePath() : null;

            if (isBlank(title) && isBlank(genres) && isBlank(summary) && isBlank(filePath) && isBlank(coverPath)) {
                PublishDraftDao.deleteForAuthor(currentUser.getId());
                return;
            }

            PublishDraftDao.upsert(
                currentUser.getId(),
                title,
                genres,
                summary,
                filePath,
                coverPath,
                Instant.now().toString()
            );
        } catch (SQLException ignored) {
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    private static void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
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

    private static boolean showConfirmation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private static void onGenerateSummary() {
        if (selectedBookFile == null) {
            showError("No File Selected", "Please choose a book file before generating a summary.");
            return;
        }
        if (!BookPreviewUtil.isSupportedPreviewType(selectedBookFile.getAbsolutePath())) {
            showError("Unsupported File", "Summary generation supports PDF, TXT, DOC, and DOCX files.");
            return;
        }

        generateSummaryButton.setDisable(true);
        String originalText = generateSummaryButton.getText();
        generateSummaryButton.setText("Generating...");
        summaryStatusLabel.setText("Summary status: Generating...");
        summaryStatusLabel.setStyle("-fx-text-fill: #2980b9;");

        Task<BookSummaryService.SummaryResult> task = new Task<>() {
            @Override
            protected BookSummaryService.SummaryResult call() {
                BookSummaryService service = new BookSummaryService();
                return service.generateSummaryFromBookFile(selectedBookFile.getAbsolutePath());
            }
        };

        task.setOnSucceeded(e -> {
            BookSummaryService.SummaryResult result = task.getValue();
            if (result.success()) {
                descriptionArea.setText(result.summary());
                summaryStatusLabel.setText("Summary status: Generated");
                summaryStatusLabel.setStyle("-fx-text-fill: #27ae60;");
                showSuccess(result.message());
                persistDraftQuietly();
            } else {
                summaryStatusLabel.setText("Summary status: Draft");
                summaryStatusLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic;");
                showError("Summary Generation Failed", result.message());
            }
            generateSummaryButton.setText(originalText);
            generateSummaryButton.setDisable(false);
        });

        task.setOnFailed(e -> {
            summaryStatusLabel.setText("Summary status: Draft");
            summaryStatusLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic;");
            generateSummaryButton.setText(originalText);
            generateSummaryButton.setDisable(false);
            Throwable ex = task.getException();
            String message = ex == null ? "Unexpected error during summary generation." : ex.getMessage();
            showError("Summary Generation Failed", message);
        });

        Thread worker = new Thread(task, "summary-generation-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private static String getFileExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            return name.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    private static boolean isValidFileType(String extension) {
        return extension.equals("pdf") ||
                extension.equals("txt") ||
                extension.equals("doc") ||
                extension.equals("docx");
    }
}