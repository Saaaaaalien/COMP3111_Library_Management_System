package org.example.ui;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.app.Navigator;
import org.example.db.UserDao;
import org.example.domain.Role;
import org.example.domain.User;
import org.example.service.NotificationService;

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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Librarian screen: view, search, filter, edit, deactivate, and reactivate all users.
 */
public final class LibrarianManageUsersScreen {

    private LibrarianManageUsersScreen() {}

    // Search/filter state — reset each time the screen is opened (not preserved across navigation)
    private static String currentSearchTerm = "";
    private static String currentRoleFilter = "";

    // Tracks which user IDs are selected for bulk actions
    private static final Set<Long> selectedBulkIds = new LinkedHashSet<>();

    public static Scene create(Navigator navigator, User librarian) {
        // Reset search/filter state on every open so the screen starts clean
        currentSearchTerm = "";
        currentRoleFilter = "";

        Label title = new Label("Manage Users");
        title.getStyleClass().add("screen-title");

        Label librarianInfoLbl = new Label("Logged in as: " + librarian.getFullName());
        librarianInfoLbl.getStyleClass().add("info-label");

        // Search + filter bar
        HBox searchFilterBox = buildSearchFilterBox();

        // Scrollable user list
        VBox userListContent = new VBox(12);
        userListContent.setPadding(new Insets(20));

        ScrollPane scrollPane = new ScrollPane(userListContent);
        scrollPane.setFitToWidth(true);

        // Initial load
        loadUsers(userListContent, librarian);

        // Apply button
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

        HBox actionBar = new HBox(10, applyBtn, resetBtn);
        actionBar.setPadding(new Insets(10, 20, 0, 20));
        actionBar.setAlignment(Pos.CENTER_LEFT);

        // Navigation handled by global menu; removed per-screen Back button
        // Button myProfileBtn = new Button("My Profile");
        // myProfileBtn.getStyleClass().add("secondary-button");
        // myProfileBtn.setOnAction(e -> navigator.showLibrarianProfile(librarian));

        // Button borrowRecordsBtn = new Button("Borrow Records");
        // borrowRecordsBtn.getStyleClass().add("secondary-button");
        // borrowRecordsBtn.setOnAction(e -> navigator.showLibrarianBorrowRecords(librarian));

        // Button notificationsBtn = new Button("🔔 Notifications");
        // notificationsBtn.getStyleClass().add("secondary-button");
        // notificationsBtn.setOnAction(e -> navigator.showLibrarianNotifications(librarian));

        VBox headerBox = new VBox(8, title, librarianInfoLbl, searchFilterBox);
        headerBox.setPadding(new Insets(20, 20, 0, 20));
        headerBox.setStyle("-fx-border-color: #f0f0f0; -fx-border-width: 0 0 1 0;");

        // HBox footerBtns = new HBox(10, borrowRecordsBtn, notificationsBtn, myProfileBtn);
        // footerBtns.setAlignment(Pos.CENTER_RIGHT);

        // VBox footerBox = new VBox();
        // footerBox.setPadding(new Insets(15, 20, 15, 20));
        // footerBox.setAlignment(Pos.CENTER_RIGHT);
        // footerBox.getChildren().add(footerBtns);

        BorderPane root = new BorderPane();
        root.setTop(headerBox);
        root.setCenter(new VBox(actionBar, scrollPane));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        // root.setBottom(footerBox);
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

            // ── Count label ─────────────────────────────────────────────
            Label countLbl = new Label("Results: " + users.size() + " user(s)");
            countLbl.setStyle("-fx-font-size: 12; -fx-text-fill: #555;");
            container.getChildren().add(countLbl);

            // ── Collect CheckBoxes for Select-All wiring ─────────────────
            List<CheckBox> allCheckBoxes = new ArrayList<>();

            // ── Bulk action bar ──────────────────────────────────────────
            Button selectAllBtn    = new Button("\u2611 Select All");
            selectAllBtn.getStyleClass().add("secondary-button");

            Button deselectAllBtn  = new Button("\u2610 Deselect All");
            deselectAllBtn.getStyleClass().add("secondary-button");

            Button bulkDeactivateBtn = new Button("\u26D4 Bulk Deactivate");
            bulkDeactivateBtn.getStyleClass().add("secondary-button");
            bulkDeactivateBtn.setStyle("-fx-text-fill: #d9534f;");
            bulkDeactivateBtn.setOnAction(e -> handleBulkDeactivate(container, librarian, users));

            Button bulkReactivateBtn = new Button("\u2705 Bulk Reactivate");
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

            // ── Build cards ──────────────────────────────────────────────
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

    /**
     * @param allCheckBoxes mutable list — this card's selection CheckBox is
     *                      appended so the bulk bar can select/deselect all.
     */
    private static VBox buildUserCard(User user, User librarian, VBox container,
                                      List<CheckBox> allCheckBoxes) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-border-color: #ddd; -fx-border-width: 1; -fx-border-radius: 5;");

        // ── Bulk-selection CheckBox ──────────────────────────────────────
        CheckBox selectBox = new CheckBox("Select for bulk action");
        selectBox.setStyle("-fx-font-size: 11; -fx-text-fill: #555;");
        selectBox.setSelected(selectedBulkIds.contains(user.getId()));
        selectBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) selectedBulkIds.add(user.getId());
            else        selectedBulkIds.remove(user.getId());
        });
        card.getChildren().add(selectBox);
        allCheckBoxes.add(selectBox);

        // ── Header row: username + status badge ──
        Label usernameLbl = new Label(user.getUsername());
        usernameLbl.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        Label statusBadge = buildStatusBadge(user);

        HBox headerRow = new HBox(10, usernameLbl, statusBadge);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        // ── Detail grid ──
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

        // ── Bio (authors only) ──
        VBox bioBox = new VBox();
        if (user.getRole() == Role.AUTHOR && user.getBio() != null && !user.getBio().isBlank()) {
            Label bioLbl = new Label("Bio: " + user.getBio());
            bioLbl.setWrapText(true);
            bioLbl.setStyle("-fx-font-size: 10; -fx-text-fill: #555;");
            bioBox.getChildren().add(bioLbl);
        }

        // ── Action buttons ──
        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().add("primary-button");
        editBtn.setMinWidth(80);
        editBtn.setOnAction(e -> showEditDialog(user, librarian, container));

        Button toggleBtn = buildToggleButton(user, librarian, container);

        HBox btnRow = new HBox(10, editBtn, toggleBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(headerRow, details);
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

    // ── Edit dialog ───────────────────────────────────────────────────────

    private static void showEditDialog(User user, User librarian, VBox container) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit User: " + user.getUsername());
        dialog.setHeaderText("Edit profile details for " + user.getFullName() + " (" + formatRole(user.getRole()) + ")");

        // Fields
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

        // Only show bio for AUTHORS; show employee id for STAFF / LIBRARIAN
        boolean showEmpId = user.getRole() == Role.STAFF || user.getRole() == Role.LIBRARIAN;
        boolean showBio = user.getRole() == Role.AUTHOR;

        VBox formContent = new VBox(10);
        formContent.setPadding(new Insets(15));
        formContent.getChildren().addAll(fullNameLbl, fullNameField);
        if (showEmpId) {
            formContent.getChildren().addAll(empIdLbl, empIdField);
        }
        if (showBio) {
            formContent.getChildren().addAll(bioLbl, bioArea);
        }

        Label noteInfo = new Label("* Required field. Username and role cannot be changed.");
        noteInfo.setStyle("-fx-font-size: 10; -fx-text-fill: #888;");
        formContent.getChildren().add(noteInfo);

        DialogPane dp = dialog.getDialogPane();
        dp.setContent(formContent);
        dp.setPrefWidth(380);
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Prevent OK close when validation fails
        Button okBtn = (Button) dp.lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String newFullName = fullNameField.getText().trim();
            if (newFullName.isBlank()) {
                showError("Validation Error", "Full Name is required.");
                event.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String newFullName = fullNameField.getText().trim();
            String newEmpId = showEmpId ? empIdField.getText().trim() : user.getEmployeeId();
            String newBio = showBio ? bioArea.getText().trim() : user.getBio();

            // Confirmation
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
                                user.getId(),
                                "A librarian updated your profile details."
                        );
                    } catch (SQLException ignored) {
                    }
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
        // Librarians cannot deactivate themselves
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
                try {
                    NotificationService.notifyUserAccountStatusChangedByLibrarian(user.getId(), false);
                } catch (SQLException ignored) {
                }
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
                try {
                    NotificationService.notifyUserAccountStatusChangedByLibrarian(user.getId(), true);
                } catch (SQLException ignored) {
                }
                showSuccess("Account Reactivated", "\"" + user.getUsername() + "\" has been reactivated.");
                loadUsers(container, librarian);
            } catch (SQLException ex) {
                showError("Reactivation Failed", "A database error occurred: " + ex.getMessage());
            }
        }
    }

    // ── Bulk deactivate / reactivate ──────────────────────────────────────

    /**
     * Deactivates all currently selected ACTIVE users in one go.
     * Skips the librarian's own account and already-deactivated accounts.
     */
    private static void handleBulkDeactivate(VBox container, User librarian, List<User> allUsers) {
        List<User> targets = new ArrayList<>();
        for (User u : allUsers) {
            if (selectedBulkIds.contains(u.getId()) && u.isActive()) {
                if (u.getId() == librarian.getId()) continue; // cannot self-deactivate
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
        confirm.setHeaderText("Bulk Deactivate \u2014 " + targets.size() + " account(s)");
        confirm.setContentText(msg.toString());
        confirm.getDialogPane().setPrefWidth(480);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        List<String> failed = new ArrayList<>();
        for (User u : targets) {
            try {
                UserDao.setActive(u.getId(), false);
                try {
                    NotificationService.notifyUserAccountStatusChangedByLibrarian(u.getId(), false);
                } catch (SQLException ignored) {
                }
            } catch (SQLException ex) {
                failed.add(u.getUsername() + " (" + ex.getMessage() + ")");
            }
        }

        if (failed.isEmpty()) {
            showSuccess("Bulk Deactivation Complete",
                    targets.size() + " account(s) have been deactivated.");
        } else {
            showError("Bulk Deactivation Partially Failed",
                    "The following accounts could not be deactivated:\n" + String.join("\n", failed));
        }
        loadUsers(container, librarian);
    }

    /**
     * Reactivates all currently selected INACTIVE users in one go.
     */
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
        confirm.setHeaderText("Bulk Reactivate \u2014 " + targets.size() + " account(s)");
        confirm.setContentText(msg.toString());
        confirm.getDialogPane().setPrefWidth(480);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        List<String> failed = new ArrayList<>();
        for (User u : targets) {
            try {
                UserDao.setActive(u.getId(), true);
                try {
                    NotificationService.notifyUserAccountStatusChangedByLibrarian(u.getId(), true);
                } catch (SQLException ignored) {
                }
            } catch (SQLException ex) {
                failed.add(u.getUsername() + " (" + ex.getMessage() + ")");
            }
        }

        if (failed.isEmpty()) {
            showSuccess("Bulk Reactivation Complete",
                    targets.size() + " account(s) have been reactivated.");
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
