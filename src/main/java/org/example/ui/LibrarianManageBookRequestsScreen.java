package org.example.ui;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.example.app.Navigator;
import org.example.db.BookRequestDao;
import org.example.domain.BookRequest;
import org.example.domain.BookRequest.RequestStatus;
import org.example.domain.User;
import org.example.service.BookDownloaderService;
import org.example.service.BookSummaryService;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
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
 * Features:
 * - View all pending book requests
 * - Approve or reject requests
 * - Download books from online sources
 * - Generate summaries using LLM
 * - Search and filter requests
 */
public final class LibrarianManageBookRequestsScreen {

    private LibrarianManageBookRequestsScreen() {}

    public static final class RequestRow {
        private final long id;
        private final String title;
        private final String author;
        private final String requestedBy;
        private final String status;
        private final String createdAt;

        public RequestRow(BookRequest req) {
            this.id = req.getId();
            this.title = req.getTitle();
            this.author = req.getAuthorName();
            this.requestedBy = req.getRequestedByName();
            this.status = req.getStatus().name();
            this.createdAt = req.getCreatedAt().substring(0, 10); // Date only
        }

        public long getId() { return id; }
        public String getTitle() { return title; }
        public String getAuthor() { return author; }
        public String getRequestedBy() { return requestedBy; }
        public String getStatus() { return status; }
        public String getCreatedAt() { return createdAt; }
    }

    private static String currentSearchTerm = "";

    public static Scene create(Navigator navigator, User librarian) {
        Label titleLbl = new Label("Manage Book Requests");
        titleLbl.getStyleClass().add("screen-title");

        Label infoLbl = new Label("Manage book requests from students and staff");
        infoLbl.getStyleClass().add("info-label");

        // Search/Filter panel
        HBox searchBox = createSearchBox();

        // Table for displaying requests
        TableView<RequestRow> table = new TableView<>();
        configureRequestTable(table);

        // Load initial data
        loadRequestsIntoTable(table, RequestStatus.PENDING);

        // Buttons for actions
        Button approveBtn = new Button("Approve");
        approveBtn.getStyleClass().add("primary-button");
        approveBtn.setOnAction(e -> handleApproveRequest(table, librarian));

        Button rejectBtn = new Button("Reject");
        rejectBtn.getStyleClass().add("danger-button");
        rejectBtn.setOnAction(e -> handleRejectRequest(table, librarian));

        Button downloadBtn = new Button("Download Book");
        downloadBtn.getStyleClass().add("secondary-button");
        downloadBtn.setOnAction(e -> handleDownloadBook(table, librarian));

        Button viewDetailsBtn = new Button("View Details");
        viewDetailsBtn.getStyleClass().add("secondary-button");
        viewDetailsBtn.setOnAction(e -> handleViewDetails(table, librarian));

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> loadRequestsIntoTable(table, RequestStatus.PENDING));

        HBox buttonBox = new HBox(10, viewDetailsBtn, downloadBtn, approveBtn, rejectBtn, refreshBtn);
        buttonBox.setPadding(new Insets(10));
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        VBox headerBox = new VBox(8, titleLbl, infoLbl, searchBox);
        headerBox.setPadding(new Insets(20));
        headerBox.setStyle("-fx-border-color: #f0f0f0; -fx-border-width: 0 0 1 0;");

        ScrollPane tableScroll = new ScrollPane(table);
        tableScroll.setFitToWidth(true);

        BorderPane root = new BorderPane();
        root.setTop(headerBox);
        root.setCenter(new VBox(10, tableScroll, buttonBox));
        VBox.setVgrow(tableScroll, Priority.ALWAYS);
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        java.net.URL cssResource = LibrarianManageBookRequestsScreen.class.getResource("/app.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }
        scene.setUserData(librarian);

        return scene;
    }

    private static HBox createSearchBox() {
        HBox box = new HBox(10);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-border-color: #f5f5f5; -fx-border-width: 0 0 1 0;");

        Label searchLbl = new Label("Search:");
        TextField searchField = new TextField();
        searchField.setPromptText("Search by title, author, or requester...");
        searchField.setPrefWidth(300);

        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().add("secondary-button");
        searchBtn.setOnAction(e -> {
            currentSearchTerm = searchField.getText();
            // Search will be implemented when table is accessed
        });

        box.getChildren().addAll(searchLbl, searchField, searchBtn);
        return box;
    }

    private static void configureRequestTable(TableView<RequestRow> table) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

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
        statusCol.setPrefWidth(100);

        TableColumn<RequestRow, String> dateCol = new TableColumn<>("Request Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        dateCol.setPrefWidth(100);

        table.getColumns().addAll(titleCol, authorCol, requestorCol, statusCol, dateCol);
    }

    private static void loadRequestsIntoTable(TableView<RequestRow> table, RequestStatus status) {
        Task<List<BookRequest>> task = new Task<List<BookRequest>>() {
            @Override
            protected List<BookRequest> call() throws Exception {
                return BookRequestDao.findByStatus(status);
            }

            @Override
            protected void succeeded() {
                List<BookRequest> requests = getValue();
                table.getItems().clear();
                for (BookRequest req : requests) {
                    table.getItems().add(new RequestRow(req));
                }
            }

            @Override
            protected void failed() {
                showError("Failed to load requests", getException().getMessage());
            }
        };

        new Thread(task).start();
    }

    private static void handleViewDetails(TableView<RequestRow> table, User librarian) {
        RequestRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("Please select a request to view details", "");
            return;
        }

        try {
            Optional<BookRequest> reqOpt = BookRequestDao.findById(selected.getId());
            if (reqOpt.isEmpty()) {
                showError("Request not found", "The selected request could not be found in the database");
                return;
            }

            BookRequest req = reqOpt.get();
            showRequestDetailsDialog(req);

        } catch (SQLException e) {
            showError("Database Error", e.getMessage());
        }
    }

    private static void showRequestDetailsDialog(BookRequest req) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Book Request Details");
        dialog.setHeaderText("Request ID: #" + req.getId());

        GridPane content = new GridPane();
        content.setHgap(10);
        content.setVgap(10);
        content.setPadding(new Insets(20));

        // Details
        content.add(new Label("Title:"), 0, 0);
        content.add(new Label(req.getTitle()), 1, 0);

        content.add(new Label("Author:"), 0, 1);
        content.add(new Label(req.getAuthorName()), 1, 1);

        content.add(new Label("Genre:"), 0, 2);
        content.add(new Label(req.getGenre() != null ? req.getGenre() : "N/A"), 1, 2);

        content.add(new Label("Requested By:"), 0, 3);
        content.add(new Label(req.getRequestedByName()), 1, 3);

        content.add(new Label("Status:"), 0, 4);
        content.add(new Label(req.getStatus().name()), 1, 4);

        content.add(new Label("Created:"), 0, 5);
        content.add(new Label(req.getCreatedAt().substring(0, 10)), 1, 5);

        TextArea descArea = new TextArea();
        descArea.setText(req.getDescription() != null ? req.getDescription() : "No description provided");
        descArea.setWrapText(true);
        descArea.setPrefRowCount(4);
        descArea.setEditable(false);
        content.add(new Label("Description:"), 0, 6);
        content.add(descArea, 1, 6);

        if (req.getApprovalNotes() != null && !req.getApprovalNotes().isEmpty()) {
            TextArea notesArea = new TextArea();
            notesArea.setText(req.getApprovalNotes());
            notesArea.setWrapText(true);
            notesArea.setPrefRowCount(3);
            notesArea.setEditable(false);
            content.add(new Label("Notes:"), 0, 7);
            content.add(notesArea, 1, 7);
        }

        if (req.getGeneratedSummary() != null && !req.getGeneratedSummary().isEmpty()) {
            TextArea summaryArea = new TextArea();
            summaryArea.setText(req.getGeneratedSummary());
            summaryArea.setWrapText(true);
            summaryArea.setPrefRowCount(4);
            summaryArea.setEditable(false);
            content.add(new Label("Generated Summary:"), 0, 8);
            content.add(summaryArea, 1, 8);
        }

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private static void handleApproveRequest(TableView<RequestRow> table, User librarian) {
        RequestRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("Please select a request to approve", "");
            return;
        }

        try {
            Optional<BookRequest> reqOpt = BookRequestDao.findById(selected.getId());
            if (reqOpt.isEmpty()) {
                showError("Request not found", "The selected request could not be found");
                return;
            }

            BookRequest req = reqOpt.get();

            // Show dialog to add approval notes
            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("Approve Request");
            dialog.setHeaderText("Approving: " + req.getTitle());

            TextArea notesArea = new TextArea();
            notesArea.setPromptText("Optional: Add notes about approval (e.g., action plan, timeline)");
            notesArea.setWrapText(true);
            notesArea.setPrefRowCount(5);

            dialog.getDialogPane().setContent(notesArea);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                String notes = notesArea.getText().trim();
                BookRequestDao.approve(req.getId(), notes);
                
                // Create notification for user
                createNotificationForRequester(req, "Book Request Approved", 
                    "Your request for '" + req.getTitle() + "' has been approved by the librarian.");
                
                showInfo("Success", "Request approved successfully!");
                loadRequestsIntoTable(table, RequestStatus.PENDING);
            }

        } catch (SQLException e) {
            showError("Database Error", e.getMessage());
        }
    }

    private static void handleRejectRequest(TableView<RequestRow> table, User librarian) {
        RequestRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("Please select a request to reject", "");
            return;
        }

        try {
            Optional<BookRequest> reqOpt = BookRequestDao.findById(selected.getId());
            if (reqOpt.isEmpty()) {
                showError("Request not found", "The selected request could not be found");
                return;
            }

            BookRequest req = reqOpt.get();

            // Show dialog to add rejection reason
            Dialog<String> dialog = new Dialog<>();
            dialog.setTitle("Reject Request");
            dialog.setHeaderText("Rejecting: " + req.getTitle());

            TextArea reasonArea = new TextArea();
            reasonArea.setPromptText("Please provide a reason for rejection...");
            reasonArea.setWrapText(true);
            reasonArea.setPrefRowCount(5);

            dialog.getDialogPane().setContent(reasonArea);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                String reason = reasonArea.getText().trim();
                if (reason.isEmpty()) {
                    showInfo("Please provide a rejection reason", "");
                    return;
                }
                
                BookRequestDao.reject(req.getId(), reason);
                
                // Create notification for user
                createNotificationForRequester(req, "Book Request Rejected", 
                    "Your request for '" + req.getTitle() + "' has been rejected. Reason: " + reason);
                
                showInfo("Success", "Request rejected successfully!");
                loadRequestsIntoTable(table, RequestStatus.PENDING);
            }

        } catch (SQLException e) {
            showError("Database Error", e.getMessage());
        }
    }

    private static void handleDownloadBook(TableView<RequestRow> table, User librarian) {
        RequestRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("Please select a request to download", "");
            return;
        }

        try {
            Optional<BookRequest> reqOpt = BookRequestDao.findById(selected.getId());
            if (reqOpt.isEmpty()) {
                showError("Request not found", "The selected request could not be found");
                return;
            }

            BookRequest req = reqOpt.get();

            // Show progress dialog
            Stage progressStage = new Stage();
            progressStage.initModality(Modality.APPLICATION_MODAL);
            progressStage.setTitle("Downloading Book");

            ProgressIndicator progressIndicator = new ProgressIndicator();
            Label statusLabel = new Label("Searching for book online...");

            VBox progressBox = new VBox(10);
            progressBox.setPadding(new Insets(20));
            progressBox.setAlignment(Pos.CENTER);
            progressBox.getChildren().addAll(progressIndicator, statusLabel);

            Scene progressScene = new Scene(progressBox, 300, 150);
            progressStage.setScene(progressScene);
            progressStage.show();

            // Download in background
            Task<BookDownloaderService.DownloadResult> downloadTask = new Task<BookDownloaderService.DownloadResult>() {
                @Override
                protected BookDownloaderService.DownloadResult call() {
                    updateMessage("Searching for book: " + req.getTitle());
                    return BookDownloaderService.downloadBook(req.getTitle(), req.getAuthorName());
                }

                @Override
                protected void succeeded() {
                    progressStage.close();
                    BookDownloaderService.DownloadResult result = getValue();

                    if (result.success) {
                        // Book downloaded successfully, now generate summary if needed
                        if (req.getDescription() == null || req.getDescription().isEmpty()) {
                            generateAndStoreSummary(req, result.filePath);
                        }

                        try {
                            BookRequestDao.markAsProcessed(req.getId(), result.filePath, req.getGeneratedSummary());
                        } catch (SQLException e) {
                            showError("Database Error", e.getMessage());
                        }
                        
                        showInfo("Success", "Book downloaded successfully!\nFile: " + result.fileName);
                        createNotificationForRequester(req, "Book Downloaded",
                            "Your requested book '" + req.getTitle() + "' has been downloaded and added to the library.");
                    } else {
                        showError("Download Failed", result.errorMessage);
                    }
                }

                @Override
                protected void failed() {
                    progressStage.close();
                    showError("Download Error", getException().getMessage());
                }
            };

            new Thread(downloadTask).start();

        } catch (SQLException e) {
            showError("Database Error", e.getMessage());
        }
    }

    private static void generateAndStoreSummary(BookRequest req, String filePath) {
        try {
            BookSummaryService summaryService = new BookSummaryService();
            BookSummaryService.SummaryResult result = summaryService.generateSummaryFromBookFile(filePath);
            
            if (result.success()) {
                req.setGeneratedSummary(result.summary());
            }
        } catch (Exception e) {
            System.err.println("Failed to generate summary: " + e.getMessage());
        }
    }

    private static void createNotificationForRequester(BookRequest req, String title, String message) {
        try {
            org.example.service.NotificationService.notifyBookRequestUpdate(req.getRequestedByUserId(), title, message);
        } catch (Exception ignored) {
        }
    }

    private static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
