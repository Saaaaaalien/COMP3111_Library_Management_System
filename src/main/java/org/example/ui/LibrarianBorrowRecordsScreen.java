package org.example.ui;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.example.app.Navigator;
import org.example.db.BorrowDao;
import org.example.db.BorrowDao.BorrowRecord;
import org.example.domain.User;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Task 3.6 – Librarian "Borrowed Books Record" screen.
 * <p>
 * Displays every borrow record in the system with:
 *   – Book Title, Borrower Username / Full Name
 *   – Borrow Date, Due Date, Return Date
 *   – Status badge: ACTIVE / OVERDUE / RETURNED
 * <p>
 * Supports free-text search (book title or borrower username) and a status
 * filter (All / Active / Overdue / Returned).
 */
public final class LibrarianBorrowRecordsScreen {

    private LibrarianBorrowRecordsScreen() {}

    // ── Persistent filter state ───────────────────────────────────────────────
    private static String currentSearch = "";
    private static String currentStatus = "All";

    // Status filter options
    private static final String STATUS_ALL      = "All";
    private static final String STATUS_ACTIVE   = "Active";
    private static final String STATUS_OVERDUE  = "Overdue";
    private static final String STATUS_RETURNED = "Returned";

    // ── Scene factory ─────────────────────────────────────────────────────────

    public static Scene create(Navigator navigator, User librarian) {
        currentSearch = "";
        currentStatus = STATUS_ALL;

        // Header
        Label titleLbl = new Label("Borrowed Books Records");
        titleLbl.getStyleClass().add("screen-title");

        Label subLbl = new Label("Logged in as: " + librarian.getFullName());
        subLbl.getStyleClass().add("info-label");

        // ── Search / filter bar (built inline so Reset can update the controls) ──
        Label searchLbl = new Label("Search (Title / Username):");
        searchLbl.setStyle("-fx-font-size: 11;");

        TextField searchField = new TextField(currentSearch);
        searchField.setPromptText("Type to search…");
        searchField.setPrefWidth(220);
        searchField.textProperty().addListener((obs, old, val) -> currentSearch = val == null ? "" : val.trim());

        Label statusLbl = new Label("Status:");
        statusLbl.setStyle("-fx-font-size: 11;");

        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().addAll(STATUS_ALL, STATUS_ACTIVE, STATUS_OVERDUE, STATUS_RETURNED);
        statusBox.setValue(currentStatus);
        statusBox.setOnAction(e -> currentStatus = statusBox.getValue());

        HBox searchFilterBar = new HBox(14, searchLbl, searchField, statusLbl, statusBox);
        searchFilterBar.setPadding(new Insets(10, 0, 6, 0));
        searchFilterBar.setAlignment(Pos.CENTER_LEFT);

        VBox headerBox = new VBox(6, titleLbl, subLbl, searchFilterBar);
        headerBox.setPadding(new Insets(20, 20, 0, 20));
        headerBox.setStyle("-fx-border-color: #f0f0f0; -fx-border-width: 0 0 1 0;");

        // Summary counters (refreshed on each load)
        Label summaryLbl = new Label();
        summaryLbl.setStyle("-fx-font-size: 11; -fx-text-fill: #666;");

        // Record list
        VBox listContent = new VBox(8);
        listContent.setPadding(new Insets(16));

        ScrollPane scroll = new ScrollPane(listContent);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Holds the most-recently rendered filtered list for CSV export
        final List<BorrowRecord>[] lastFiltered = new List[]{List.of()};

        // Initial load
        loadRecords(listContent, summaryLbl, lastFiltered);

        // Action bar
        Button applyBtn = new Button("Search / Filter");
        applyBtn.getStyleClass().add("primary-button");
        applyBtn.setOnAction(e -> loadRecords(listContent, summaryLbl, lastFiltered));

        Button resetBtn = new Button("Reset");
        resetBtn.getStyleClass().add("secondary-button");
        resetBtn.setOnAction(e -> {
            currentSearch = "";
            currentStatus = STATUS_ALL;
            // Sync the visible controls so the UI matches the reset state
            searchField.setText("");
            statusBox.setValue(STATUS_ALL);
            loadRecords(listContent, summaryLbl, lastFiltered);
        });

        Button exportCsvBtn = new Button("\uD83D\uDCBE Export CSV");
        exportCsvBtn.getStyleClass().add("secondary-button");
        exportCsvBtn.setOnAction(e -> exportToCsv(lastFiltered[0]));

        HBox actionBar = new HBox(10, applyBtn, resetBtn, exportCsvBtn, summaryLbl);
        actionBar.setPadding(new Insets(10, 20, 0, 20));
        actionBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(summaryLbl, Priority.ALWAYS);

        // Footer navigation
        // Button notificationsBtn = new Button("🔔 Notifications");
        // notificationsBtn.getStyleClass().add("secondary-button");
        // notificationsBtn.setOnAction(e -> navigator.showLibrarianNotifications(librarian));

            // Navigation handled by global menu; remove per-screen Back button
            // HBox footerBox = new HBox(10, notificationsBtn);
        // footerBox.setPadding(new Insets(15, 20, 15, 20));
        // footerBox.setAlignment(Pos.CENTER_RIGHT);

        // Root
        BorderPane root = new BorderPane();
        root.setTop(headerBox);
        root.setCenter(new VBox(actionBar, scroll));
        // root.setBottom(footerBox);
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL css = LibrarianBorrowRecordsScreen.class.getResource("/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        return scene;
    }

    // ── Data load + render ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static void loadRecords(VBox listContent, Label summaryLbl,
                                    List<BorrowRecord>[] lastFiltered) {
        listContent.getChildren().clear();

        List<BorrowRecord> records;
        try {
            records = BorrowDao.findAllWithBorrower();
        } catch (SQLException ex) {
            new Alert(Alert.AlertType.ERROR, "Failed to load borrow records: " + ex.getMessage())
                .showAndWait();
            return;
        }

        String nowIso = Instant.now().toString();

        // Apply filters
        String searchTerm = currentSearch.toLowerCase();
        List<BorrowRecord> filtered = records.stream()
            .filter(r -> {
                if (!searchTerm.isBlank()) {
                    boolean matches = r.bookTitle().toLowerCase().contains(searchTerm)
                        || r.borrowerUsername().toLowerCase().contains(searchTerm)
                        || r.borrowerFullName().toLowerCase().contains(searchTerm);
                    if (!matches) return false;
                }
                return switch (currentStatus) {
                    case STATUS_ACTIVE   -> r.isActive() && !r.isOverdue(nowIso);
                    case STATUS_OVERDUE  -> r.isOverdue(nowIso);
                    case STATUS_RETURNED -> !r.isActive();
                    default              -> true;  // All
                };
            })
            .collect(Collectors.toList());

        // Store for CSV export
        if (lastFiltered != null) lastFiltered[0] = filtered;

        // Summary counts across the full unfiltered set
        long totalActive   = records.stream().filter(r -> r.isActive() && !r.isOverdue(nowIso)).count();
        long totalOverdue  = records.stream().filter(r -> r.isOverdue(nowIso)).count();
        long totalReturned = records.stream().filter(r -> !r.isActive()).count();
        summaryLbl.setText(String.format(
            "Total: %d  |  Active: %d  |  Overdue: %d  |  Returned: %d  |  Showing: %d",
            records.size(), totalActive, totalOverdue, totalReturned, filtered.size()));

        if (filtered.isEmpty()) {
            Label emptyLbl = new Label("No borrow records match the current search / filter.");
            emptyLbl.setStyle("-fx-text-fill: #888; -fx-font-size: 13; -fx-padding: 20;");
            listContent.getChildren().add(emptyLbl);
            return;
        }

        // Table header
        listContent.getChildren().add(buildHeaderRow());

        for (BorrowRecord r : filtered) {
            listContent.getChildren().add(buildRecordRow(r, nowIso));
        }
    }

    // ── Column-header row ─────────────────────────────────────────────────────

    private static HBox buildHeaderRow() {
        HBox row = new HBox();
        row.setStyle(
            "-fx-background-color: #2c3e50;" +
            "-fx-padding: 8 12 8 12;" +
            "-fx-border-radius: 4 4 0 0;" +
            "-fx-background-radius: 4 4 0 0;"
        );

        row.getChildren().addAll(
            headerCell("Book Title",        260),
            headerCell("Borrower",          160),
            headerCell("Borrow Date",       130),
            headerCell("Due Date",          120),
            headerCell("Return Date",       120),
            headerCell("Status",             90)
        );
        return row;
    }

    private static Label headerCell(String text, double width) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11;");
        lbl.setMinWidth(width);
        lbl.setPrefWidth(width);
        return lbl;
    }

    // ── Individual record row ─────────────────────────────────────────────────

    private static HBox buildRecordRow(BorrowRecord r, String nowIso) {
        boolean returned = !r.isActive();
        boolean overdue  = r.isOverdue(nowIso);

        // Status badge
        String statusText  = returned ? "RETURNED" : (overdue ? "OVERDUE" : "ACTIVE");
        String badgeColour = returned ? "#27ae60"  : (overdue ? "#e74c3c" : "#2980b9");
        String rowBg       = overdue  ? "#ffeaea"  : (returned ? "#f9f9f9" : "#ffffff");
        String rowBorder   = overdue  ? "#e74c3c"  : "#e8e8e8";
        String rowBorderW  = overdue  ? "0 0 1 4"  : "0 0 1 0";

        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle(
            "-fx-background-color: " + rowBg + ";" +
            "-fx-padding: 10 12 10 12;" +
            "-fx-border-color: " + rowBorder + ";" +
            "-fx-border-width: " + rowBorderW + ";"
        );

        // Book title + author (stacked)
        VBox titleBox = new VBox(2);
        titleBox.setMinWidth(260);
        titleBox.setPrefWidth(260);
        Label titleLbl = new Label(r.bookTitle());
        titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12; -fx-wrap-text: true;"
                + (overdue ? " -fx-text-fill: #c0392b;" : ""));
        titleLbl.setWrapText(true);
        titleLbl.setMaxWidth(250);
        Label authorLbl = new Label("by " + r.bookAuthor());
        authorLbl.setStyle("-fx-font-size: 10; -fx-text-fill: #777;");
        titleBox.getChildren().addAll(titleLbl, authorLbl);

        // Borrower username + full name
        VBox borrowerBox = new VBox(2);
        borrowerBox.setMinWidth(160);
        borrowerBox.setPrefWidth(160);
        Label usernameLbl = new Label(r.borrowerUsername());
        usernameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");
        Label fullNameLbl = new Label(r.borrowerFullName());
        fullNameLbl.setStyle("-fx-font-size: 10; -fx-text-fill: #777;");
        borrowerBox.getChildren().addAll(usernameLbl, fullNameLbl);

        Label borrowedLbl = dateCell(r.borrowedAt(), 130);
        Label dueLbl      = dateCell(r.dueAt(), 120);
        if (!returned && overdue) {
            dueLbl.setStyle("-fx-font-size: 11; -fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-min-width: 120; -fx-pref-width: 120;");
        }
        Label returnedLbl = dateCell(r.returnedAt(), 120);

        Label statusBadge = new Label(statusText);
        statusBadge.setStyle(
            "-fx-background-color: " + badgeColour + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 10;" +
            "-fx-font-weight: bold;" +
            "-fx-padding: 3 8 3 8;" +
            "-fx-background-radius: 10;" +
            "-fx-min-width: 90;" +
            "-fx-alignment: center;"
        );

        row.getChildren().addAll(titleBox, borrowerBox, borrowedLbl, dueLbl, returnedLbl, statusBadge);
        return row;
    }

    // ── CSV Export ─────────────────────────────────────────────────────────

    private static void exportToCsv(List<BorrowRecord> records) {
        if (records == null || records.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "No records to export. Apply a filter first if needed.")
                    .showAndWait();
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Borrow Records as CSV");
        chooser.setInitialFileName("borrow_records.csv");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"));
        File dest = chooser.showSaveDialog(null);
        if (dest == null) return; // cancelled

        String nowIso = Instant.now().toString();
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(dest)))) {
            // Header row
            pw.println("Book Title,Book Author,Borrower Username,Borrower Full Name,Borrow Date,Due Date,Return Date,Status");
            for (BorrowRecord r : records) {
                String status = !r.isActive() ? "RETURNED" : (r.isOverdue(nowIso) ? "OVERDUE" : "ACTIVE");
                pw.println(String.join(",",
                        escapeCsv(r.bookTitle()),
                        escapeCsv(r.bookAuthor()),
                        escapeCsv(r.borrowerUsername()),
                        escapeCsv(r.borrowerFullName()),
                        escapeCsv(dateOnly(r.borrowedAt())),
                        escapeCsv(dateOnly(r.dueAt())),
                        escapeCsv(dateOnly(r.returnedAt())),
                        status
                ));
            }
            new Alert(Alert.AlertType.INFORMATION,
                    records.size() + " record(s) exported to:\n" + dest.getAbsolutePath())
                    .showAndWait();
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR, "Export failed: " + ex.getMessage()).showAndWait();
        }
    }

    /** Wraps a CSV field in double quotes and escapes any embedded quotes. */
    private static String escapeCsv(String value) {
        if (value == null || value.isBlank()) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /** Returns only the date part (YYYY-MM-DD) of an ISO-8601 instant string, or "—". */
    private static String dateOnly(String iso) {
        if (iso == null || iso.isBlank()) return "-";
        return iso.length() >= 10 ? iso.substring(0, 10) : iso;
    }

    /** Formats an ISO-8601 instant string to a readable short date, or "—" if absent. */
    private static Label dateCell(String iso, double width) {
        String text = "-";
        if (iso != null && !iso.isBlank()) {
            // Keep only the date portion (first 10 chars of ISO string)
            text = iso.length() >= 10 ? iso.substring(0, 10) : iso;
        }
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 11; -fx-min-width: " + width + "; -fx-pref-width: " + width + ";");
        return lbl;
    }
}
