package org.example.app;

import org.example.db.BookDao;
import org.example.db.BorrowDao;
import org.example.db.NotificationDao;
import org.example.domain.Book;
import org.example.domain.Borrow;
import org.example.domain.Role;
import org.example.domain.User;

import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.sql.SQLException;

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
        // Wrap scene root with a lightweight app header overlay (menu + notifications)
        Object installed = scene.getProperties().get("appHeaderInstalled");
        if (!Boolean.TRUE.equals(installed)) {
            var originalRoot = scene.getRoot();
            BorderPane wrapper = new BorderPane();
            wrapper.setCenter(originalRoot);

            // Top bar with menu button (left) and notification button (right)
            HBox topBar = new HBox();
            topBar.setPadding(new Insets(8));
            topBar.setAlignment(Pos.CENTER_LEFT);
            Button menuBtn = new Button("☰");
            menuBtn.getStyleClass().add("menu-button");
            menuBtn.setFocusTraversable(false);

            // Drawer pane (left) - will contain navigation links
            VBox drawer = new VBox(8);
            drawer.setPadding(new Insets(12));
            drawer.setStyle("-fx-background-color: white; -fx-border-color: #ddd;");
            drawer.setVisible(false);

            // Responsive width: 20% of window, with sensible min/max
            drawer.prefWidthProperty().bind(stage.widthProperty().multiply(0.20));
            drawer.minWidthProperty().bind(stage.widthProperty().multiply(0.12));
            drawer.maxWidthProperty().bind(stage.widthProperty().multiply(0.15));
            drawer.setMaxHeight(Double.MAX_VALUE);

            // Ensure the drawer is aligned to the left of the StackPane and does not expand to full width
            StackPane.setAlignment(drawer, Pos.TOP_LEFT);

            // Initialize translateX off-screen (use stage width until drawer computes its layout)
            drawer.setTranslateX(-Math.max(200, stage.getWidth() * 0.20));

            // Keep translateX in sync when drawer width changes (stay hidden when closed)
            drawer.widthProperty().addListener((obs, oldW, newW) -> {
                if (drawer.getTranslateX() < 0) {
                    drawer.setTranslateX(-newW.doubleValue());
                }
            });

            // Toggle drawer with slide animation (compute width dynamically)
            menuBtn.setOnAction(e -> {
                boolean opening = drawer.getTranslateX() < 0;
                TranslateTransition tt = new TranslateTransition(Duration.millis(220), drawer);
                double w = drawer.getWidth() > 0 ? drawer.getWidth() : Math.max(200, stage.getWidth() * 0.20);
                if (opening) {
                    drawer.setVisible(true);
                    tt.setFromX(-w);
                    tt.setToX(0);
                } else {
                    tt.setFromX(0);
                    tt.setToX(-w);
                    tt.setOnFinished(ev -> drawer.setVisible(false));
                }
                tt.play();
            });

            // Notification button (constant)
            Button notifBtn = new Button("Notifications");
            notifBtn.getStyleClass().add("secondary-button");
            notifBtn.setFocusTraversable(false);
            HBox rightBox = new HBox(notifBtn);
            rightBox.setAlignment(Pos.CENTER_RIGHT);
            HBox.setHgrow(rightBox, javafx.scene.layout.Priority.ALWAYS);

            topBar.getChildren().addAll(menuBtn, rightBox);

            // Populate drawer links based on user stored in scene.userData, if present
            drawer.getChildren().clear();
            Object ud = scene.getUserData();
            if (ud instanceof User u) {
                Role r = u.getRole();
                int unread = 0;
                try {
                    unread = NotificationDao.countUnread(u.getId());
                } catch (SQLException ignored) {
                }
                notifBtn.setText(formatNotificationsLabel(unread));
                // Common links for librarians
                if (r == Role.LIBRARIAN) {
                    Hyperlink b1 = new Hyperlink("Dashboard");
                    b1.getStyleClass().add("drawer-link");
                    b1.setOnAction(ev -> showLibrarianApproval(u));
                    Hyperlink b2 = new Hyperlink("Manage Published Books");
                    b2.getStyleClass().add("drawer-link");
                    b2.setOnAction(ev -> showLibrarianCatalog(u));
                    Hyperlink b3 = new Hyperlink("Borrow Records");
                    b3.getStyleClass().add("drawer-link");
                    b3.setOnAction(ev -> showLibrarianBorrowRecords(u));
                    Hyperlink b4 = new Hyperlink("My Profile");
                    b4.getStyleClass().add("drawer-link");
                    b4.setOnAction(ev -> showLibrarianProfile(u));
                    Hyperlink b5 = new Hyperlink("Notifications");
                    b5.getStyleClass().add("drawer-link");
                    b5.setOnAction(ev -> showLibrarianNotifications(u));
                    b5.setText(formatNotificationsLabel(unread));
                    Hyperlink b6 = new Hyperlink("Manage Users");
                    b6.getStyleClass().add("drawer-link");
                    b6.setOnAction(ev -> showLibrarianManageUsers(u));
                    Hyperlink b7 = new Hyperlink("Manage Book Requests");
                    b7.getStyleClass().add("drawer-link");
                    b7.setOnAction(ev -> showLibrarianManageBookRequests(u));
                    drawer.getChildren().addAll(b1, b6, b2, b3, b7, b4, b5);
                } else if (r == Role.AUTHOR) {
                    Hyperlink b1 = new Hyperlink("Dashboard");
                    b1.getStyleClass().add("drawer-link");
                    b1.setOnAction(ev -> showAuthorDashboard(u));
                    Hyperlink b2 = new Hyperlink("My Books");
                    b2.getStyleClass().add("drawer-link");
                    b2.setOnAction(ev -> showAuthorPublishedBooks(u));
                    Hyperlink b3 = new Hyperlink("Publish");
                    b3.getStyleClass().add("drawer-link");
                    b3.setOnAction(ev -> showPublishBook(u));
                    Hyperlink b4 = new Hyperlink("View Stats");
                    b4.getStyleClass().add("drawer-link");
                    b4.setOnAction(ev -> showAuthorStats(u));
                    Hyperlink b5 = new Hyperlink("Review Handling");
                    b5.getStyleClass().add("drawer-link");
                    b5.setOnAction(ev -> showAuthorReviews(u));
                    Hyperlink b6 = new Hyperlink("Profile");
                    b6.getStyleClass().add("drawer-link");
                    b6.setOnAction(ev -> showAuthorProfile(u));
                    Hyperlink b7 = new Hyperlink("Notifications");
                    b7.getStyleClass().add("drawer-link");
                    b7.setOnAction(ev -> showAuthorNotifications(u));
                    b7.setText(formatNotificationsLabel(unread));
                    drawer.getChildren().addAll(b1, b2, b3, b4, b5, b6, b7);
                } else if (r == Role.STUDENT || r == Role.STAFF) {
                    Hyperlink b1 = new Hyperlink("Available Books");
                    b1.getStyleClass().add("drawer-link");
                    b1.setOnAction(ev -> showAvailableBooks(u));
                    Hyperlink b2 = new Hyperlink("My Borrows");
                    b2.getStyleClass().add("drawer-link");
                    b2.setOnAction(ev -> showMyBorrowedBooks(u));
                    Hyperlink b3 = new Hyperlink("Profile");
                    b3.getStyleClass().add("drawer-link");
                    b3.setOnAction(ev -> showStudentStaffProfile(u));
                    Hyperlink b4 = new Hyperlink("Notifications");
                    b4.getStyleClass().add("drawer-link");
                    b4.setOnAction(ev -> showStudentStaffNotifications(u));
                    b4.setText(formatNotificationsLabel(unread));
                    drawer.getChildren().addAll(b1, b2, b3, b4);
                }

                // Notification button action
                notifBtn.setOnAction(ev -> {
                    switch (u.getRole()) {
                        case LIBRARIAN -> showLibrarianNotifications(u);
                        case AUTHOR -> showAuthorNotifications(u);
                        case STUDENT, STAFF -> showStudentStaffNotifications(u);
                        default -> {}
                    }
                });
            } else {
                // If no user data, disable notification button and show generic links
                notifBtn.setDisable(true);
                Label info = new Label("No account context available");
                drawer.getChildren().add(info);
            }

            // Put content and drawer into a StackPane so the drawer slides over the content
            StackPane stack = new StackPane(originalRoot, drawer);
            // Ensure the drawer sits above the content but only occupies its bound width
            StackPane.setAlignment(originalRoot, Pos.CENTER);
            BorderPane overlayContainer = new BorderPane();
            overlayContainer.setTop(topBar);
            overlayContainer.setCenter(stack);

            // If we captured a window size snapshot and are not preserving fullscreen/maximized,
            // prefer that size for the new root so stage doesn't visibly resize when swapping scenes.
            if (snapshot != null && !snapshot.wasFullScreen() && !snapshot.wasMaximized()
                    && snapshot.width() > 0 && snapshot.height() > 0) {
                overlayContainer.setPrefSize(snapshot.width(), snapshot.height());
            }

            // Add a logout hyperlink immediately after the links
            Hyperlink logoutLink = new Hyperlink("Logout");
            logoutLink.getStyleClass().add("drawer-link-logout");
            logoutLink.setOnAction(ev -> {
                SessionService.clear();
                showWelcome();
            });
            drawer.getChildren().add(logoutLink);
            // Spacer to push any following content to the bottom (keeps logout just under links)
            Region spacer = new Region();
            VBox.setVgrow(spacer, Priority.ALWAYS);
            drawer.getChildren().add(spacer);

            scene.setRoot(overlayContainer);
            scene.getProperties().put("appHeaderInstalled", true);
        }

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
        scene.setUserData(studentOrStaff);
        showScenePreservingWindowState(scene);
    }

    public void showMyBorrowedBooks(User studentOrStaff) {
        SessionService.save("MY_BORROWS", studentOrStaff.getId());
        Scene scene = org.example.ui.MyBorrowedBooksScreen.create(this, studentOrStaff);
        scene.setUserData(studentOrStaff);
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
            borrowedScene.setUserData(user);
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
        scene.setUserData(user);
        showScenePreservingWindowState(scene);
    }

    public void showStudentStaffNotifications(User user) {
        showStudentStaffNotifications(user, false);
    }

    public void showStudentStaffNotifications(User user, boolean returnToBorrowedBooks) {
        SessionService.save("NOTIFICATIONS_STUDENT", user.getId());
        Scene scene = org.example.ui.StudentStaffNotificationBoardScreen.create(this, user, returnToBorrowedBooks);
        scene.setUserData(user);
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
        scene.setUserData(user);
        showScenePreservingWindowState(scene);
    }

    public void showPublishBook(User user) {
        SessionService.save("AUTHOR_PUBLISH", user.getId());
        Scene scene = org.example.ui.PublishBookScreen.create(this, user);
        scene.setUserData(user);
        showScenePreservingWindowState(scene);
    }

    public void showAuthorPublishedBooks(User user) {
        SessionService.save("AUTHOR_PUBLISHED", user.getId());
        Scene scene = org.example.ui.AuthorPublishedBooksScreen.create(this, user);
        scene.setUserData(user);
        showScenePreservingWindowState(scene);
    }

    public void showAuthorStats(User user) {
        SessionService.save("AUTHOR_STATS", user.getId());
        Scene scene = org.example.ui.AuthorStatsScreen.create(this, user);
        scene.setUserData(user);
        showScenePreservingWindowState(scene);
    }

    public void showAuthorProfile(User user) {
        SessionService.save("AUTHOR_PROFILE", user.getId());
        Scene scene = org.example.ui.AuthorProfileScreen.create(this, user);
        scene.setUserData(user);
        showScenePreservingWindowState(scene);
    }

    public void showAuthorNotifications(User user) {
        SessionService.save("AUTHOR_NOTIFICATIONS", user.getId());
        Scene scene = org.example.ui.AuthorNotificationsScreen.create(this, user);
        scene.setUserData(user);
        showScenePreservingWindowState(scene);
    }

    public void showAuthorReviews(User user) {
        SessionService.save("AUTHOR_REVIEWS", user.getId());
        Scene scene = org.example.ui.AuthorReviewsScreen.create(this, user);
        scene.setUserData(user);
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
        scene.setUserData(librarian);
        showScenePreservingWindowState(scene);
    }

    public void showLibrarianProfile(User librarian) {
        SessionService.save("LIBRARIAN_PROFILE", librarian.getId());
        Scene scene = org.example.ui.LibrarianProfileScreen.create(this, librarian);
        scene.setUserData(librarian);
        showScenePreservingWindowState(scene);
    }

    public void showLibrarianBorrowRecords(User librarian) {
        SessionService.save("LIBRARIAN_BORROW_RECORDS", librarian.getId());
        Scene scene = org.example.ui.LibrarianBorrowRecordsScreen.create(this, librarian);
        scene.setUserData(librarian);
        showScenePreservingWindowState(scene);
    }

    public void showLibrarianNotifications(User librarian) {
        SessionService.save("LIBRARIAN_NOTIFICATIONS", librarian.getId());
        Scene scene = org.example.ui.LibrarianNotificationBoardScreen.create(this, librarian);
        scene.setUserData(librarian);
        showScenePreservingWindowState(scene);
    }

    public void showLibrarianCatalog(User librarian) {
        SessionService.save("LIBRARIAN_CATALOG", librarian.getId());
        Scene scene = org.example.ui.LibrarianCatalogScreen.create(this, librarian);
        scene.setUserData(librarian);
        showScenePreservingWindowState(scene);
    }

    public void showLibrarianManageBookRequests(User librarian) {
        SessionService.save("LIBRARIAN_BOOK_REQUESTS", librarian.getId());
        Scene scene = org.example.ui.LibrarianManageBookRequestsScreen.create(this, librarian);
        scene.setUserData(librarian);
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

    private static String formatNotificationsLabel(int unread) {
        return unread > 0 ? "Notifications (" + unread + ")" : "Notifications";
    }
}