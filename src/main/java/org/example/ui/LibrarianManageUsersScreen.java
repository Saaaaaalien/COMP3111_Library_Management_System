package org.example.ui;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.app.Navigator;
import org.example.db.BorrowDao;
import org.example.db.UserDao;
import org.example.domain.Role;
import org.example.domain.User;
import org.example.security.PasswordHasher;
import org.example.service.NotificationService;
import org.example.util.ValidationException;
import org.example.util.Validators;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Librarian screen: view, search, filter, edit, deactivate, reactivate, and add all users.
 * Includes per-user activity log (last login, active borrow count).
 */
public final class LibrarianManageUsersScreen {

    private LibrarianManageUsersScreen() {}

    // Search/filter state — reset each time the screen is opened
    private static String currentSearchTerm = "";
    private static String currentRoleFilter = "";

    // Tracks which user IDs are selected for bulk actions
    private static final Set<Long> selectedBulkIds = new LinkedHashSet<>();

    public static Scene create(Navigator navigator, User librarian) {
        currentSearchTerm = "";
        currentRoleFilter = "";

        Label title = new Label("Manage Users");
        title.getStyleClass().add("screen-title");

        Label librarianInfoLbl = new Label("Logged in as: " + librarian.getFullName());
        librarianInfoLbl.getStyleClass().add("info-label");

        HBox searchFilterBox = buildSearchFilterBox();

        VBox userListContent = new VBox(12);
        userListContent.setPadding(new Insets(20));

        ScrollPane scrollPane = new ScrollPane(userListContent);
        scrollPane.setFitToWidth(true);

        loadUsers(userListContent, librarian);

        Button applyBtn = new Button("Search / Filter");
        applyBtn.getStyleClass().add("primary-button");
        applyBtn.setOnAction(e -> loadUsers(userListContent, librarian));

        Button resetBtn = new Button("Reset");
        resetBtn.getStyleClass().add("secondary-button");
        resetBtn.setOnAction(e -> {
            currentSearchTerm = "";
            currentRoleFilter = "";
            loadUsers(userListContent, librarian);
        });

        Button addUserBtn = new Button("+ Add New User");
        addUserBtn.getStyleClass().add("primary-button");
        addUserBtn.setOnAction(e -> showAddUserDialog(userListContent, librarian));

        HBox actionBar = new HBox(10, applyBtn, resetBtn, addUserBtn);
        actionBar.setPadding(new Insets(10, 20, 0, 20));
        actionBar.setAlignment(Pos.CENTER_LEFT);

        VBox headerBox = new VBox(8, title, librarianInfoLbl, searchFilterBox);
        headerBox.setPadding(new Insets(20, 20, 0, 20));
        headerBox.setStyle("-fx-border-color: #f0f0f0; -fx-border-width: 0 0 1 0;");

        BorderPane root = new BorderPane();
        root.setTop(headerBox);
        root.setCenter(new VBox(actionBar, scrollPane));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL css = LibrarianManageUsersScreen.class.getResource("/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        return scene;
    }

    // ── Search / filter bar ────────────────────────────────────────────────

    private static HBox buildSearchFilterBox() {
        HBox box = new HBox(15);
        box.setPadding(new Insets(10, 0, 0, 0));
        box.setStyle("-fx-padding: 10;");

        Label searchLbl = new Label("Search (Username / Name):");
        searchLbl.setStyle("-fx-font-size: 11;");

        TextField searchField = new TextField();
        searchField.setPromptText("Type to search...");
        searchField.setPrefWidth(200);
        searchField.setText(currentSearchTerm);
        searchField.setOnKeyReleased(e -> currentSearchTerm = searchField.getText());
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Label roleLbl = new Label("Role:");
        roleLbl.setStyle("-fx-font-size: 11;");

        ComboBox<String> roleCombo = new ComboBox<>();
        roleCombo.getItems().addAll("All Roles", "STUDENT", "STAFF", "AUTHOR", "LIBRARIAN");
        roleCombo.setValue(currentRoleFilter.isEmpty() ? "All Roles" : currentRoleFilter);
        roleCombo.setPrefWidth(130);
        roleCombo.setOnAction(e -> {
            String v = roleCombo.getValue();
            currentRoleFilter = "All Roles".equals(v) ? "" : v;
        });

        box.getChildren().addAll(searchLbl, searchField,
                new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
                roleLbl, roleCombo);
        return box;
    }

    // ── Load & render users ───────────────────────────────────────────────

    private static void loadUsers(VBox container, User librarian) {
        container.getChildren().clear();
        selectedBulkIds.clear();
        try {
            List<User> users = queryUsers();
            if (users.isEmpty()) {
                Label empty = new Label("No users match your search criteria.");
                empty.setStyle("-fx-font-size: 14; -fx-text-fill: #888;");
                container.getChildren().add(empty);
                return;
            }

            Label countLbl = new Label("Results: " + users.size() + " user(s)");
            countLbl.setStyle("-fx-font-size: 12; -fx-text-fill: #555;");
            container.getChildren().add(countLbl);

            List<CheckBox> allCheckBoxes = new ArrayList<>();

            Button selectAllBtn    = new Button("☑ Select All");
            selectAllBtn.getStyleClass().add("secondary-button");

            Button deselectAllBtn  = new Button("☐ Deselect All");
            deselectAllBtn.getStyleClass().add("secondary-button");

            Button bulkDeactivateBtn = new Button("⛔ Bulk Deactivate");
            bulkDeactivateBtn.getStyleClass().add("secondary-button");
            bulkDeactivateBtn.setStyle("-fx-text-fill: #d9534f;");
            bulkDeactivateBtn.setOnAction(e -> handleBulkDeactivate(container, librarian, users));

            Button bulkReactivateBtn = new Button("✅ Bulk Reactivate");
            bulkReactivateBtn.getStyleClass().add("secondary-button");
            bulkReactivateBtn.setStyle("-fx-text-fill: #4caf50;");
            bulkReactivateBtn.setOnAction(e -> handleBulkReactivate(container, librarian, users));

            selectAllBtn.setOnAction(e -> {
                for (CheckBox cb : allCheckBoxes) cb.setSelected(true);
            });
            deselectAllBtn.setOnAction(e -> {
                for (CheckBox cb : allCheckBoxes) cb.setSelected(false);
            });

            HBox bulkBar = new HBox(10,
                    new Label("Bulk Actions:"),
                    selectAllBtn, deselectAllBtn,
                    new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
                    bulkDeactivateBtn, bulkReactivateBtn);
            bulkBar.setAlignment(Pos.CENTER_LEFT);
            bulkBar.setPadding(new Insets(8));
            bulkBar.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #e0e0e0;"
                    + " -fx-border-width: 1; -fx-border-radius: 4;");
            container.getChildren().add(bulkBar);

            for (User u : users) {
                container.getChildren().add(buildUserCard(u, librarian, container, allCheckBoxes));
            }
        } catch (SQLException ex) {
            Label err = new Label("Error loading users: " + ex.getMessage());
            err.setStyle("-fx-text-fill: #d9534f;");
            container.getChildren().add(err);
        }
    }

    private static List<User> queryUsers() throws SQLException {
        boolean hasSearch = !currentSearchTerm.isBlank();
        boolean hasRole = !currentRoleFilter.isEmpty();
        Role roleEnum = hasRole ? Role.valueOf(currentRoleFilter) : null;

        if (hasSearch) {
            return UserDao.search(currentSearchTerm.trim(), roleEnum);
        } else if (hasRole) {
            return UserDao.findAllByRole(roleEnum);
        } else {
            return UserDao.findAll();
        }
    }

    // ── Build a single user card ──────────────────────────────────────────

    private static VBox buildUserCard(User user, User librarian, VBox container,
                                      List<CheckBox> allCheckBoxes) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-border-color: #ddd; -fx-border-width: 1; -fx-border-radius: 5;");

        CheckBox selectBox = new CheckBox("Select for bulk action");
        selectBox.setStyle("-fx-font-size: 11; -fx-text-fill: #555;");
        selectBox.setSelected(selectedBulkIds.contains(user.getId()));
        selectBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) selectedBulkIds.add(user.getId());
            else        selectedBulkIds.remove(user.getId());
        });
        card.getChildren().add(selectBox);
        allCheckBoxes.add(selectBox);

        // Header row: username + status badge
        Label usernameLbl = new Label(user.getUsername());
        usernameLbl.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        Label statusBadge = buildStatusBadge(user);

        HBox headerRow = new HBox(10, usernameLbl, statusBadge);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        // Profile details grid
        GridPane details = new GridPane();
        details.setHgap(12);
        details.setVgap(4);

        addDetail(details, 0, "Full Name:", user.getFullName());
        addDetail(details, 1, "Role:", formatRole(user.getRole()));
        addDetail(details, 2, "Employee ID:",
                user.getEmployeeId() != null && !user.getEmployeeId().isBlank()
                        ? user.getEmployeeId() : "—");
        addDetail(details, 3, "Registered:", user.getCreatedAt() != null
                ? user.getCreatedAt().replace("T", " ").substring(0, Math.min(19, user.getCreatedAt().length()))
                : "—");

        if (user.getLockedUntil() != null && !user.getLockedUntil().isBlank()) {
            addDetail(details, 4, "Locked until:", user.getLockedUntil());
        }

        // Activity log
        String lastLoginStr = user.getLastLogin() != null && !user.getLastLogin().isBlank()
                ? user.getLastLogin().replace("T", " ").substring(0, Math.min(19, user.getLastLogin().length()))
                : "Never";
        int activeBorrows = 0;
        try { activeBorrows = BorrowDao.countActiveByBorrowerUserId(user.getId()); } catch (SQLException ignored) {}

        Label activityHeader = new Label("Activity Log");
        activityHeader.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #444;");

        GridPane activityGrid = new GridPane();
        activityGrid.setHgap(12);
        activityGrid.setVgap(3);
        addDetail(activityGrid, 0, "Last Login:", lastLoginStr);
        addDetail(activityGrid, 1, "Active Borrows:", activeBorrows + " book(s)");

        VBox activityBox = new VBox(4, activityHeader, activityGrid);
        activityBox.setPadding(new Insets(6, 8, 6, 8));
        activityBox.setStyle("-fx-background-color: #f5f9ff; -fx-border-color: #d0e4f7;"
                + " -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;");

        // Bio (authors only)
        VBox bioBox = new VBox();
        if (user.getRole() == Role.AUTHOR && user.getBio() != null && !user.getBio().isBlank()) {
            Label bioLbl = new Label("Bio: " + user.getBio());
            bioLbl.setWrapText(true);
            bioLbl.setStyle("-fx-font-size: 10; -fx-text-fill: #555;");
            bioBox.getChildren().add(bioLbl);
        }

        // Action buttons
        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().add("primary-button");
        editBtn.setMinWidth(80);
        editBtn.setOnAction(e -> showEditDialog(user, librarian, container));

        Button toggleBtn = buildToggleButton(user, librarian, container);

        HBox btnRow = new HBox(10, editBtn, toggleBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(headerRow, details, activityBox);
        if (!bioBox.getChildren().isEmpty()) card.getChildren().add(bioBox);
        card.getChildren().addAll(new javafx.scene.control.Separator(), btnRow);
        return card;
    }

    private static Label buildStatusBadge(User user) {
        Label badge;
        if (!user.isActive()) {
            badge = new Label("DEACTIVATED");
            badge.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-padding: 2 8 2 8; -fx-background-radius: 10; -fx-font-size: 10;");
        } else {
            badge = new Label("ACTIVE");
            badge.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white; -fx-padding: 2 8 2 8; -fx-background-radius: 10; -fx-font-size: 10;");
        }
        return badge;
    }

    private static Button buildToggleButton(User user, User librarian, VBox container) {
        if (user.isActive()) {
            Button deactivateBtn = new Button("Deactivate");
            deactivateBtn.getStyleClass().add("secondary-button");
            deactivateBtn.setMinWidth(100);
            deactivateBtn.setStyle("-fx-text-fill: #d9534f;");
            deactivateBtn.setOnAction(e -> handleDeactivate(user, librarian, container));
            return deactivateBtn;
        } else {
            Button reactivateBtn = new Button("Reactivate");
            reactivateBtn.getStyleClass().add("secondary-button");
            reactivateBtn.setMinWidth(100);
            reactivateBtn.setStyle("-fx-text-fill: #4caf50;");
            reactivateBtn.setOnAction(e -> handleReactivate(user, librarian, container));
            return reactivateBtn;
        }
    }

    private static void addDetail(GridPane grid, int row, String label, String value) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 11;");
        grid.add(lbl, 0, row);
        grid.add(val, 1, row);
    }

    private static String formatRole(Role role) {
        return switch (role) {
            case STUDENT -> "Student";
            case STAFF -> "Staff";
            case AUTHOR -> "Author";
            case LIBRARIAN -> "Librarian";
        };
    }

    // ── Add New User dialog ───────────────────────────────────────────────

    private static void showAddUserDialog(VBox container, User librarian) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add New User");
        dialog.setHeaderText("Create a new user account");

        Label usernameLbl = new Label("Username *:");
        usernameLbl.setStyle("-fx-font-weight: bold;");
        TextField usernameField = new TextField();
        usernameField.setPromptText("Unique login name");
        usernameField.setPrefWidth(280);

        Label fullNameLbl = new Label("Full Name *:");
        fullNameLbl.setStyle("-fx-font-weight: bold;");
        TextField fullNameField = new TextField();
        fullNameField.setPromptText("First and last name");
        fullNameField.setPrefWidth(280);

        Label roleLbl = new Label("Role *:");
        roleLbl.setStyle("-fx-font-weight: bold;");
        ComboBox<String> roleCombo = new ComboBox<>();
        roleCombo.getItems().addAll("STUDENT", "STAFF", "AUTHOR", "LIBRARIAN");
        roleCombo.setValue("STUDENT");
        roleCombo.setPrefWidth(280);

        Label passwordLbl = new Label("Password *:");
        passwordLbl.setStyle("-fx-font-weight: bold;");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Min 8 chars, 1 uppercase, 1 digit, 1 special");
        passwordField.setPrefWidth(280);

        Label confirmPwLbl = new Label("Confirm Password *:");
        confirmPwLbl.setStyle("-fx-font-weight: bold;");
        PasswordField confirmPwField = new PasswordField();
        confirmPwField.setPromptText("Re-enter password");
        confirmPwField.setPrefWidth(280);

        Label empIdLbl = new Label("Employee ID:");
        empIdLbl.setStyle("-fx-font-weight: bold;");
        TextField empIdField = new TextField();
        empIdField.setPromptText("Optional — for Staff and Librarian");
        empIdField.setPrefWidth(280);

        Label bioLbl = new Label("Bio:");
        bioLbl.setStyle("-fx-font-weight: bold;");
        TextArea bioArea = new TextArea();
        bioArea.setPromptText("Short biography — for Authors");
        bioArea.setWrapText(true);
        bioArea.setPrefRowCount(2);
        bioArea.setPrefWidth(280);

        Label errorLbl = new Label("");
        errorLbl.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11;");
        errorLbl.setWrapText(true);
        errorLbl.setMaxWidth(320);

        Label noteInfo = new Label("* Required. Username cannot be changed after creation.");
        noteInfo.setStyle("-fx-font-size: 10; -fx-text-fill: #888;");

        VBox formContent = new VBox(8,
                usernameLbl, usernameField,
                fullNameLbl, fullNameField,
                roleLbl, roleCombo,
                passwordLbl, passwordField,
                confirmPwLbl, confirmPwField,
                empIdLbl, empIdField,
                bioLbl, bioArea,
                errorLbl,
                noteInfo);
        formContent.setPadding(new Insets(15));

        ScrollPane formScroll = new ScrollPane(formContent);
        formScroll.setFitToWidth(true);
        formScroll.setPrefHeight(420);

        DialogPane dp = dialog.getDialogPane();
        dp.setContent(formScroll);
        dp.setPrefWidth(400);
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Prevent dialog close on validation failure
        Button okBtn = (Button) dp.lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            errorLbl.setText("");
            String username = usernameField.getText().trim();
            String fullName = fullNameField.getText().trim();
            String password = passwordField.getText();
            String confirmPw = confirmPwField.getText();

            if (username.isBlank()) { errorLbl.setText("Username is required."); event.consume(); return; }
            if (fullName.isBlank()) { errorLbl.setText("Full Name is required."); event.consume(); return; }
            if (password.isBlank()) { errorLbl.setText("Password is required."); event.consume(); return; }
            if (!password.equals(confirmPw)) { errorLbl.setText("Passwords do not match."); event.consume(); return; }

            try {
                Validators.validatePasswordStrength(password);
            } catch (ValidationException ex) {
                errorLbl.setText(ex.getMessage()); event.consume(); return;
            }

            // Check username uniqueness
            try {
                if (UserDao.findByUsername(username).isPresent()) {
                    errorLbl.setText("Username \"" + username + "\" is already taken."); event.consume();
                }
            } catch (SQLException ex) {
                errorLbl.setText("Database error: " + ex.getMessage()); event.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.filter(bt -> bt == ButtonType.OK).isPresent()) {
            String username   = usernameField.getText().trim();
            String fullName   = fullNameField.getText().trim();
            Role   role       = Role.valueOf(roleCombo.getValue());
            String password   = passwordField.getText();
            String empId      = empIdField.getText().trim();
            String bio        = bioArea.getText().trim();
            String salt       = PasswordHasher.generateSalt();
            String hash       = PasswordHasher.hash(password, salt);
            String createdAt  = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            try {
                UserDao.insert(username, fullName, role, hash, salt, createdAt,
                        bio.isEmpty() ? null : bio,
                        empId.isEmpty() ? null : empId);
                showSuccess("User Created",
                        "Account \"" + username + "\" (" + formatRole(role) + ") created successfully.");
                loadUsers(container, librarian);
            } catch (SQLException ex) {
                showError("Create Failed", "A database error occurred: " + ex.getMessage());
            }
        }
    }

    // ── Edit dialog ───────────────────────────────────────────────────────

    private static void showEditDialog(User user, User librarian, VBox container) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit User: " + user.getUsername());
        dialog.setHeaderText("Edit profile details for " + user.getFullName() + " (" + formatRole(user.getRole()) + ")");

        Label fullNameLbl = new Label("Full Name *:");
        fullNameLbl.setStyle("-fx-font-weight: bold;");
        TextField fullNameField = new TextField(user.getFullName() != null ? user.getFullName() : "");
        fullNameField.setPromptText("First and last name");
        fullNameField.setPrefWidth(280);

        Label empIdLbl = new Label("Employee ID:");
        empIdLbl.setStyle("-fx-font-weight: bold;");
        TextField empIdField = new TextField(user.getEmployeeId() != null ? user.getEmployeeId() : "");
        empIdField.setPromptText("Optional");
        empIdField.setPrefWidth(280);

        Label bioLbl = new Label("Bio:");
        bioLbl.setStyle("-fx-font-weight: bold;");
        TextArea bioArea = new TextArea(user.getBio() != null ? user.getBio() : "");
        bioArea.setWrapText(true);
        bioArea.setPrefRowCount(3);
        bioArea.setPrefWidth(280);
        bioArea.setPromptText("Short biography (visible to authors and librarians)");

        boolean showEmpId = user.getRole() == Role.STAFF || user.getRole() == Role.LIBRARIAN;
        boolean showBio   = user.getRole() == Role.AUTHOR;

        VBox formContent = new VBox(10);
        formContent.setPadding(new Insets(15));
        formContent.getChildren().addAll(fullNameLbl, fullNameField);
        if (showEmpId) formContent.getChildren().addAll(empIdLbl, empIdField);
        if (showBio)   formContent.getChildren().addAll(bioLbl, bioArea);

        Label noteInfo = new Label("* Required field. Username and role cannot be changed.");
        noteInfo.setStyle("-fx-font-size: 10; -fx-text-fill: #888;");
        formContent.getChildren().add(noteInfo);

        DialogPane dp = dialog.getDialogPane();
        dp.setContent(formContent);
        dp.setPrefWidth(380);
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) dp.lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (fullNameField.getText().trim().isBlank()) {
                showError("Validation Error", "Full Name is required.");
                event.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String newFullName = fullNameField.getText().trim();
            String newEmpId = showEmpId ? empIdField.getText().trim() : user.getEmployeeId();
            String newBio   = showBio   ? bioArea.getText().trim()    : user.getBio();

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Edit");
            confirm.setHeaderText(null);
            confirm.setContentText("Save changes to user \"" + user.getUsername() + "\"?\n\nFull Name: " + newFullName);
            Optional<ButtonType> confirmed = confirm.showAndWait();
            if (confirmed.isPresent() && confirmed.get() == ButtonType.OK) {
                try {
                    UserDao.updateProfile(user.getId(), newFullName, newEmpId, newBio);
                    try {
                        NotificationService.notifyUserAccountUpdatedByLibrarian(
                                user.getId(), "A librarian updated your profile details.");
                    } catch (SQLException ignored) {}
                    showSuccess("User Updated", "Profile for \"" + user.getUsername() + "\" has been updated.");
                    loadUsers(container, librarian);
                } catch (SQLException ex) {
                    showError("Update Failed", "A database error occurred: " + ex.getMessage());
                }
            }
        }
    }

    // ── Deactivate / reactivate ───────────────────────────────────────────

    private static void handleDeactivate(User user, User librarian, VBox container) {
        if (user.getId() == librarian.getId()) {
            showError("Operation Not Allowed", "You cannot deactivate your own account.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Deactivation");
        confirm.setHeaderText(null);
        confirm.setContentText(
                "Deactivate account \"" + user.getUsername() + "\" (" + formatRole(user.getRole()) + ")?\n\n"
                + "The user will no longer be able to log in until reactivated.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                UserDao.setActive(user.getId(), false);
                try { NotificationService.notifyUserAccountStatusChangedByLibrarian(user.getId(), false); }
                catch (SQLException ignored) {}
                showSuccess("Account Deactivated", "\"" + user.getUsername() + "\" has been deactivated.");
                loadUsers(container, librarian);
            } catch (SQLException ex) {
                showError("Deactivation Failed", "A database error occurred: " + ex.getMessage());
            }
        }
    }

    private static void handleReactivate(User user, User librarian, VBox container) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Reactivation");
        confirm.setHeaderText(null);
        confirm.setContentText(
                "Reactivate account \"" + user.getUsername() + "\" (" + formatRole(user.getRole()) + ")?\n\n"
                + "The user will regain the ability to log in.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                UserDao.setActive(user.getId(), true);
                try { NotificationService.notifyUserAccountStatusChangedByLibrarian(user.getId(), true); }
                catch (SQLException ignored) {}
                showSuccess("Account Reactivated", "\"" + user.getUsername() + "\" has been reactivated.");
                loadUsers(container, librarian);
            } catch (SQLException ex) {
                showError("Reactivation Failed", "A database error occurred: " + ex.getMessage());
            }
        }
    }

    // ── Bulk deactivate / reactivate ──────────────────────────────────────

    private static void handleBulkDeactivate(VBox container, User librarian, List<User> allUsers) {
        List<User> targets = new ArrayList<>();
        for (User u : allUsers) {
            if (selectedBulkIds.contains(u.getId()) && u.isActive()) {
                if (u.getId() == librarian.getId()) continue;
                targets.add(u);
            }
        }

        if (targets.isEmpty()) {
            showError("No Eligible Users Selected",
                    "Please select at least one active user (other than yourself) to deactivate.");
            return;
        }

        StringBuilder msg = new StringBuilder();
        msg.append("You are about to DEACTIVATE the following ")
           .append(targets.size()).append(" account(s):\n\n");
        for (int i = 0; i < targets.size(); i++) {
            msg.append("  ").append(i + 1).append(". ")
               .append(targets.get(i).getUsername())
               .append(" (").append(formatRole(targets.get(i).getRole())).append(")\n");
        }
        msg.append("\nThese users will no longer be able to log in until reactivated. Proceed?");

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Bulk Deactivation");
        confirm.setHeaderText("Bulk Deactivate — " + targets.size() + " account(s)");
        confirm.setContentText(msg.toString());
        confirm.getDialogPane().setPrefWidth(480);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        List<String> failed = new ArrayList<>();
        for (User u : targets) {
            try {
                UserDao.setActive(u.getId(), false);
                try { NotificationService.notifyUserAccountStatusChangedByLibrarian(u.getId(), false); }
                catch (SQLException ignored) {}
            } catch (SQLException ex) {
                failed.add(u.getUsername() + " (" + ex.getMessage() + ")");
            }
        }

        if (failed.isEmpty()) {
            showSuccess("Bulk Deactivation Complete", targets.size() + " account(s) have been deactivated.");
        } else {
            showError("Bulk Deactivation Partially Failed",
                    "The following accounts could not be deactivated:\n" + String.join("\n", failed));
        }
        loadUsers(container, librarian);
    }

    private static void handleBulkReactivate(VBox container, User librarian, List<User> allUsers) {
        List<User> targets = new ArrayList<>();
        for (User u : allUsers) {
            if (selectedBulkIds.contains(u.getId()) && !u.isActive()) {
                targets.add(u);
            }
        }

        if (targets.isEmpty()) {
            showError("No Eligible Users Selected",
                    "Please select at least one deactivated user to reactivate.");
            return;
        }

        StringBuilder msg = new StringBuilder();
        msg.append("You are about to REACTIVATE the following ")
           .append(targets.size()).append(" account(s):\n\n");
        for (int i = 0; i < targets.size(); i++) {
            msg.append("  ").append(i + 1).append(". ")
               .append(targets.get(i).getUsername())
               .append(" (").append(formatRole(targets.get(i).getRole())).append(")\n");
        }
        msg.append("\nThese users will regain the ability to log in. Proceed?");

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Bulk Reactivation");
        confirm.setHeaderText("Bulk Reactivate — " + targets.size() + " account(s)");
        confirm.setContentText(msg.toString());
        confirm.getDialogPane().setPrefWidth(480);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        List<String> failed = new ArrayList<>();
        for (User u : targets) {
            try {
                UserDao.setActive(u.getId(), true);
                try { NotificationService.notifyUserAccountStatusChangedByLibrarian(u.getId(), true); }
                catch (SQLException ignored) {}
            } catch (SQLException ex) {
                failed.add(u.getUsername() + " (" + ex.getMessage() + ")");
            }
        }

        if (failed.isEmpty()) {
            showSuccess("Bulk Reactivation Complete", targets.size() + " account(s) have been reactivated.");
        } else {
            showError("Bulk Reactivation Partially Failed",
                    "The following accounts could not be reactivated:\n" + String.join("\n", failed));
        }
        loadUsers(container, librarian);
    }

    // ── Alert helpers ─────────────────────────────────────────────────────

    private static void showSuccess(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private static void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
