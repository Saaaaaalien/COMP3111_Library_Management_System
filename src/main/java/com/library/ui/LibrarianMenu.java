package com.library.ui;

import com.library.model.User;
import javafx.stage.Stage;

/**
 * Minimal stub controller for the legacy Librarian dashboard referenced by LoginMenu.
 * The actual librarian functionality is implemented in the newer org.example.* flows.
 */
public class LibrarianMenu {

    private User currentUser;
    private Stage primaryStage;

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }
}

