package org.example.ui;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.example.app.Navigator;
import org.example.db.BookDao;
import org.example.db.BookRequestDao;
import org.example.domain.BookRequest;
import org.example.domain.BookRequest.RequestStatus;
import org.example.domain.User;
import org.example.service.BookDownloaderService;
import org.example.service.BookSummaryService;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Librarian screen for managing book requests from students/staff.
 *
 * Workflow:
 *   1. PENDING    — student/staff submitted a request; shown in the table.
 *   2. DOWNLOADED — librarian searched, selected, and downloaded a book file;
 *                   row turns green so the librarian can then approve it.
 *   3. APPROVED   — book inserted into the catalog; row leaves the active list.
 *   4. REJECTED   — librarian rejected; row leaves the active list.
 */
public final class LibrarianManageBookRequestsScreen {

    private LibrarianManageBookRequestsScreen() {}

    // ── Row model ─────────────────────────────────────────────────────────────

    public static final class RequestRow {
        private final long id;
        private final String title;
        private final String author;
        private final String requestedBy;
        private final String status;
        private final String createdAt;
        private final int requestCount;
        private final int priorityScore;
        private final String priority;
        private final String priorityLabel;

        public RequestRow(BookRequest req, int requestCount) {
            this.id          = req.getId();
            this.title       = req.getTitle();
            this.author      = req.getAuthorName();
            this.requestedBy = req.getRequestedByName();
            this.status      = req.getStatus().name();
            this.createdAt   = req.getCreatedAt() != null && req.getCreatedAt().length() >= 10
                               ? req.getCreatedAt().substring(0, 10) : "";
            this.requestCount = requestCount;

            int daysAgo = 0;
            try {
                if (!this.createdAt.isEmpty()) {
                    daysAgo = (int) ChronoUnit.DAYS.between(LocalDate.parse(this.createdAt), LocalDate.now());
                }
            } catch (Exception ignored) {}
            this.priorityScore = (daysAgo / 3) + Math.max(0, requestCount - 1) * 3;

            if (priorityScore >= 6) {
                this.priority = "HIGH";
                this.priorityLabel = "HIGH ★★★";
            } else if (priorityScore >= 2) {
                this.priority = "MEDIUM";
                this.priorityLabel = "MED ★★";
            } else {
                this.priority = "LOW";
                this.priorityLabel = "LOW ★";
            }
        }

        public RequestRow(BookRequest req) { this(req, 1); }

        public long   getId()            { return id; }
        public String getTitle()         { return title; }
        public String getAuthor()        { return author; }
        public String getRequestedBy()   { return requestedBy; }
        public String getStatus()        { return status; }
        public String getCreatedAt()     { return createdAt; }
        public int    getRequestCount()  { return requestCount; }
        public int    getPriorityScore() { return priorityScore; }
        public String getPriority()      { return priority; }
        public String getPriorityLabel() { return priorityLabel; }
    }

    // ── Scene factory ─────────────────────────────────────────────────────────

    public static Scene create(Navigator navigator, User librarian) {
        Label titleLbl = new Label("Manage Book Requests");
        titleLbl.getStyleClass().add("screen-title");

        Label infoLbl = new Label(
                "PENDING = new request  •  DOWNLOADED = ready for approval  "
                + "(select a downloaded request, then click Approve to publish it)");
        infoLbl.getStyleClass().add("info-label");
        infoLbl.setWrapText(true);

        TableView<RequestRow> table = new TableView<>();
        configureRequestTable(table);
        loadRequests(table, "");

        HBox searchBox = createSearchBox(table);

        Button approveBtn = new Button("Approve & Publish");
        approveBtn.getStyleClass().add("primary-button");
        approveBtn.setOnAction(e -> handleApproveRequest(table));

        Button rejectBtn = new Button("Reject");
        rejectBtn.getStyleClass().add("danger-button");
        rejectBtn.setOnAction(e -> handleRejectRequest(table));

        Button downloadBtn = new Button("Download Book");
        downloadBtn.getStyleClass().add("secondary-button");
        downloadBtn.setOnAction(e -> handleDownloadBook(table));

        Button viewDetailsBtn = new Button("View Details");
        viewDetailsBtn.getStyleClass().add("secondary-button");
        viewDetailsBtn.setOnAction(e -> handleViewDetails(table));

        Button analyticsBtn = new Button("Analytics");
        analyticsBtn.getStyleClass().add("secondary-button");
        analyticsBtn.setOnAction(e -> showAnalyticsDialog());

        Button statsBtn = new Button("Book Stats");
        statsBtn.getStyleClass().add("secondary-button");
        statsBtn.setOnAction(e -> LibrarianDownloadedBookStatsScreen.show(librarian));

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> loadRequests(table, ""));

        HBox buttonBox = new HBox(8, viewDetailsBtn, downloadBtn, approveBtn, rejectBtn,
                analyticsBtn, statsBtn, refreshBtn);
        buttonBox.setPadding(new Insets(10));
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        VBox headerBox = new VBox(8, titleLbl, infoLbl, searchBox);
        headerBox.setPadding(new Insets(20));
        headerBox.setStyle("-fx-border-color: #f0f0f0; -fx-border-width: 0 0 1 0;");

        ScrollPane tableScroll = new ScrollPane(table);
        tableScroll.setFitToWidth(true);
        VBox.setVgrow(tableScroll, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setTop(headerBox);
        root.setCenter(new VBox(10, tableScroll, buttonBox));
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL cssResource = LibrarianManageBookRequestsScreen.class.getResource("/app.css");
        if (cssResource != null) scene.getStylesheets().add(cssResource.toExternalForm());
        scene.setUserData(librarian);
        return scene;
    }

    // ── Table setup ───────────────────────────────────────────────────────────

    private static HBox createSearchBox(TableView<RequestRow> table) {
        HBox box = new HBox(10);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-border-color: #f5f5f5; -fx-border-width: 0 0 1 0;");

        Label searchLbl = new Label("Search:");
        TextField searchField = new TextField();
        searchField.setPromptText("Search by title, author, or requester…");
        searchField.setPrefWidth(300);

        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().add("secondary-button");
        searchBtn.setOnAction(e -> loadRequests(table, searchField.getText().trim()));
        searchField.setOnAction(e -> loadRequests(table, searchField.getText().trim()));

        box.getChildren().addAll(searchLbl, searchField, searchBtn);
        return box;
    }

    private static void configureRequestTable(TableView<RequestRow> table) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Row colouring: green for DOWNLOADED, warm tints for high/medium priority PENDING.
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(RequestRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else if ("DOWNLOADED".equals(item.getStatus()) || "PROCESSED".equals(item.getStatus())) {
                    setStyle("-fx-background-color: #e8f5e9;");
                } else if ("PENDING".equals(item.getStatus())) {
                    if ("HIGH".equals(item.getPriority())) {
                        setStyle("-fx-background-color: #fff3e0;");
                    } else if ("MEDIUM".equals(item.getPriority())) {
                        setStyle("-fx-background-color: #fffde7;");
                    } else {
                        setStyle("");
                    }
                } else {
                    setStyle("");
                }
            }
        });

        TableColumn<RequestRow, String> priorityCol = new TableColumn<>("Priority");
        priorityCol.setCellValueFactory(new PropertyValueFactory<>("priorityLabel"));
        priorityCol.setPrefWidth(90);
        priorityCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setStyle("");
                } else {
                    setText(item);
                    if (item.startsWith("HIGH")) {
                        setStyle("-fx-text-fill: #e65100; -fx-font-weight: bold;");
                    } else if (item.startsWith("MED")) {
                        setStyle("-fx-text-fill: #f57f17; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #757575;");
                    }
                }
            }
        });

        TableColumn<RequestRow, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(150);

        TableColumn<RequestRow, String> authorCol = new TableColumn<>("Author");
        authorCol.setCellValueFactory(new PropertyValueFactory<>("author"));
        authorCol.setPrefWidth(100);

        TableColumn<RequestRow, String> requestorCol = new TableColumn<>("Requested By");
        requestorCol.setCellValueFactory(new PropertyValueFactory<>("requestedBy"));
        requestorCol.setPrefWidth(120);

        TableColumn<RequestRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(110);

        TableColumn<RequestRow, String> dateCol = new TableColumn<>("Request Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        dateCol.setPrefWidth(100);

        table.getColumns().addAll(priorityCol, titleCol, authorCol, requestorCol, statusCol, dateCol);
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private static void loadRequests(TableView<RequestRow> table, String searchTerm) {
        Task<List<BookRequest>> task = new Task<>() {
            @Override
            protected List<BookRequest> call() throws Exception {
                if (searchTerm == null || searchTerm.isEmpty()) {
                    return BookRequestDao.findActivePending();
                }
                return BookRequestDao.search(searchTerm).stream()
                        .filter(r -> r.getStatus() == RequestStatus.PENDING
                                  || r.getStatus() == RequestStatus.DOWNLOADED
                                  || r.getStatus() == RequestStatus.PROCESSED)
                        .collect(Collectors.toList());
            }

            @Override
            protected void succeeded() {
                List<BookRequest> reqs = getValue();
                // Count duplicate requests per title+author for priority scoring.
                Map<String, Long> countByKey = reqs.stream()
                    .collect(Collectors.groupingBy(
                        r -> r.getTitle().toLowerCase().trim() + "|||" + r.getAuthorName().toLowerCase().trim(),
                        Collectors.counting()
                    ));
                List<RequestRow> rows = reqs.stream()
                    .map(r -> {
                        int count = (int)(long) countByKey.getOrDefault(
                            r.getTitle().toLowerCase().trim() + "|||" + r.getAuthorName().toLowerCase().trim(), 1L);
                        return new RequestRow(r, count);
                    })
                    .sorted((a, b) -> {
                        // DOWNLOADED rows first, then PENDING sorted by priority score descending.
                        int aRank = ("DOWNLOADED".equals(a.getStatus()) || "PROCESSED".equals(a.getStatus())) ? 0 : 1;
                        int bRank = ("DOWNLOADED".equals(b.getStatus()) || "PROCESSED".equals(b.getStatus())) ? 0 : 1;
                        if (aRank != bRank) return aRank - bRank;
                        return Integer.compare(b.getPriorityScore(), a.getPriorityScore());
                    })
                    .collect(Collectors.toList());
                table.setItems(FXCollections.observableArrayList(rows));
            }

            @Override
            protected void failed() {
                showError("Failed to load requests", getException().getMessage());
            }
        };
        new Thread(task).start();
    }

    // ── View details ──────────────────────────────────────────────────────────

    private static void handleViewDetails(TableView<RequestRow> table) {
        RequestRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("View Details", "Please select a request to view details"); return; }

        try {
            Optional<BookRequest> reqOpt = BookRequestDao.findById(selected.getId());
            if (reqOpt.isEmpty()) { showError("Request not found", "The selected request could not be found"); return; }
            showRequestDetailsDialog(reqOpt.get());
        } catch (SQLException e) {
            showError("Database Error", e.getMessage());
        }
    }

    private static void showRequestDetailsDialog(BookRequest req) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Book Request Details");
        dialog.setHeaderText("Request #" + req.getId() + "  —  " + req.getStatus().name());

        GridPane content = new GridPane();
        content.setHgap(10); content.setVgap(10); content.setPadding(new Insets(20));

        content.add(new Label("Title:"),        0, 0); content.add(new Label(req.getTitle()),      1, 0);
        content.add(new Label("Author:"),       0, 1); content.add(new Label(req.getAuthorName()), 1, 1);
        content.add(new Label("Genre:"),        0, 2); content.add(new Label(req.getGenre() != null ? req.getGenre() : "N/A"), 1, 2);
        content.add(new Label("Requested By:"), 0, 3); content.add(new Label(req.getRequestedByName()), 1, 3);
        content.add(new Label("Status:"),       0, 4); content.add(new Label(req.getStatus().name()), 1, 4);
        content.add(new Label("Created:"),      0, 5); content.add(new Label(req.getCreatedAt().substring(0, 10)), 1, 5);

        TextArea descArea = new TextArea(req.getDescription() != null ? req.getDescription() : "No description");
        descArea.setWrapText(true); descArea.setPrefRowCount(4); descArea.setEditable(false);
        content.add(new Label("Description:"), 0, 6); content.add(descArea, 1, 6);

        if (req.getDownloadedFilePath() != null && !req.getDownloadedFilePath().isEmpty()) {
            content.add(new Label("File:"), 0, 7);
            content.add(new Label(req.getDownloadedFilePath()), 1, 7);
        }

        if (req.getGeneratedSummary() != null && !req.getGeneratedSummary().isEmpty()) {
            TextArea summaryArea = new TextArea(req.getGeneratedSummary());
            summaryArea.setWrapText(true); summaryArea.setPrefRowCount(4); summaryArea.setEditable(false);
            content.add(new Label("Generated Summary:"), 0, 8); content.add(summaryArea, 1, 8);
        }

        if (req.getApprovalNotes() != null && !req.getApprovalNotes().isEmpty()) {
            TextArea notesArea = new TextArea(req.getApprovalNotes());
            notesArea.setWrapText(true); notesArea.setPrefRowCount(3); notesArea.setEditable(false);
            content.add(new Label("Notes:"), 0, 9); content.add(notesArea, 1, 9);
        }

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    // ── Download book — phase 1: search ───────────────────────────────────────

    /**
     * Phase 1: searches online for candidates (no download yet).
     * On success, opens {@link #showSelectionDialog} so the librarian picks one.
     */
    private static void handleDownloadBook(TableView<RequestRow> table) {
        RequestRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Download Book", "Please select a request to download"); return; }

        if ("DOWNLOADED".equals(selected.getStatus()) || "PROCESSED".equals(selected.getStatus())) {
            showInfo("Already Downloaded",
                    "This book has already been downloaded.\n"
                    + "Select it and click \"Approve & Publish\" to add it to the catalog.");
            return;
        }

        try {
            Optional<BookRequest> reqOpt = BookRequestDao.findById(selected.getId());
            if (reqOpt.isEmpty()) { showError("Request not found", "The selected request could not be found"); return; }
            BookRequest req = reqOpt.get();

            // Show a non-blocking progress stage while searching online.
            Stage searchStage = new Stage();
            searchStage.initModality(Modality.APPLICATION_MODAL);
            searchStage.setTitle("Searching…");

            ProgressBar searchBar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
            searchBar.setPrefWidth(260);
            Label searchMsg = new Label("Searching for \"" + req.getTitle() + "\"…");
            VBox searchBox = new VBox(8, searchBar, searchMsg);
            searchBox.setPadding(new Insets(24));
            searchBox.setAlignment(Pos.CENTER);
            searchStage.setScene(new javafx.scene.Scene(searchBox, 320, 120));
            searchStage.show();

            Task<BookDownloaderService.SearchResults> searchTask = new Task<>() {
                @Override
                protected BookDownloaderService.SearchResults call() {
                    return BookDownloaderService.searchForCandidates(req.getTitle(), req.getAuthorName());
                }

                @Override
                protected void succeeded() {
                    searchStage.close();
                    BookDownloaderService.SearchResults results = getValue();
                    if (results.isEmpty()) {
                        showError("No Results Found",
                                "Could not find any public-domain edition of \"" + req.getTitle()
                                + "\" online.\n\nOnly public-domain books (Project Gutenberg) "
                                + "are available for automatic download.");
                        return;
                    }
                    showSelectionDialog(table, req, results);
                }

                @Override
                protected void failed() {
                    searchStage.close();
                    showError("Search Error", getException().getMessage());
                }
            };
            new Thread(searchTask).start();

        } catch (SQLException e) {
            showError("Database Error", e.getMessage());
        }
    }

    // ── Download book — phase 2: select ───────────────────────────────────────

    /**
     * Shows a dialog listing the top search result and alternatives.
     * The librarian clicks a card to select a book, then clicks Download.
     */
    private static void showSelectionDialog(TableView<RequestRow> table, BookRequest req,
                                             BookDownloaderService.SearchResults results) {
        ObjectProperty<BookDownloaderService.BookCandidate> selectedCandidate =
                new SimpleObjectProperty<>();

        Dialog<BookDownloaderService.BookCandidate> dialog = new Dialog<>();
        dialog.setTitle("Select Book to Download");
        dialog.setHeaderText("Request: \"" + req.getTitle() + "\" by " + req.getAuthorName()
                + "\nClick a book to select it, then click Download.");

        VBox body = new VBox(10);
        body.setPadding(new Insets(4));

        // ── Top search result section ─────────────────────────────────────────
        if (results.topResult() != null) {
            Label topLbl = new Label("Top search result:");
            topLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #1565c0; -fx-font-size: 12;");
            body.getChildren().add(topLbl);
            body.getChildren().add(makeCandidateCard(results.topResult(), selectedCandidate));
        }

        // ── Alternatives section ──────────────────────────────────────────────
        if (!results.alternatives().isEmpty()) {
            if (results.topResult() != null) body.getChildren().add(new Separator());
            Label altLbl = new Label("Alternatives found:");
            altLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #1565c0; -fx-font-size: 12;");
            body.getChildren().add(altLbl);
            for (BookDownloaderService.BookCandidate c : results.alternatives()) {
                body.getChildren().add(makeCandidateCard(c, selectedCandidate));
            }
        }

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefHeight(320);

        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setPrefWidth(480);

        ButtonType downloadBtnType = new ButtonType("Download", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(downloadBtnType, ButtonType.CANCEL);

        Node downloadBtn = dialog.getDialogPane().lookupButton(downloadBtnType);
        downloadBtn.setDisable(true);
        selectedCandidate.addListener((obs, old, sel) -> downloadBtn.setDisable(sel == null));

        dialog.setResultConverter(bt -> bt == downloadBtnType ? selectedCandidate.get() : null);
        dialog.showAndWait().ifPresent(candidate -> doDownloadCandidate(table, req, candidate));
    }

    /** Builds a clickable card for one candidate that highlights when selected. */
    private static VBox makeCandidateCard(BookDownloaderService.BookCandidate c,
                                           ObjectProperty<BookDownloaderService.BookCandidate> selected) {
        Label titleLbl = new Label(c.title());
        titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");
        titleLbl.setWrapText(true);
        Label authorLbl = new Label("by " + c.author());
        authorLbl.setStyle("-fx-text-fill: #555; -fx-font-size: 11;");

        VBox card = new VBox(3, titleLbl, authorLbl);
        card.setPadding(new Insets(10, 14, 10, 14));
        card.setStyle(CARD_NORMAL);
        card.setMaxWidth(Double.MAX_VALUE);

        // Reflect selection state via border/background change.
        selected.addListener((obs, old, newVal) ->
            card.setStyle(c.equals(newVal) ? CARD_SELECTED : CARD_NORMAL));

        card.setOnMouseClicked(e -> selected.set(c));
        card.setCursor(javafx.scene.Cursor.HAND);
        return card;
    }

    private static final String CARD_NORMAL   =
            "-fx-background-color: #fafafa; -fx-border-color: #ddd; "
            + "-fx-border-radius: 6; -fx-background-radius: 6;";
    private static final String CARD_SELECTED =
            "-fx-background-color: #e3f2fd; -fx-border-color: #1565c0; "
            + "-fx-border-width: 2; -fx-border-radius: 6; -fx-background-radius: 6;";

    // ── Download book — phase 3: download ─────────────────────────────────────

    /** Downloads the chosen candidate in the background, then saves to DB. */
    private static void doDownloadCandidate(TableView<RequestRow> table, BookRequest req,
                                             BookDownloaderService.BookCandidate candidate) {
        Stage progressStage = new Stage();
        progressStage.initModality(Modality.APPLICATION_MODAL);
        progressStage.setTitle("Downloading Book");

        ProgressBar progressBar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.setPrefWidth(260);
        Label progressPctLabel = new Label("");
        Label statusLabel = new Label("Downloading \"" + candidate.title() + "\"…");

        progressBar.progressProperty().addListener((obs, o, n) -> {
            double v = n.doubleValue();
            progressPctLabel.setText(v < 0 ? "" : String.format("%.0f%%", v * 100));
        });

        VBox box = new VBox(8, progressBar, progressPctLabel, statusLabel);
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.CENTER);
        progressStage.setScene(new javafx.scene.Scene(box, 320, 160));
        progressStage.show();

        Task<DownloadOutcome> task = new Task<>() {
            @Override
            protected DownloadOutcome call() {
                updateProgress(-1, 1);
                updateMessage("Downloading \"" + candidate.title() + "\"…");
                BookDownloaderService.DownloadResult dlResult =
                        BookDownloaderService.downloadCandidate(candidate);
                if (!dlResult.success) return new DownloadOutcome(dlResult, null);
                updateProgress(0.7, 1.0);
                String summary = null;
                try {
                    updateMessage("Generating summary…");
                    BookSummaryService svc = new BookSummaryService();
                    BookSummaryService.SummaryResult sr =
                            svc.generateSummaryFromBookFile(dlResult.filePath);
                    if (sr.success()) summary = sr.summary();
                } catch (Exception ignored) {}
                updateProgress(1.0, 1.0);
                return new DownloadOutcome(dlResult, summary);
            }

            @Override
            protected void succeeded() {
                progressStage.close();
                DownloadOutcome outcome = getValue();
                if (!outcome.downloadResult.success) {
                    showError("Download Failed", outcome.downloadResult.errorMessage);
                    return;
                }
                try {
                    // If the candidate title is sufficiently close to the requested title,
                    // keep the original request fields; otherwise record the alternative title/author.
                    boolean titleMatches =
                        candidate.title().toLowerCase().contains(req.getTitle().toLowerCase())
                        || req.getTitle().toLowerCase().contains(candidate.title().toLowerCase());
                    if (titleMatches) {
                        BookRequestDao.markAsDownloaded(req.getId(),
                                outcome.downloadResult.filePath, outcome.generatedSummary);
                    } else {
                        BookRequestDao.markAsDownloadedAlternative(req.getId(),
                                candidate.title(), candidate.author(),
                                outcome.downloadResult.filePath, outcome.generatedSummary);
                    }
                } catch (SQLException e) {
                    showError("Database Error", e.getMessage());
                }

                String msg = "\"" + candidate.title() + "\" downloaded successfully!"
                        + "\nFile: " + outcome.downloadResult.fileName
                        + "\n\nThe row is now green. Click \"Approve & Publish\" to publish it.";
                if (outcome.generatedSummary != null) msg += "\n\nA summary was generated automatically.";
                showInfo("Download Complete", msg);

                createNotificationForRequester(req, "Book Downloaded",
                        "Your requested book '" + req.getTitle()
                        + "' has been located. The librarian will review and publish it soon.");
                loadRequests(table, "");
            }

            @Override
            protected void failed() {
                progressStage.close();
                showError("Download Error", getException().getMessage());
            }
        };
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());
        new Thread(task).start();
    }

    // ── Approve & publish ─────────────────────────────────────────────────────

    private static void handleApproveRequest(TableView<RequestRow> table) {
        RequestRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Approve Request", "Please select a request to approve"); return; }

        if (!"DOWNLOADED".equals(selected.getStatus()) && !"PROCESSED".equals(selected.getStatus())) {
            showInfo("Download Required",
                    "Please download the book first (click \"Download Book\").\n"
                    + "After a successful download the row turns green and you can approve it.");
            return;
        }

        try {
            Optional<BookRequest> reqOpt = BookRequestDao.findById(selected.getId());
            if (reqOpt.isEmpty()) { showError("Request not found", "The selected request could not be found"); return; }

            BookRequest req = reqOpt.get();

            if (req.getDownloadedFilePath() == null || req.getDownloadedFilePath().isEmpty()) {
                showInfo("Download Required",
                        "No downloaded file is associated with this request. "
                        + "Please use \"Download Book\" first.");
                return;
            }

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Approve & Publish");
            dialog.setHeaderText("Publishing \"" + req.getTitle() + "\" to the catalog");

            TextArea notesArea = new TextArea();
            notesArea.setPromptText("Optional notes (shown in the request record)");
            notesArea.setWrapText(true);
            notesArea.setPrefRowCount(4);

            Label hint = new Label(
                    "The book will be added to the available-books catalog immediately.\n"
                    + "Author displayed: " + req.getAuthorName()
                    + (req.getGenre() != null ? "  |  Genre: " + req.getGenre() : ""));
            hint.setWrapText(true);
            hint.setStyle("-fx-font-size: 11; -fx-text-fill: #555;");

            VBox content = new VBox(8, hint, notesArea);
            content.setPadding(new Insets(4));
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            Optional<ButtonType> result = dialog.showAndWait();
            if (result.filter(bt -> bt == ButtonType.OK).isEmpty()) return;

            String notes = notesArea.getText().trim();
            User librarian = (User) table.getScene().getUserData();

            String summary = req.getGeneratedSummary() != null && !req.getGeneratedSummary().isEmpty()
                    ? req.getGeneratedSummary()
                    : (req.getDescription() != null ? req.getDescription() : "");
            String genre = req.getGenre() != null && !req.getGenre().isEmpty()
                    ? req.getGenre() : "General";

            BookDao.insert(req.getTitle(), librarian.getId(), req.getAuthorName(),
                    genre, summary, req.getDownloadedFilePath(), LocalDate.now().toString());

            BookRequestDao.approve(req.getId(), notes);

            createNotificationForRequester(req, "Book Request Approved & Published",
                    "Your request for \"" + req.getTitle()
                    + "\" has been approved! The book is now available in the catalog for borrowing.");

            showInfo("Published",
                    "\"" + req.getTitle() + "\" by " + req.getAuthorName()
                    + " is now available in the student/staff catalog.");
            loadRequests(table, "");

        } catch (SQLException e) {
            showError("Database Error", e.getMessage());
        }
    }

    // ── Reject ────────────────────────────────────────────────────────────────

    private static void handleRejectRequest(TableView<RequestRow> table) {
        RequestRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { showInfo("Reject Request", "Please select a request to reject"); return; }

        try {
            Optional<BookRequest> reqOpt = BookRequestDao.findById(selected.getId());
            if (reqOpt.isEmpty()) { showError("Request not found", "The selected request could not be found"); return; }

            BookRequest req = reqOpt.get();

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Reject Request");
            dialog.setHeaderText("Rejecting: " + req.getTitle());

            TextArea reasonArea = new TextArea();
            reasonArea.setPromptText("Please provide a reason for rejection…");
            reasonArea.setWrapText(true);
            reasonArea.setPrefRowCount(5);

            dialog.getDialogPane().setContent(reasonArea);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            Optional<ButtonType> result = dialog.showAndWait();
            if (result.filter(bt -> bt == ButtonType.OK).isEmpty()) return;

            String reason = reasonArea.getText().trim();
            if (reason.isEmpty()) { showInfo("Reason Required", "Please provide a rejection reason."); return; }

            BookRequestDao.reject(req.getId(), reason);

            createNotificationForRequester(req, "Book Request Rejected",
                    "Your request for \"" + req.getTitle()
                    + "\" has been rejected. Reason: " + reason);

            showInfo("Rejected", "Request rejected successfully.");
            loadRequests(table, "");

        } catch (SQLException e) {
            showError("Database Error", e.getMessage());
        }
    }

    // ── Analytics dialog (all requested books) ────────────────────────────────

    private static void showAnalyticsDialog() {
        List<BookRequest> all;
        try {
            all = BookRequestDao.findAll();
        } catch (SQLException e) {
            showError("Analytics", "Failed to load data: " + e.getMessage());
            return;
        }

        long total      = all.size();
        long pending    = all.stream().filter(r -> r.getStatus() == RequestStatus.PENDING).count();
        long downloaded = all.stream().filter(r -> r.getStatus() == RequestStatus.DOWNLOADED).count();
        long approved   = all.stream().filter(r -> r.getStatus() == RequestStatus.APPROVED).count();
        long rejected   = all.stream().filter(r -> r.getStatus() == RequestStatus.REJECTED).count();

        Map<String, Long> genreCounts = all.stream()
            .filter(r -> r.getGenre() != null && !r.getGenre().isBlank())
            .flatMap(r -> Arrays.stream(r.getGenre().split(",\\s*")))
            .collect(Collectors.groupingBy(String::trim, Collectors.counting()));

        Map<String, Long> authorCounts = all.stream()
            .filter(r -> r.getAuthorName() != null && !r.getAuthorName().isBlank())
            .collect(Collectors.groupingBy(r -> r.getAuthorName().trim(), Collectors.counting()));

        Map<String, Long> titleCounts = all.stream()
            .filter(r -> r.getTitle() != null && !r.getTitle().isBlank())
            .collect(Collectors.groupingBy(r -> r.getTitle().trim(), Collectors.counting()));

        Map<String, Long> statusCounts = all.stream()
            .collect(Collectors.groupingBy(r -> r.getStatus().name(), Collectors.counting()));

        // KPI row
        HBox kpiRow = new HBox(12,
            statChip("Total",      String.valueOf(total)),
            statChip("Pending",    String.valueOf(pending)),
            statChip("Downloaded", String.valueOf(downloaded)),
            statChip("Approved",   String.valueOf(approved)),
            statChip("Rejected",   String.valueOf(rejected))
        );
        kpiRow.setAlignment(Pos.CENTER);

        // Row 1: Status pie + Genre bar
        PieChart statusPie = new PieChart();
        statusPie.setTitle("Status Distribution");
        statusCounts.forEach((s, c) -> statusPie.getData().add(new PieChart.Data(s, c)));
        statusPie.setLegendVisible(true);
        statusPie.setPrefSize(360, 270);

        BarChart<String, Number> genreChart = makeBarChart("Most Requested Genres", genreCounts, 8);
        genreChart.setPrefSize(380, 270);

        HBox row1 = new HBox(16, statusPie, genreChart);
        row1.setAlignment(Pos.CENTER);

        // Row 2: Author bar + Top titles bar
        BarChart<String, Number> authorChart = makeBarChart("Most Requested Authors", authorCounts, 8);
        authorChart.setPrefSize(370, 270);

        BarChart<String, Number> titleChart = makeBarChart("Most Requested Titles", titleCounts, 8);
        titleChart.setPrefSize(370, 270);

        HBox row2 = new HBox(16, authorChart, titleChart);
        row2.setAlignment(Pos.CENTER);

        VBox content = new VBox(14, kpiRow, new Separator(), row1, new Separator(), row2);
        content.setPadding(new Insets(16));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Analytics — All Requested Books");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(820, 640);
        dialog.showAndWait();
    }

    private static BarChart<String, Number> makeBarChart(String title, Map<String, Long> data, int limit) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Count");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setLegendVisible(false);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        data.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(limit)
            .forEach(e -> series.getData().add(new XYChart.Data<>(e.getKey(), e.getValue())));
        chart.getData().add(series);
        return chart;
    }

    private static VBox statChip(String label, String value) {
        Label valLbl = new Label(value);
        valLbl.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: #1565c0;");
        Label keyLbl = new Label(label);
        keyLbl.setStyle("-fx-font-size: 11; -fx-text-fill: #555;");
        VBox chip = new VBox(2, valLbl, keyLbl);
        chip.setAlignment(Pos.CENTER);
        chip.setPadding(new Insets(10, 20, 10, 20));
        chip.setStyle("-fx-background-color: #f5f9ff; -fx-background-radius: 8; "
                    + "-fx-border-color: #c5cae9; -fx-border-radius: 8;");
        chip.setMinWidth(110);
        return chip;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void createNotificationForRequester(BookRequest req, String title, String message) {
        try {
            org.example.service.NotificationService.notifyBookRequestUpdate(
                    req.getRequestedByUserId(), title, message);
        } catch (SQLException ignored) {}
    }

    private static void showInfo(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(message); a.showAndWait();
    }

    private static void showError(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(message); a.showAndWait();
    }

    private static final class DownloadOutcome {
        final BookDownloaderService.DownloadResult downloadResult;
        final String generatedSummary;
        DownloadOutcome(BookDownloaderService.DownloadResult dr, String gs) {
            this.downloadResult   = dr;
            this.generatedSummary = gs;
        }
    }
}
