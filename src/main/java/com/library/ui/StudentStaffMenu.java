package com.library.ui;

import javafx.stage.Stage;

/**
 * Minimal controller stub for the Student/Staff dashboard used by LoginMenu.
 * Uses generic types so it can compile in both module variants.
 */
public class StudentStaffMenu {

    private Object currentUser;
    private Stage primaryStage;

    public void setCurrentUser(Object user) {
        this.currentUser = user;
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }
}

