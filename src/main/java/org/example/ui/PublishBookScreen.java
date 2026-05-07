package org.example.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.example.app.Navigator;
import org.example.db.BookChangeLogDao;
import org.example.db.BookDao;
import org.example.db.BookRequestDao;
import org.example.db.PublishDraftDao;
import org.example.domain.Book;
import org.example.domain.BookRequest;
import org.example.domain.User;
import org.example.service.BookSummaryService;
import org.example.service.NotificationService;
import org.example.service.PublishService;
import org.example.util.BookPreviewUtil;

import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

public final class PublishBookScreen {

    private static File selectedBookFile;
    private static File selectedCoverFile;
    private static Label fileNameLabel;
    private static TextField titleField;
    private static TextField authorNameField;
    private static ListView<String> genreListView;
    private static TextArea descriptionArea;
    private static User currentUser;
    private static Navigator navigator;
    private static boolean librarianPublishMode;
    private static Long editingBookId;
    private static Book editingBookSnapshot;
    /** When set, publishing completes a book request (catalog insert + approve + notify). */
    private static Long pendingBookRequestApproveId;
    private static TextArea bookRequestApproveNotesArea;

    // Display components for selections
    private static Label selectedGenresLabel;
    private static Label fileDisplayLabel;
    private static Label coverPathDisplay;
    private static Label coverNameLabel;
    private static Label summaryStatusLabel;
    private static Button generateSummaryButton;
    private static Button cancelSummaryButton;
    private static ComboBox<BookSummaryService.SummaryStyle> summaryStyleComboBox;
    private static boolean internalSummaryProgrammaticUpdate;
    private static Task<BookSummaryService.SummaryResult> activeSummaryTask;
    private static Thread activeSummaryThread;
    private static final String LIBRARIAN_PUBLISHED_UPLOAD_DIR = System.getProperty("user.home")
            + File.separator + "library_uploads"
            + File.separator + "published";
    private static final String LIBRARIAN_PUBLISHED_COVER_DIR = "data" + File.separator + "covers";

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
        return create(nav, user, false, null, null);
    }

    public static Scene create(Navigator nav, User user, boolean librarianMode) {
        return create(nav, user, librarianMode, null, null);
    }

    public static Scene create(Navigator nav, User user, boolean librarianMode, Long editBookId) {
        return create(nav, user, librarianMode, editBookId, null);
    }

    /**
     * @param bookRequestApproveId when non-null (librarian flow), form is prefilled from that request
     *                             and publish approves the request after adding the book to the catalog.
     */
    public static Scene create(Navigator nav, User user, boolean librarianMode, Long editBookId,
                               Long bookRequestApproveId) {
        navigator = nav;
        currentUser = user;
        librarianPublishMode = librarianMode;
        editingBookId = editBookId;
        editingBookSnapshot = null;
        pendingBookRequestApproveId = bookRequestApproveId;

        // Title
        Label title = new Label(screenTitleText());
        title.getStyleClass().add("screen-title");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));

        // Main form
        VBox formBox = createForm();

        // Buttons
        HBox buttonBox = new HBox(20);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(20, 0, 30, 0));
        buttonBox.getStyleClass().add("button-bar");

        Button submitBtn = new Button(primarySubmitButtonText());
        submitBtn.getStyleClass().add("primary-button");
        submitBtn.setPrefWidth(200);

        Button clearFormBtn = new Button("Clear Form");
        clearFormBtn.getStyleClass().add("secondary-button");
        clearFormBtn.setPrefWidth(140);
        clearFormBtn.setOnAction(e -> {
            if (showConfirmation("Clear Form", "Clear all fields in this publishing form?")) {
                clearForm();
            }
        });

        // Navigation is handled by global menu; remove per-screen Back button.
        buttonBox.getChildren().addAll(submitBtn, clearFormBtn);

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

            PublishService.PublishResult result;
            if (isEditingLibrarianBook()) {
                result = updateBookAsLibrarian(
                        currentUser,
                        editingBookId,
                        titleField.getText().trim(),
                        authorNameField == null ? "" : authorNameField.getText().trim(),
                        genres,
                        descriptionArea.getText().trim(),
                        selectedBookFile,
                        selectedCoverFile
                );
            } else if (pendingBookRequestApproveId != null && pendingBookRequestApproveId > 0) {
                String notes = bookRequestApproveNotesArea == null
                        ? "" : bookRequestApproveNotesArea.getText().trim();
                result = publishFromBookRequest(
                        currentUser,
                        pendingBookRequestApproveId,
                        notes,
                        titleField.getText().trim(),
                        authorNameField == null ? "" : authorNameField.getText().trim(),
                        genres,
                        descriptionArea.getText().trim(),
                        selectedBookFile,
                        selectedCoverFile
                );
            } else if (librarianPublishMode) {
                result = submitBookAsLibrarian(
                        currentUser,
                        titleField.getText().trim(),
                        authorNameField == null ? "" : authorNameField.getText().trim(),
                        genres,
                        descriptionArea.getText().trim(),
                        selectedBookFile,
                        selectedCoverFile
                );
            } else {
                result = PublishService.submitBook(
                        currentUser,
                        titleField.getText().trim(),
                        genres,
                        descriptionArea.getText().trim(),
                        selectedBookFile,
                        selectedCoverFile
                );
            }

            if (result.success()) {
                showSuccess(result.message());
                if (isEditingLibrarianBook()) {
                    navigator.showLibrarianCatalog(currentUser);
                } else if (pendingBookRequestApproveId != null && pendingBookRequestApproveId > 0) {
                    pendingBookRequestApproveId = null;
                    navigator.showLibrarianManageBookRequests(currentUser);
                } else {
                    clearForm();
                }
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

        if (!librarianPublishMode) {
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
        }
        if (isEditingLibrarianBook()) {
            preloadForLibrarianEdit();
        } else if (pendingBookRequestApproveId != null && pendingBookRequestApproveId > 0) {
            preloadFromBookRequest(pendingBookRequestApproveId);
        }

        PauseTransition draftDebounce = new PauseTransition(Duration.seconds(1.2));
        draftDebounce.setOnFinished(ev -> {
            persistDraftQuietly();
        });
        Runnable bumpDraft = () -> draftDebounce.playFromStart();
        titleField.textProperty().addListener((a, b, c) -> bumpDraft.run());
        descriptionArea.textProperty().addListener((a, b, c) -> bumpDraft.run());
        descriptionArea.textProperty().addListener((a, b, c) -> {
            if (internalSummaryProgrammaticUpdate) {
                return;
            }
            if (c != null && !c.isBlank()) {
                markSummaryDraft("Summary status: Draft (edited)");
            }
        });
        genreListView.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<String>) c -> bumpDraft.run());
        summaryStyleComboBox.valueProperty().addListener((a, b, c) -> {
            if (c != null && c != b) {
                markSummaryDraft("Summary status: Draft (" + c.label() + " style selected)");
            }
        });
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

        // Author field
        Label authorLabel = new Label(librarianPublishMode ? "Author Name *" : "Author");
        authorLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #34495e;");
        authorNameField = new TextField(librarianPublishMode ? "" : currentUser.getFullName());
        authorNameField.setEditable(librarianPublishMode);
        if (librarianPublishMode) {
            authorNameField.setPromptText("Enter author full name");
            authorNameField.setStyle("-fx-border-color: #d0d7e2; -fx-border-radius: 5;");
        } else {
            authorNameField.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #d0d7e2; -fx-border-radius: 5;");
        }
        authorNameField.setPrefWidth(550);

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
        generateSummaryButton.setOnAction(e -> onGenerateSummary(false));

        cancelSummaryButton = new Button("Cancel Generation");
        cancelSummaryButton.getStyleClass().add("secondary-button");
        cancelSummaryButton.setPrefWidth(160);
        cancelSummaryButton.setDisable(true);
        cancelSummaryButton.setOnAction(e -> onCancelSummaryGeneration());

        Label summaryStyleLabel = new Label("Summary style:");
        summaryStyleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #34495e;");
        summaryStyleComboBox = new ComboBox<>();
        summaryStyleComboBox.getItems().addAll(BookSummaryService.SummaryStyle.values());
        summaryStyleComboBox.setValue(BookSummaryService.SummaryStyle.MEDIUM);
        summaryStyleComboBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(BookSummaryService.SummaryStyle item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        });
        summaryStyleComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(BookSummaryService.SummaryStyle item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        });
        summaryStyleComboBox.setPrefWidth(140);

        summaryStatusLabel = new Label("Summary status: Draft");
        summaryStatusLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic;");

        HBox summaryActionBox = new HBox(
                10,
                summaryStyleLabel,
                summaryStyleComboBox,
                generateSummaryButton,
                cancelSummaryButton);
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
                FileChooser.ExtensionFilter allFilter = librarianPublishMode
                        ? new FileChooser.ExtensionFilter("All Supported Files",
                                "*.pdf", "*.txt", "*.doc", "*.docx", "*.epub")
                        : new FileChooser.ExtensionFilter("All Supported Files",
                                "*.pdf", "*.txt", "*.doc", "*.docx");
                if (librarianPublishMode) {
                    FileChooser.ExtensionFilter epubFilter =
                            new FileChooser.ExtensionFilter("EPUB (*.epub)", "*.epub");
                    fileChooser.getExtensionFilters().addAll(pdfFilter, txtFilter, docFilter, epubFilter, allFilter);
                } else {
                    fileChooser.getExtensionFilters().addAll(pdfFilter, txtFilter, docFilter, allFilter);
                }

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
                    markSummaryDraft("Summary status: Draft (new file selected)");
                    persistDraftQuietly();
                    if (!librarianPublishMode && BookPreviewUtil.isSupportedPreviewType(selectedBookFile.getAbsolutePath())) {
                        onGenerateSummary(true);
                    }
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

        bookRequestApproveNotesArea = null;
        formBox.getChildren().addAll(
                formTitle,
                titleLabel, titleField,
                authorLabel, authorNameField,
                genreLabel, genreBox,
                fileLabel, fileSelectionBox,
                descriptionLabel, descriptionArea,
                summaryControlsBox,
                coverBox
        );
        if (pendingBookRequestApproveId != null && pendingBookRequestApproveId > 0) {
            Label reqNotesLabel = new Label("Request approval notes (optional)");
            reqNotesLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #34495e;");
            bookRequestApproveNotesArea = new TextArea();
            bookRequestApproveNotesArea.setPromptText("Stored on the book request when you publish…");
            bookRequestApproveNotesArea.setWrapText(true);
            bookRequestApproveNotesArea.setPrefRowCount(3);
            bookRequestApproveNotesArea.setPrefWidth(550);
            bookRequestApproveNotesArea.getStyleClass().add("text-area");
            formBox.getChildren().addAll(reqNotesLabel, bookRequestApproveNotesArea);
        }
        formBox.getChildren().add(requiredNote);

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
        if (librarianPublishMode) {
            String authorName = authorNameField == null ? "" : authorNameField.getText();
            if (authorName == null || authorName.trim().isEmpty()) {
                errors.add("Author name is required");
            }
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
        } else {
            String ext = getFileExtension(selectedBookFile);
            if (!isValidFileType(ext)) {
                errors.add("Unsupported book file type: ." + ext);
            }
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
        String previewAuthor = librarianPublishMode
                ? (authorNameField == null ? "" : authorNameField.getText().trim())
                : currentUser.getFullName();
        Label authorVal = new Label(previewAuthor);
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

        Label confirmMsg = new Label(isEditingLibrarianBook()
                ? "Are you sure you want to save these published book changes?"
                : (pendingBookRequestApproveId != null && pendingBookRequestApproveId > 0)
                ? "Publish this book to the catalog and mark the book request as approved?"
                : librarianPublishMode
                ? "Are you sure you want to publish this book to the catalog?"
                : "Are you sure you want to submit this book for approval?");
        confirmMsg.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold; -fx-font-size: 14px;");
        confirmMsg.setAlignment(Pos.CENTER);
        confirmMsg.setWrapText(true);

        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);

        Button submitBtn = new Button(isEditingLibrarianBook()
                ? "Yes, Save Changes"
                : (pendingBookRequestApproveId != null && pendingBookRequestApproveId > 0)
                ? "Yes, publish & approve"
                : (librarianPublishMode ? "Yes, Publish" : "✅ Yes, Submit"));
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
        if (authorNameField != null) {
            authorNameField.setText(librarianPublishMode ? "" : currentUser.getFullName());
        }
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
        if (bookRequestApproveNotesArea != null) {
            bookRequestApproveNotesArea.clear();
        }
    }

    private static void persistDraftQuietly() {
        if (librarianPublishMode) {
            return;
        }
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

    private static void showInfo(String title, String message) {
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

    private static boolean showConfirmation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private static void onGenerateSummary(boolean autoTriggered) {
        if (selectedBookFile == null) {
            if (!autoTriggered) {
                showError("No File Selected", "Please choose a book file before generating a summary.");
            }
            return;
        }
        if (!BookPreviewUtil.isSupportedPreviewType(selectedBookFile.getAbsolutePath())) {
            if (!autoTriggered) {
                showError("Unsupported File", "Summary generation supports PDF, TXT, DOC, and DOCX files.");
            }
            return;
        }

        generateSummaryButton.setDisable(true);
        if (cancelSummaryButton != null) {
            cancelSummaryButton.setDisable(false);
        }
        if (summaryStyleComboBox != null) {
            summaryStyleComboBox.setDisable(true);
        }
        String originalText = generateSummaryButton.getText();
        generateSummaryButton.setText("Generating...");
        summaryStatusLabel.setText("Summary status: Generating...");
        summaryStatusLabel.setStyle("-fx-text-fill: #2980b9;");
        BookSummaryService.SummaryStyle selectedStyle = summaryStyleComboBox != null && summaryStyleComboBox.getValue() != null
                ? summaryStyleComboBox.getValue()
                : BookSummaryService.SummaryStyle.MEDIUM;

        Task<BookSummaryService.SummaryResult> task = new Task<>() {
            @Override
            protected BookSummaryService.SummaryResult call() {
                BookSummaryService service = new BookSummaryService();
                String title = titleField == null ? "" : titleField.getText();
                String author = authorNameField == null ? "" : authorNameField.getText();
                return service.generateSummaryFromBookFile(
                        selectedBookFile.getAbsolutePath(),
                        selectedStyle,
                        title,
                        author);
            }
        };
        activeSummaryTask = task;

        task.setOnSucceeded(e -> {
            BookSummaryService.SummaryResult result = task.getValue();
            if (result.success()) {
                internalSummaryProgrammaticUpdate = true;
                descriptionArea.setText(result.summary());
                internalSummaryProgrammaticUpdate = false;
                String provider = result.provider() == null ? "unknown" : result.provider();
                if ("ollama".equalsIgnoreCase(provider)) {
                    String model = result.modelUsed() == null || result.modelUsed().isBlank()
                            ? ""
                            : " (" + result.modelUsed() + ")";
                    summaryStatusLabel.setText("Summary status: Generated by Ollama" + model + " (not finalized)");
                } else if ("catalog".equalsIgnoreCase(provider)) {
                    summaryStatusLabel.setText("Summary status: Generated from catalog metadata (not finalized)");
                } else if ("hf".equalsIgnoreCase(provider)) {
                    summaryStatusLabel.setText("Summary status: Generated by HF (not finalized)");
                } else {
                    String reason = result.fallbackReason() == null || result.fallbackReason().isBlank()
                            ? ""
                            : " - " + result.fallbackReason();
                    summaryStatusLabel.setText("Summary status: Generated locally (fallback)" + reason + " (not finalized)");
                }
                summaryStatusLabel.setStyle("-fx-text-fill: #27ae60;");
                if (!autoTriggered) {
                    showSuccess(result.message());
                }
                persistDraftQuietly();
            } else {
                String base = "Summary status: Generation failed";
                if (result.message() != null && !result.message().isBlank()) {
                    base += " - " + result.message();
                }
                summaryStatusLabel.setText(base);
                summaryStatusLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-style: italic;");
                if (!autoTriggered) {
                    showError("Summary Generation Failed", result.message());
                }
            }
            generateSummaryButton.setText(originalText);
            generateSummaryButton.setDisable(false);
            if (cancelSummaryButton != null) {
                cancelSummaryButton.setDisable(true);
            }
            if (summaryStyleComboBox != null) {
                summaryStyleComboBox.setDisable(false);
            }
            activeSummaryTask = null;
            activeSummaryThread = null;
        });

        task.setOnFailed(e -> {
            summaryStatusLabel.setText("Summary status: Generation failed (unexpected error)");
            summaryStatusLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-style: italic;");
            generateSummaryButton.setText(originalText);
            generateSummaryButton.setDisable(false);
            if (cancelSummaryButton != null) {
                cancelSummaryButton.setDisable(true);
            }
            if (summaryStyleComboBox != null) {
                summaryStyleComboBox.setDisable(false);
            }
            Throwable ex = task.getException();
            String message = ex == null ? "Unexpected error during summary generation." : ex.getMessage();
            if (!autoTriggered) {
                showError("Summary Generation Failed", message);
            }
            activeSummaryTask = null;
            activeSummaryThread = null;
        });

        Thread worker = new Thread(task, "summary-generation-worker");
        worker.setDaemon(true);
        activeSummaryThread = worker;
        worker.start();
    }

    private static void onCancelSummaryGeneration() {
        if (activeSummaryTask != null && activeSummaryTask.isRunning()) {
            activeSummaryTask.cancel(true);
        }
        if (activeSummaryThread != null && activeSummaryThread.isAlive()) {
            activeSummaryThread.interrupt();
        }
        summaryStatusLabel.setText("Summary status: Generation cancelled");
        summaryStatusLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic;");
        if (generateSummaryButton != null) {
            generateSummaryButton.setText("Generate Summary");
            generateSummaryButton.setDisable(false);
        }
        if (cancelSummaryButton != null) {
            cancelSummaryButton.setDisable(true);
        }
        if (summaryStyleComboBox != null) {
            summaryStyleComboBox.setDisable(false);
        }
        activeSummaryTask = null;
        activeSummaryThread = null;
    }

    private static void markSummaryDraft(String statusText) {
        if (summaryStatusLabel != null) {
            summaryStatusLabel.setText(statusText);
            summaryStatusLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic;");
        }
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
                extension.equals("docx") ||
                (librarianPublishMode && extension.equals("epub"));
    }

    private static PublishService.PublishResult submitBookAsLibrarian(User librarian,
                                                                      String title,
                                                                      String authorName,
                                                                      String genre,
                                                                      String description,
                                                                      File bookFile,
                                                                      File coverFile) {
        try {
            if (librarian == null || librarian.getId() <= 0) {
                return new PublishService.PublishResult(false, "Librarian account is invalid.");
            }
            Files.createDirectories(Paths.get(LIBRARIAN_PUBLISHED_UPLOAD_DIR));
            String safeBookName = sanitizeFilename(bookFile.getName());
            String uniqueBookName = System.currentTimeMillis() + "_" + safeBookName;
            Path bookDest = Paths.get(LIBRARIAN_PUBLISHED_UPLOAD_DIR, uniqueBookName);
            Files.copy(bookFile.toPath(), bookDest, StandardCopyOption.REPLACE_EXISTING);

            String coverPath = null;
            if (coverFile != null) {
                Files.createDirectories(Paths.get(LIBRARIAN_PUBLISHED_COVER_DIR));
                String uniqueCoverName = System.currentTimeMillis() + "_" + sanitizeFilename(coverFile.getName());
                Path coverDest = Paths.get(LIBRARIAN_PUBLISHED_COVER_DIR, uniqueCoverName);
                Files.copy(coverFile.toPath(), coverDest, StandardCopyOption.REPLACE_EXISTING);
                coverPath = coverDest.toAbsolutePath().toString();
            }

            BookDao.insert(
                    title,
                    librarian.getId(),
                    authorName,
                    genre,
                    description,
                    bookDest.toAbsolutePath().toString(),
                    Instant.now().toString(),
                    coverPath
            );
            return new PublishService.PublishResult(true, "Book published successfully.");
        } catch (IOException | SQLException ex) {
            return new PublishService.PublishResult(false, "Could not publish book: " + ex.getMessage());
        }
    }

    private static PublishService.PublishResult updateBookAsLibrarian(User librarian,
                                                                      Long bookId,
                                                                      String title,
                                                                      String authorName,
                                                                      String genre,
                                                                      String description,
                                                                      File bookFile,
                                                                      File coverFile) {
        if (bookId == null || bookId <= 0) {
            return new PublishService.PublishResult(false, "Invalid book selected for editing.");
        }
        if (librarian == null || librarian.getId() <= 0) {
            return new PublishService.PublishResult(false, "Librarian account is invalid.");
        }
        try {
            String storedBookPath = persistBookFileForLibrarian(bookFile, editingBookSnapshot == null ? null : editingBookSnapshot.getFilePath());
            String storedCoverPath = persistCoverFileForLibrarian(coverFile, editingBookSnapshot == null ? null : editingBookSnapshot.getCoverImagePath());
            
            // Log all changes BEFORE the update (while we still have the original values)
            try {
                if (editingBookSnapshot != null) {
                    // Record title change
                    if (title != null && !title.equals(editingBookSnapshot.getTitle())) {
                        BookChangeLogDao.recordChange(
                            bookId,
                            librarian.getId(),
                            librarian.getFullName(),
                            "EDIT",
                            "title",
                            editingBookSnapshot.getTitle(),
                            title,
                            null
                        );
                    }
                    
                    // Record genre change
                    if (genre != null && !genre.equals(editingBookSnapshot.getGenre())) {
                        BookChangeLogDao.recordChange(
                            bookId,
                            librarian.getId(),
                            librarian.getFullName(),
                            "EDIT",
                            "genre",
                            editingBookSnapshot.getGenre(),
                            genre,
                            null
                        );
                    }
                    
                    // Record summary change
                    if (description != null && !description.equals(editingBookSnapshot.getSummary())) {
                        BookChangeLogDao.recordChange(
                            bookId,
                            librarian.getId(),
                            librarian.getFullName(),
                            "EDIT",
                            "summary",
                            editingBookSnapshot.getSummary(),
                            description,
                            null
                        );
                    }
                }
            } catch (SQLException ignored) {
                // Non-fatal: keep edit successful even if change logging fails
            }
            
            BookDao.updatePublishedByLibrarian(
                    bookId,
                    title,
                    editingBookSnapshot == null ? librarian.getId() : editingBookSnapshot.getAuthorUserId(),
                    authorName,
                    genre,
                    description,
                    storedBookPath,
                    storedCoverPath
            );
            
            if (editingBookSnapshot != null
                    && editingBookSnapshot.getAuthorUserId() > 0
                    && editingBookSnapshot.getAuthorUserId() != librarian.getId()) {
                try {
                    NotificationService.notifyAuthorBookUpdatedByLibrarian(
                            editingBookSnapshot.getAuthorUserId(),
                            title
                    );
                } catch (SQLException ignored) {
                    // Non-fatal: keep edit successful even if notification fails.
                }
            }
            return new PublishService.PublishResult(true, "Published book updated successfully.");
        } catch (IOException | SQLException ex) {
            return new PublishService.PublishResult(false, "Could not update book: " + ex.getMessage());
        }
    }

    private static String persistBookFileForLibrarian(File bookFile, String originalPath) throws IOException {
        if (bookFile != null && originalPath != null && bookFile.getAbsolutePath().equals(originalPath)) {
            return originalPath;
        }
        Files.createDirectories(Paths.get(LIBRARIAN_PUBLISHED_UPLOAD_DIR));
        String safeBookName = sanitizeFilename(bookFile.getName());
        String uniqueBookName = System.currentTimeMillis() + "_" + safeBookName;
        Path bookDest = Paths.get(LIBRARIAN_PUBLISHED_UPLOAD_DIR, uniqueBookName);
        Files.copy(bookFile.toPath(), bookDest, StandardCopyOption.REPLACE_EXISTING);
        return bookDest.toAbsolutePath().toString();
    }

    private static String persistCoverFileForLibrarian(File coverFile, String originalPath) throws IOException {
        if (coverFile == null) {
            return null;
        }
        if (originalPath != null && coverFile.getAbsolutePath().equals(originalPath)) {
            return originalPath;
        }
        Files.createDirectories(Paths.get(LIBRARIAN_PUBLISHED_COVER_DIR));
        String uniqueCoverName = System.currentTimeMillis() + "_" + sanitizeFilename(coverFile.getName());
        Path coverDest = Paths.get(LIBRARIAN_PUBLISHED_COVER_DIR, uniqueCoverName);
        Files.copy(coverFile.toPath(), coverDest, StandardCopyOption.REPLACE_EXISTING);
        return coverDest.toAbsolutePath().toString();
    }

    private static void preloadForLibrarianEdit() {
        if (!isEditingLibrarianBook()) {
            return;
        }
        try {
            var opt = BookDao.findById(editingBookId);
            if (opt.isEmpty()) {
                showError("Not Found", "The selected book no longer exists.");
                navigator.showLibrarianCatalog(currentUser);
                return;
            }
            Book b = opt.get();
            editingBookSnapshot = b;
            titleField.setText(b.getTitle() == null ? "" : b.getTitle());
            if (authorNameField != null) {
                authorNameField.setText(b.getAuthorFullNameSnapshot() == null ? "" : b.getAuthorFullNameSnapshot());
            }
            descriptionArea.setText(b.getSummary() == null ? "" : b.getSummary());
            genreListView.getSelectionModel().clearSelection();
            if (b.getGenre() != null && !b.getGenre().isBlank()) {
                for (String part : b.getGenre().split(",")) {
                    String g = part.trim();
                    int idx = AVAILABLE_GENRES.indexOf(g);
                    if (idx >= 0) {
                        genreListView.getSelectionModel().select(idx);
                    }
                }
            }
            updateSelectedGenresDisplay();
            if (b.getFilePath() != null && !b.getFilePath().isBlank()) {
                File existing = new File(b.getFilePath());
                if (existing.exists() && existing.canRead()) {
                    selectedBookFile = existing;
                    fileNameLabel.setText(existing.getName());
                    fileNameLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    fileDisplayLabel.setText(existing.getName() + " (" + formatFileSize(existing.length()) + ")");
                    fileDisplayLabel.setStyle("-fx-text-fill: #27ae60;");
                }
            }
            if (b.getCoverImagePath() != null && !b.getCoverImagePath().isBlank()) {
                File existingCover = new File(b.getCoverImagePath());
                if (existingCover.exists() && existingCover.canRead()) {
                    selectedCoverFile = existingCover;
                    if (coverNameLabel != null) {
                        coverNameLabel.setText(existingCover.getName());
                        coverNameLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    }
                    if (coverPathDisplay != null) {
                        coverPathDisplay.setText(existingCover.getName() + " (" + formatFileSize(existingCover.length()) + ")");
                        coverPathDisplay.setStyle("-fx-text-fill: #27ae60;");
                    }
                }
            }
        } catch (SQLException ex) {
            showError("Load Failed", ex.getMessage());
            navigator.showLibrarianCatalog(currentUser);
        }
    }

    private static String screenTitleText() {
        if (isEditingLibrarianBook()) {
            return "Edit Published Book";
        }
        if (pendingBookRequestApproveId != null && pendingBookRequestApproveId > 0) {
            return "Approve & publish request";
        }
        return "Publish New Book";
    }

    private static String primarySubmitButtonText() {
        if (isEditingLibrarianBook()) {
            return "Save Changes";
        }
        if (pendingBookRequestApproveId != null && pendingBookRequestApproveId > 0) {
            return "Publish to catalog";
        }
        return librarianPublishMode ? "Publish Book" : "Submit for Approval";
    }

    private static void preloadFromBookRequest(long requestId) {
        try {
            Optional<BookRequest> opt = BookRequestDao.findById(requestId);
            if (opt.isEmpty()) {
                showError("Request not found", "This book request is no longer available.");
                navigator.showLibrarianManageBookRequests(currentUser);
                return;
            }
            BookRequest req = opt.get();
            String path = req.getDownloadedFilePath();
            if (path != null && !path.isBlank()) {
                File f = new File(path);
                if (f.isFile() && f.canRead()) {
                    selectedBookFile = f;
                    fileNameLabel.setText(f.getName());
                    fileNameLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    fileDisplayLabel.setText(f.getName() + " (" + formatFileSize(f.length()) + ")");
                    fileDisplayLabel.setStyle("-fx-text-fill: #27ae60;");
                }
            }

            titleField.setText(req.getTitle() == null ? "" : req.getTitle());
            if (authorNameField != null) {
                authorNameField.setText(req.getAuthorName() == null ? "" : req.getAuthorName());
            }
            String summary = req.getGeneratedSummary() != null && !req.getGeneratedSummary().isEmpty()
                    ? req.getGeneratedSummary()
                    : (req.getDescription() != null ? req.getDescription() : "");
            descriptionArea.setText(summary);
            if (!summary.isBlank()) {
                summaryStatusLabel.setText("Summary status: Ready (from request)");
                summaryStatusLabel.setStyle("-fx-text-fill: #27ae60;");
            }

            genreListView.getSelectionModel().clearSelection();
            boolean anyGenre = false;
            if (req.getGenre() != null && !req.getGenre().isBlank()) {
                for (String part : req.getGenre().split(",")) {
                    String g = part.trim();
                    int idx = AVAILABLE_GENRES.indexOf(g);
                    if (idx >= 0) {
                        genreListView.getSelectionModel().select(idx);
                        anyGenre = true;
                    }
                }
            }
            if (!anyGenre) {
                int ix = AVAILABLE_GENRES.indexOf("Classic");
                genreListView.getSelectionModel().select(ix >= 0 ? ix : 0);
            }
            updateSelectedGenresDisplay();
        } catch (SQLException ex) {
            showError("Load failed", ex.getMessage());
            navigator.showLibrarianManageBookRequests(currentUser);
        }
    }

    private static PublishService.PublishResult publishFromBookRequest(
            User librarian,
            long requestId,
            String approvalNotes,
            String title,
            String authorName,
            String genres,
            String description,
            File bookFile,
            File coverFile) {
        PublishService.PublishResult published = submitBookAsLibrarian(
                librarian, title, authorName, genres, description, bookFile, coverFile);
        if (!published.success()) {
            return published;
        }
        int approvedGroupCount = 0;
        try {
            approvedGroupCount = BookRequestDao.approveGroupedActiveByRequestId(
                    requestId,
                    approvalNotes == null ? "" : approvalNotes
            );
        } catch (SQLException ex) {
            return new PublishService.PublishResult(false,
                    "Book was published, but updating the request failed: " + ex.getMessage());
        }
        try {
            Optional<BookRequest> refreshed = BookRequestDao.findById(requestId);
            if (refreshed.isPresent()) {
                BookRequest req = refreshed.get();
                NotificationService.notifyBookRequestUpdate(
                        req.getRequestedByUserId(),
                        "Book Request Approved & Published",
                        "Your request for \"" + req.getTitle()
                                + "\" has been approved! The book is now available in the catalog for borrowing.");
            }
        } catch (SQLException ignored) {
        }
        String suffix = approvedGroupCount > 1
                ? " (" + approvedGroupCount + " grouped requests were approved together)."
                : ".";
        return new PublishService.PublishResult(true,
                "Book published and the request was approved" + suffix);
    }

    private static boolean isEditingLibrarianBook() {
        return librarianPublishMode && editingBookId != null && editingBookId > 0;
    }

    private static String sanitizeFilename(String filename) {
        String safeName = new File(filename).getName();
        return safeName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Adjusts generated summary length for display based on summary style (Short / Medium / Detailed).
     */
    public static String adjustSummaryForDisplay(String generated, String style) {
        if (generated == null) {
            return "";
        }
        if (style == null) {
            return generated;
        }
        if (style.equalsIgnoreCase("short")) {
            int idx = generated.indexOf(". ");
            if (idx >= 0) {
                return generated.substring(0, idx + 1);
            }
            return generated.trim();
        }
        if (style.equalsIgnoreCase("medium")) {
            int half = generated.length() / 2;
            return generated.substring(0, half);
        }
        if (style.equalsIgnoreCase("detailed")) {
            return generated;
        }
        return generated;
    }
}