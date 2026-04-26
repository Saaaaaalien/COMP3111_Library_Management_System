package org.example.app;

import org.example.db.BookDao;
import org.example.db.BorrowDao;
import org.example.domain.Book;
import org.example.domain.Borrow;
import org.example.domain.User;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

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
        setAppIcon();
        this.stage.sceneProperty().addListener((obs, oldScene, newScene) -> attachDevCrashShortcut(newScene));
    }

    /**
     * Switches main scenes while preserving the current window state.
     * If the user is in fullscreen/maximized/manual resize mode, keep it across navigation.
     */
    private void showScenePreservingWindowState(Scene scene) {
        WindowStateKeeper.Snapshot snapshot = WindowStateKeeper.capture(stage);
        stage.setScene(scene);
        WindowStateKeeper.applyAfterSceneSwap(stage, snapshot);
    }

    private void setAppIcon() {
        try {
            String iconPath = "/icons/app-icon.png";
            Image icon = new Image(getClass().getResourceAsStream(iconPath));
            stage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("Failed to load app icon: " + e.getMessage());
        }
    }

    private void attachDevCrashShortcut(Scene scene) {
        if (scene == null || !AppConfig.DEV_MODE) {
            return;
        }
        var crashCombo = new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN);
        scene.getAccelerators().put(crashCombo, () -> SessionService.simulateCrash());
        Object wrapped = scene.getProperties().get("devCrashOverlayInstalled");
        if (Boolean.TRUE.equals(wrapped)) {
            return;
        }
        Button crashBtn = new Button("Crash Test");
        crashBtn.getStyleClass().add("secondary-button");
        crashBtn.setOnAction(e -> SessionService.simulateCrash());
        crashBtn.setFocusTraversable(false);
        StackPane wrapper = new StackPane(scene.getRoot(), crashBtn);
        StackPane.setAlignment(crashBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(crashBtn, new Insets(10));
        scene.setRoot(wrapper);
        scene.getProperties().put("devCrashOverlayInstalled", true);
    }

    public void showWelcome() {
        SessionService.clear();
        Scene scene = org.example.ui.WelcomeScreen.create(this);
        showScenePreservingWindowState(scene);
    }

    /** Opens each role's main hub after a partial session restore. */
    public void showHomeForUser(User user) {
        switch (user.getRole()) {
            case STUDENT, STAFF -> showAvailableBooks(user);
            case AUTHOR -> showAuthorDashboard(user);
            case LIBRARIAN -> showLibrarianApproval(user);
        }
    }

    /** Persists PDF reader context so a crash/kill can reopen the reader with the same borrow. */
    public void recordPdfReaderSession(User user, long borrowId, long bookId, String returnRoute) {
        SessionService.save("PDF_READER", user.getId(), borrowId, bookId, returnRoute);
    }

    /** Restores session to the main screen under the PDF reader after it closes. */
    public void clearPdfReaderSession(User user, String returnRoute) {
        SessionService.save(returnRoute, user.getId());
    }

    public void showStudentStaffPortal() {
        SessionService.save("STUDENT_PORTAL", 0);
        Scene scene = org.example.ui.StudentStaffEntryScreen.create(this);
        showScenePreservingWindowState(scene);
    }

    public void showStudentStaffLogin() {
        SessionService.save("STUDENT_LOGIN", 0);
        Scene scene = org.example.ui.StudentStaffLoginScreen.create(this);
        showScenePreservingWindowState(scene);
    }

    public void showStudentStaffRegister() {
        SessionService.save("STUDENT_REGISTER", 0);
        Scene scene = org.example.ui.StudentStaffRegisterScreen.create(this);
        showScenePreservingWindowState(scene);
    }

    public void showAvailableBooks(User studentOrStaff) {
        SessionService.save("AVAILABLE_BOOKS", studentOrStaff.getId());
        Scene scene = org.example.ui.AvailableBooksScreen.create(this, studentOrStaff);
        showScenePreservingWindowState(scene);
    }

    public void showMyBorrowedBooks(User studentOrStaff) {
        SessionService.save("MY_BORROWS", studentOrStaff.getId());
        Scene scene = org.example.ui.MyBorrowedBooksScreen.create(this, studentOrStaff);
        showScenePreservingWindowState(scene);
    }

    public void showPdfReader(User user,
                                Long borrowId,
                                Long bookId,
                                Integer pageIndex0Override,
                                Integer zoomPercentOverride) {
        // Crash recovery route: if state or DB rows are missing, fall back to home.
        try {
            if (borrowId == null || bookId == null) {
                showWelcome();
                return;
            }
            long borrowId0 = borrowId;
            long bookId0 = bookId;

            Borrow borrow = BorrowDao.findById(borrowId0).orElse(null);
            if (borrow == null || borrow.getBorrowerUserId() != user.getId() || borrow.getBookId() != bookId0) {
                showWelcome();
                return;
            }
            Book book = BookDao.findById(bookId0).orElse(null);
            if (book == null) {
                showWelcome();
                return;
            }

            // Ensure the underlying screen is the user's "My Borrowed Books" so
            // the PDF reader appears on top of it (both for normal and crash recovery restores).
            // This does not overwrite session.json because we are not calling SessionService.save here.
            Scene borrowedScene = org.example.ui.MyBorrowedBooksScreen.create(this, user);
            showScenePreservingWindowState(borrowedScene);

            org.example.ui.PdfReaderScreen.open(
                    this,
                    user,
                    borrowId0,
                    bookId0,
                    book.getTitle(),
                    book.getFilePath(),
                    pageIndex0Override,
                    zoomPercentOverride
            );
        } catch (Exception ignored) {
            showWelcome();
        }
    }

    public void showStudentStaffProfile(User user) {
        SessionService.save("PROFILE_STUDENT", user.getId());
        Scene scene = org.example.ui.StudentStaffProfileScreen.create(this, user);
        showScenePreservingWindowState(scene);
    }

    public void showStudentStaffNotifications(User user) {
        showStudentStaffNotifications(user, false);
    }

    public void showStudentStaffNotifications(User user, boolean returnToBorrowedBooks) {
        SessionService.save("NOTIFICATIONS_STUDENT", user.getId());
        Scene scene = org.example.ui.StudentStaffNotificationBoardScreen.create(this, user, returnToBorrowedBooks);
        showScenePreservingWindowState(scene);
    }

    public void showAuthorPortal() {
        SessionService.save("AUTHOR_PORTAL", 0);
        Scene scene = org.example.ui.AuthorEntryScreen.create(this);
        showScenePreservingWindowState(scene);
    }

    public void showAuthorLogin() {
        SessionService.save("AUTHOR_LOGIN", 0);
        Scene scene = org.example.ui.AuthorLoginScreen.create(this);
        showScenePreservingWindowState(scene);
    }

    public void showAuthorRegister() {
        SessionService.save("AUTHOR_REGISTER", 0);
        Scene scene = org.example.ui.AuthorRegisterScreen.create(this);
        showScenePreservingWindowState(scene);
    }

    public void showAuthorDashboard(User user) {
        SessionService.save("AUTHOR_DASH", user.getId());
        Scene scene = org.example.ui.AuthorDashboardScreen.create(this, user);
        showScenePreservingWindowState(scene);
    }

    public void showPublishBook(User user) {
        SessionService.save("AUTHOR_PUBLISH", user.getId());
        Scene scene = org.example.ui.PublishBookScreen.create(this, user);
        showScenePreservingWindowState(scene);
    }

    public void showAuthorPublishedBooks(User user) {
        SessionService.save("AUTHOR_PUBLISHED", user.getId());
        Scene scene = org.example.ui.AuthorPublishedBooksScreen.create(this, user);
        showScenePreservingWindowState(scene);
    }

    public void showAuthorStats(User user) {
        SessionService.save("AUTHOR_STATS", user.getId());
        Scene scene = org.example.ui.AuthorStatsScreen.create(this, user);
        showScenePreservingWindowState(scene);
    }

    public void showAuthorProfile(User user) {
        SessionService.save("AUTHOR_PROFILE", user.getId());
        Scene scene = org.example.ui.AuthorProfileScreen.create(this, user);
        showScenePreservingWindowState(scene);
    }

    public void showAuthorNotifications(User user) {
        SessionService.save("AUTHOR_NOTIFICATIONS", user.getId());
        Scene scene = org.example.ui.AuthorNotificationsScreen.create(this, user);
        showScenePreservingWindowState(scene);
    }

    public void showAuthorReviews(User user) {
        SessionService.save("AUTHOR_REVIEWS", user.getId());
        Scene scene = org.example.ui.AuthorReviewsScreen.create(this, user);
        showScenePreservingWindowState(scene);
    }

    public void showLibrarianPortal() {
        SessionService.save("LIBRARIAN_PORTAL", 0);
        Scene scene = org.example.ui.LibrarianEntryScreen.create(this);
        showScenePreservingWindowState(scene);
    }

    public void showLibrarianLogin() {
        SessionService.save("LIBRARIAN_LOGIN", 0);
        Scene scene = org.example.ui.LibrarianLoginScreen.create(this);
        showScenePreservingWindowState(scene);
    }

    public void showLibrarianRegister() {
        SessionService.save("LIBRARIAN_REGISTER", 0);
        Scene scene = org.example.ui.LibrarianRegisterScreen.create(this);
        showScenePreservingWindowState(scene);
    }

    public void showLibrarianApproval(User librarian) {
        SessionService.save("LIBRARIAN_APPROVAL", librarian.getId());
        Scene scene = org.example.ui.LibrarianApprovalScreen.create(this, librarian);
        scene.setUserData(librarian);
        showScenePreservingWindowState(scene);
    }

    public void showLibrarianManageUsers(User librarian) {
        SessionService.save("LIBRARIAN_MANAGE_USERS", librarian.getId());
        Scene scene = org.example.ui.LibrarianManageUsersScreen.create(this, librarian);
        showScenePreservingWindowState(scene);
    }

    public void showLibrarianProfile(User librarian) {
        SessionService.save("LIBRARIAN_PROFILE", librarian.getId());
        Scene scene = org.example.ui.LibrarianProfileScreen.create(this, librarian);
        showScenePreservingWindowState(scene);
    }

    public void showLibrarianBorrowRecords(User librarian) {
        SessionService.save("LIBRARIAN_BORROW_RECORDS", librarian.getId());
        Scene scene = org.example.ui.LibrarianBorrowRecordsScreen.create(this, librarian);
        showScenePreservingWindowState(scene);
    }

    public void showLibrarianNotifications(User librarian) {
        SessionService.save("LIBRARIAN_NOTIFICATIONS", librarian.getId());
        Scene scene = org.example.ui.LibrarianNotificationBoardScreen.create(this, librarian);
        showScenePreservingWindowState(scene);
    }

    public void showLibrarianCatalog(User librarian) {
        SessionService.save("LIBRARIAN_CATALOG", librarian.getId());
        Scene scene = org.example.ui.LibrarianCatalogScreen.create(this, librarian);
        showScenePreservingWindowState(scene);
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