package org.example.app;

import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.domain.User;

/**
 * Central navigation for the app. Holds the main Stage and switches scenes
 * for Welcome, Student/Staff, Author, and Librarian portals.
 */
public class Navigator {

    private final Stage stage;
    private static final double WIDTH = 900;
    private static final double HEIGHT = 600;

    public Navigator(Stage stage) {
        this.stage = stage;
        this.stage.setTitle("E-Book Library System");
    }

    public void showWelcome() {
        Scene scene = org.example.ui.WelcomeScreen.create(this);
        stage.setScene(scene);
        stage.show();
    }

    public void showStudentStaffPortal() {
        Scene scene = org.example.ui.StudentStaffEntryScreen.create(this);
        stage.setScene(scene);
    }

    public void showStudentStaffLogin() {
        Scene scene = org.example.ui.StudentStaffLoginScreen.create(this);
        stage.setScene(scene);
    }

    public void showStudentStaffRegister() {
        Scene scene = org.example.ui.StudentStaffRegisterScreen.create(this);
        stage.setScene(scene);
    }

    public void showAvailableBooks(User studentOrStaff) {
        Scene scene = org.example.ui.AvailableBooksScreen.create(this, studentOrStaff);
        stage.setScene(scene);
    }

    //Tasks2
    public void showAuthorPortal() {
        Scene scene = org.example.ui.AuthorEntryScreen.create(this);
        stage.setScene(scene);
    }

    public void showAuthorLogin() {
        Scene scene = org.example.ui.AuthorLoginScreen.create(this);
        stage.setScene(scene);
    }

    public void showAuthorRegister() {
        Scene scene = org.example.ui.AuthorRegisterScreen.create(this);
        stage.setScene(scene);
    }

    public void showAuthorDashboard(User user) {
        Scene scene = org.example.ui.AuthorDashboardScreen.create(this, user);
        stage.setScene(scene);
    }

    public void showPublishBook(User user) {
        Scene scene = org.example.ui.PublishBookScreen.create(this, user);
        stage.setScene(scene);
    }

    public void showLibrarianPortal() {
        Scene scene = org.example.ui.LibrarianEntryScreen.create(this);
        stage.setScene(scene);
    }

    public Stage getStage() {
        return stage;
    }

    public static double getPreferredWidth() {
        return WIDTH;
    }

    public static double getPreferredHeight() {
        return HEIGHT;
    }
}
