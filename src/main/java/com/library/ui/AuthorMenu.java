package com.library.ui;

import com.library.model.Author;
import com.library.model.Book;
import com.library.model.User;
import com.library.service.UserService;
import com.library.utils.FileUtils;
import com.library.service.BookService;
import org.example.app.WindowStateKeeper;

import javafx.collections.ObservableList;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

public class AuthorMenu{

    @FXML private Label welcomeLabel;

    // Task 2.3 Publish book tags
    @FXML private TextField titleField;
    @FXML private TextField genreField;
    @FXML private TextArea descriptionArea;
    @FXML private TextField filePathField;

    private User currentUser;
    private Stage primaryStage;
    private ObservableList<Book> myBooksList;
    private File selectedFile;

    private UserService authorService;
    private FileUtils fileStorage;
    private BookService bookPublish;
    private Author currentAuthor;


public void setCurrentUser(User user) {
    this.currentUser = user;
    welcomeLabel.setText("Welcome, " + user.getFullName() + "!");
}

public void setPrimaryStage(Stage primaryStage) {
    this.primaryStage = primaryStage;
}

private void setScenePreservingWindowState(Scene scene, String title) {
    if (primaryStage == null) {
        return;
    }
    WindowStateKeeper.Snapshot snapshot = WindowStateKeeper.capture(primaryStage);
    primaryStage.setScene(scene);
    primaryStage.setTitle(title);
    WindowStateKeeper.applyAfterSceneSwap(primaryStage, snapshot);
}

@FXML
private void handleBrowse() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Select Book File");

    // filters for supported file types
    FileChooser.ExtensionFilter pdfFilter = new FileChooser.ExtensionFilter("PDF Files", "*.pdf");
    FileChooser.ExtensionFilter txtFilter = new FileChooser.ExtensionFilter("Text Files", "*.txt");
    FileChooser.ExtensionFilter docFilter = new FileChooser.ExtensionFilter("Word Documents", "*.docx", "*.doc");

    fileChooser.getExtensionFilters().addAll(pdfFilter, txtFilter, docFilter);

    selectedFile = fileChooser.showOpenDialog(primaryStage);

    if (selectedFile != null) {
        filePathField.setText(selectedFile.getAbsolutePath());
    }
}

@FXML
private void handleSubmit() {
    String title = titleField.getText().trim();
    String genre = genreField.getText().trim();
    String description = descriptionArea.getText().trim();

    if (title.isEmpty() || genre.isEmpty() || description.isEmpty()) {
        showAlert("Error", "Please fill in all fields");
        return;
    }

    if (selectedFile == null) {
        showAlert("Error", "Please select a book file");
        return;
    }

    try {
        // Create unique file name
        String fileName = currentUser.getUsername() + "_" +
                title.replaceAll("\\s+", "_") + "_" +
                System.currentTimeMillis() +
                getFileExtension(selectedFile.getName());

        // Save file using FileUtils
        String savedFilePath = FileUtils.saveUploadedFile(selectedFile, fileName);

        // Create book submission
        Book book = new Book(
                title,
                currentUser.getUsername(),
                currentUser.getFullName(),
                genre,
                description,
                savedFilePath
        );

        boolean success = bookPublish.submitBook(book);

        if (success) {
            showAlert("Success", "Book submitted successfully! Waiting for librarian approval.");

            // Clear fields
            titleField.clear();
            genreField.clear();
            descriptionArea.clear();
            filePathField.clear();
            selectedFile = null;

        } else {
            showAlert("Error", "Failed to submit book");
        }

    } catch (IOException e) {
        e.printStackTrace();
        showAlert("Error", "Failed to save book file");
    }
}

private String getFileExtension(String fileName) {
    int lastDot = fileName.lastIndexOf('.');
    return lastDot > 0 ? fileName.substring(lastDot) : "";
}

@FXML
private void handleLogout() {
    try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
        Parent root = loader.load();

        LoginMenu loginMenu = loader.getController();
        loginMenu.setPrimaryStage(primaryStage);

        Scene scene = new Scene(root);
        setScenePreservingWindowState(scene, "Library Management System - Login");

    } catch (IOException e) {
        e.printStackTrace();
    }
}

private void showAlert(String title, String message) {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
}
}
