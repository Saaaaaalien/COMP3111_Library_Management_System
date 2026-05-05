package org.example.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.example.app.Navigator;
import org.example.db.ReadingHistoryDao;
import org.example.db.ReadingHistoryDao.ReadingHistoryRow;
import org.example.domain.User;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Task 1.8 reading history, filters, exports, charts, badges, and reader deep-link.
 */
public final class ReadingHistoryScreen {

    private ReadingHistoryScreen() {}

    public static Scene create(Navigator navigator, User user) {
        Label title = new Label("Reading History");
        title.getStyleClass().add("screen-title");
        Label subtitle = new Label(
                "Your loans, tracked reading time, and saved bookmark page for each borrow.");
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("login-hint");

        ObservableList<HistoryRow> all = FXCollections.observableArrayList();
        FilteredList<HistoryRow> filtered = new FilteredList<>(all, x -> true);
        TableView<HistoryRow> table = new TableView<>(filtered);

        TableColumn<HistoryRow, String> colTitle = new TableColumn<>("Title");
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colTitle.setPrefWidth(140);
        TableColumn<HistoryRow, String> colAuthor = new TableColumn<>("Author");
        colAuthor.setCellValueFactory(new PropertyValueFactory<>("author"));
        TableColumn<HistoryRow, String> colGenre = new TableColumn<>("Genre");
        colGenre.setCellValueFactory(new PropertyValueFactory<>("genre"));
        colGenre.setPrefWidth(90);
        TableColumn<HistoryRow, String> colBorrowed = new TableColumn<>("Borrowed");
        colBorrowed.setCellValueFactory(new PropertyValueFactory<>("borrowedDisplay"));
        TableColumn<HistoryRow, String> colReturned = new TableColumn<>("Returned");
        colReturned.setCellValueFactory(new PropertyValueFactory<>("returnedDisplay"));
        TableColumn<HistoryRow, String> colRead = new TableColumn<>("Reading time");
        colRead.setCellValueFactory(new PropertyValueFactory<>("readTimeDisplay"));
        TableColumn<HistoryRow, String> colProg = new TableColumn<>("Bookmark page");
        colProg.setCellValueFactory(new PropertyValueFactory<>("progressDisplay"));
        TableColumn<HistoryRow, Void> colOpen = new TableColumn<>("Continue");
        colOpen.setPrefWidth(150);
        colOpen.setCellFactory(tc -> new TableCell<>() {
            private final Button btn = new Button();
            {
                btn.getStyleClass().add("secondary-button");
                Tooltip tip = new Tooltip(
                        "Opens the PDF reader at your saved bookmark (active PDF loans only).");
                btn.setTooltip(tip);
                btn.setOnAction(ev -> {
                    TableRow<HistoryRow> tr = getTableRow();
                    if (tr == null) {
                        return;
                    }
                    HistoryRow row = tr.getItem();
                    if (row == null || !row.canOpenReader()) {
                        return;
                    }
                    navigator.showPdfReader(user, row.getBorrowId(), row.getBookId(), row.getLastPage0(), null);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                HistoryRow row = getTableRow().getItem();
                btn.setDisable(!row.canOpenReader());
                if (!row.canOpenReader()) {
                    btn.setText("—");
                } else if (row.getLastPage0() != null) {
                    btn.setText("Continue @ page " + (row.getLastPage0() + 1));
                } else {
                    btn.setText("Continue reading");
                }
                setGraphic(btn);
            }
        });

        table.getColumns().addAll(List.of(colTitle, colAuthor, colGenre, colBorrowed, colReturned, colRead, colProg, colOpen));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TextField search = new TextField();
        search.setPromptText("Search title or author...");
        ComboBox<String> statusFilter = new ComboBox<>(FXCollections.observableArrayList("All", "Active", "Returned"));
        statusFilter.getSelectionModel().selectFirst();
        ComboBox<String> genreFilter = new ComboBox<>(FXCollections.observableArrayList("All genres"));
        genreFilter.getSelectionModel().selectFirst();

        Label lifetimeHeading = new Label("Lifetime achievements");
        lifetimeHeading.getStyleClass().add("section-heading");
        FlowPane badgeFlow = new FlowPane(8, 8);
        badgeFlow.setPrefWrapLength(520);
        VBox badgesBlock = new VBox(6, lifetimeHeading, badgeFlow);

        VBox genreChart = new VBox(6);
        Label genreTitle = new Label("Loans by genre");
        genreTitle.getStyleClass().add("section-heading");
        genreChart.getChildren().add(genreTitle);

        VBox durationChart = new VBox(6);
        Label durTitle = new Label("Reading time by loan");
        durTitle.getStyleClass().add("section-heading");
        durationChart.getChildren().add(durTitle);

        Label insightScope = new Label(
                "Charts and exports use loans that match your search and filters below (not lifetime totals).");
        insightScope.setWrapText(true);
        insightScope.getStyleClass().add("login-hint");

        Runnable rebuildCharts = () -> {
            genreChart.getChildren().setAll(genreTitle);
            durationChart.getChildren().setAll(durTitle);
            Map<String, Integer> genreCounts = new HashMap<>();
            Map<String, Integer> bucket = new HashMap<>();
            bucket.put("0–5m", 0);
            bucket.put("5–30m", 0);
            bucket.put("30m–2h", 0);
            bucket.put("2h+", 0);
            for (HistoryRow h : filtered) {
                String g = h.getGenre() == null || h.getGenre().isBlank() ? "Unknown" : h.getGenre().split(",")[0].trim();
                genreCounts.merge(g, 1, Integer::sum);
                int s = h.getReadSeconds();
                if (s < 300) bucket.merge("0–5m", 1, Integer::sum);
                else if (s < 1800) bucket.merge("5–30m", 1, Integer::sum);
                else if (s < 7200) bucket.merge("30m–2h", 1, Integer::sum);
                else bucket.merge("2h+", 1, Integer::sum);
            }
            if (filtered.isEmpty()) {
                Label emptyG = new Label("No rows match the current filters.");
                emptyG.getStyleClass().add("login-hint");
                genreChart.getChildren().add(emptyG);
                Label emptyD = new Label("No rows match the current filters.");
                emptyD.getStyleClass().add("login-hint");
                durationChart.getChildren().add(emptyD);
                return;
            }
            int gMax = genreCounts.values().stream().mapToInt(Integer::intValue).max().orElse(1);
            for (var e : genreCounts.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).toList()) {
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                Label l = new Label(e.getKey() + ": " + e.getValue());
                l.setPrefWidth(140);
                ProgressBar pb = new ProgressBar(e.getValue() / (double) gMax);
                pb.setPrefWidth(160);
                row.getChildren().addAll(l, pb);
                genreChart.getChildren().add(row);
            }
            int dMax = Math.max(1, bucket.values().stream().mapToInt(Integer::intValue).max().orElse(1));
            for (String k : List.of("0–5m", "5–30m", "30m–2h", "2h+")) {
                int v = bucket.get(k);
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                Label l = new Label(k + ": " + v);
                l.setPrefWidth(100);
                ProgressBar pb = new ProgressBar(v / (double) dMax);
                pb.setPrefWidth(160);
                row.getChildren().addAll(l, pb);
                durationChart.getChildren().add(row);
            }
        };

        Label continueHeading = new Label("Continue reading");
        continueHeading.getStyleClass().add("section-heading");
        HBox continueCards = new HBox(12);
        continueCards.setAlignment(Pos.CENTER_LEFT);
        VBox continueBlock = new VBox(8, continueHeading, continueCards);
        Runnable rebuildContinue = () -> {
            continueCards.getChildren().clear();
            List<HistoryRow> resumable = all.stream().filter(HistoryRow::canOpenReader).limit(3).toList();
            boolean show = !resumable.isEmpty();
            continueBlock.setVisible(show);
            continueBlock.setManaged(show);
            if (!show) {
                return;
            }
            for (HistoryRow h : resumable) {
                VBox card = new VBox(6);
                card.getStyleClass().add("content-card");
                card.setPadding(new Insets(12));
                Label t = new Label(h.getTitle());
                t.setWrapText(true);
                t.getStyleClass().add("section-heading");
                String pageInfo = h.getLastPage0() != null
                        ? "Bookmark: page " + (h.getLastPage0() + 1)
                        : "No bookmark yet — opens at start";
                Label sub = new Label(pageInfo);
                sub.setWrapText(true);
                sub.getStyleClass().add("login-hint");
                Button go = new Button("Continue");
                go.getStyleClass().add("primary-button");
                go.setOnAction(e -> navigator.showPdfReader(user, h.getBorrowId(), h.getBookId(), h.getLastPage0(), null));
                card.getChildren().addAll(t, sub, go);
                continueCards.getChildren().add(card);
            }
        };

        Runnable rebuildBadges = () -> {
            badgeFlow.getChildren().clear();
            List<String> earned = computeBadgeLabels(all.stream().map(HistoryRow::source).toList());
            if (earned.isEmpty()) {
                Label empty = new Label("Keep borrowing and reading to unlock achievements.");
                empty.setWrapText(true);
                empty.getStyleClass().add("login-hint");
                badgeFlow.getChildren().add(empty);
                return;
            }
            for (String name : earned) {
                Label chip = new Label(name);
                chip.getStyleClass().add("badge-chip");
                badgeFlow.getChildren().add(chip);
            }
        };

        Runnable applyFilter = () -> {
            String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
            String st = statusFilter.getSelectionModel().getSelectedItem();
            String g = genreFilter.getSelectionModel().getSelectedItem();
            filtered.setPredicate(h -> {
                if (!q.isEmpty()) {
                    if (!h.getTitle().toLowerCase(Locale.ROOT).contains(q)
                            && !h.getAuthor().toLowerCase(Locale.ROOT).contains(q)) {
                        return false;
                    }
                }
                if (st != null && "Active".equals(st) && !h.isActive()) {
                    return false;
                }
                if (st != null && "Returned".equals(st) && h.isActive()) {
                    return false;
                }
                if (g != null && !"All genres".equals(g)) {
                    String gg = h.getGenre() == null ? "" : h.getGenre().toLowerCase(Locale.ROOT);
                    if (!gg.contains(g.toLowerCase(Locale.ROOT))) {
                        return false;
                    }
                }
                return true;
            });
            rebuildCharts.run();
        };

        Runnable reload = () -> {
            all.clear();
            try {
                List<ReadingHistoryRow> rows = ReadingHistoryDao.findForBorrower(user.getId());
                for (ReadingHistoryRow r : rows) {
                    all.add(new HistoryRow(r));
                }
                java.util.TreeSet<String> genres = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                for (HistoryRow h : all) {
                    if (h.getGenre() != null && !h.getGenre().isBlank()) {
                        for (String part : h.getGenre().split(",")) {
                            String t = part.trim();
                            if (!t.isEmpty()) {
                                genres.add(t);
                            }
                        }
                    }
                }
                List<String> gItems = new ArrayList<>();
                gItems.add("All genres");
                gItems.addAll(genres);
                genreFilter.setItems(FXCollections.observableArrayList(gItems));
                genreFilter.getSelectionModel().selectFirst();

                rebuildBadges.run();
                rebuildContinue.run();
            } catch (SQLException e) {
                new Alert(Alert.AlertType.ERROR, "Could not load history.").showAndWait();
                rebuildBadges.run();
                rebuildContinue.run();
            }
            applyFilter.run();
        };
        reload.run();

        search.textProperty().addListener((a, b, c) -> applyFilter.run());
        statusFilter.setOnAction(e -> applyFilter.run());
        genreFilter.setOnAction(e -> applyFilter.run());

        Runnable doExportPdf = () -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save reading history (filtered rows)");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            java.io.File out = fc.showSaveDialog(navigator.getStage());
            if (out == null) {
                return;
            }
            Path path = out.toPath();
            try {
                exportReadingHistoryPdf(path, user, filtered.stream().map(HistoryRow::source).toList());
                new Alert(Alert.AlertType.INFORMATION, "Exported to " + path.toAbsolutePath()).showAndWait();
            } catch (IOException ex) {
                new Alert(Alert.AlertType.ERROR, "Export failed: " + ex.getMessage()).showAndWait();
            }
        };
        Runnable doExportCsv = () -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save reading history (filtered rows)");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
            java.io.File f = fc.showSaveDialog(navigator.getStage());
            if (f == null) {
                return;
            }
            try {
                exportCsv(f.toPath(), filtered.stream().map(HistoryRow::source).toList());
                new Alert(Alert.AlertType.INFORMATION, "Exported to " + f.getAbsolutePath()).showAndWait();
            } catch (IOException ex) {
                new Alert(Alert.AlertType.ERROR, "Export failed: " + ex.getMessage()).showAndWait();
            }
        };

        MenuButton exportMenu = new MenuButton("Export…");
        exportMenu.getStyleClass().add("secondary-button");
        exportMenu.setTooltip(new Tooltip("PDF or CSV for the loans currently shown in the table."));
        MenuItem exportPdfItem = new MenuItem("Export as PDF…");
        exportPdfItem.setOnAction(e -> doExportPdf.run());
        MenuItem exportCsvItem = new MenuItem("Export as CSV…");
        exportCsvItem.setOnAction(e -> doExportCsv.run());
        exportMenu.getItems().addAll(exportPdfItem, exportCsvItem);

        HBox filterRow1 = new HBox(10,
                new Label("Search:"), search,
                new Label("Loan:"), statusFilter,
                new Label("Genre:"), genreFilter);
        filterRow1.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);
        search.setMaxWidth(Double.MAX_VALUE);

        HBox filterRow2 = new HBox(10, exportMenu);
        filterRow2.setAlignment(Pos.CENTER_LEFT);

        VBox filterBlock = new VBox(8, filterRow1, filterRow2);
        filterBlock.setPadding(new Insets(4, 0, 0, 0));

        Label insightsHeading = new Label("Insights");
        insightsHeading.getStyleClass().add("section-heading");
        genreChart.setMinWidth(280);
        durationChart.setMinWidth(280);
        FlowPane chartWrap = new FlowPane(16, 16, genreChart, durationChart);
        chartWrap.setPrefWrapLength(720);
        VBox insightsInner = new VBox(8, insightsHeading, insightScope, chartWrap);
        insightsInner.setPadding(new Insets(16));
        insightsInner.getStyleClass().add("content-card");

        Label lifetimeHint = new Label("Based on your full borrowing history, regardless of filters.");
        lifetimeHint.setWrapText(true);
        lifetimeHint.getStyleClass().add("login-hint");
        VBox achievementsCard = new VBox(8, badgesBlock, lifetimeHint);
        achievementsCard.setPadding(new Insets(16));
        achievementsCard.getStyleClass().add("content-card");

        Label historyHeading = new Label("Loan history");
        historyHeading.getStyleClass().add("section-heading");
        Label tableHint = new Label(
                "Adjust search or filters to narrow the table; charts and exports follow the same view.");
        tableHint.setWrapText(true);
        tableHint.getStyleClass().add("login-hint");
        VBox tableCard = new VBox(10, table);
        tableCard.getStyleClass().add("table-container");
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox center = new VBox(12, historyHeading, tableHint, filterBlock, tableCard);
        center.setPadding(new Insets(10, 0, 0, 0));
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        VBox headerBlock = new VBox(4, title, subtitle);
        VBox dashboard = new VBox(16,
                headerBlock,
                continueBlock,
                insightsInner,
                achievementsCard);
        dashboard.setFillWidth(true);
        ScrollPane dashboardScroll = new ScrollPane(dashboard);
        dashboardScroll.setFitToWidth(true);
        dashboardScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        dashboardScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        BorderPane root = new BorderPane();
        root.setTop(dashboardScroll);
        root.setCenter(center);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("app-root");

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        dashboardScroll.maxHeightProperty().bind(
                Bindings.createDoubleBinding(
                        () -> {
                            double h = scene.getHeight();
                            if (h <= 0) {
                                return 400.0;
                            }
                            return Math.min(560.0, Math.max(180.0, h * 0.52));
                        },
                        scene.heightProperty()));
        var css = ReadingHistoryScreen.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }

    private static void exportReadingHistoryPdf(Path path, User user, List<ReadingHistoryRow> rows) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            float y = page.getMediaBox().getHeight() - 48;
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            cs.setFont(font, 11);
            cs.beginText();
            cs.newLineAtOffset(48, y);
            cs.showText("Reading history — " + user.getFullName() + " (" + user.getUsername() + ")");
            cs.endText();
            y -= 22;
            for (ReadingHistoryRow r : rows) {
                String line = String.format(Locale.US, "%s | %s | borrowed %s | returned %s | read %s",
                        safe(r.title()), safe(r.author()), shortDate(r.borrowedAt()),
                        r.active() ? "active" : shortDate(r.returnedAt()), formatSeconds(r.accumulatedReadSeconds()));
                if (y < 52) {
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    cs.setFont(font, 10);
                    y = page.getMediaBox().getHeight() - 48;
                }
                cs.beginText();
                cs.newLineAtOffset(48, y);
                cs.showText(trimPdfLine(line, 95));
                cs.endText();
                y -= 14;
            }
            cs.close();
            doc.save(path.toFile());
        }
    }

    private static String trimPdfLine(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace('\r', ' ').replace('\n', ' ');
    }

    private static void exportCsv(Path path, List<ReadingHistoryRow> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("title,author,genre,borrowed_at,returned_at,read_seconds,last_page,active\n");
        for (ReadingHistoryRow r : rows) {
            sb.append(csv(r.title())).append(',')
                    .append(csv(r.author())).append(',')
                    .append(csv(r.genre())).append(',')
                    .append(csv(r.borrowedAt())).append(',')
                    .append(csv(r.returnedAt())).append(',')
                    .append(r.accumulatedReadSeconds()).append(',')
                    .append(r.lastPage() == null ? "" : r.lastPage()).append(',')
                    .append(r.active()).append('\n');
        }
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String csv(String s) {
        if (s == null) return "";
        String t = s.replace("\"", "\"\"");
        if (t.contains(",") || t.contains("\"") || t.contains("\n")) {
            return "\"" + t + "\"";
        }
        return t;
    }

    private static List<String> computeBadgeLabels(List<ReadingHistoryRow> rows) {
        long distinctBooks = rows.stream().map(ReadingHistoryRow::bookId).distinct().count();
        int totalSec = rows.stream().mapToInt(ReadingHistoryRow::accumulatedReadSeconds).sum();
        long returned = rows.stream().filter(r -> !r.active()).count();
        List<String> b = new ArrayList<>();
        if (distinctBooks >= 3) {
            b.add("Regular borrower");
        }
        if (returned >= 5) {
            b.add("Community member");
        }
        if (totalSec >= 3600) {
            b.add("1-hour reader club");
        }
        if (totalSec >= 36000) {
            b.add("Dedicated reader");
        }
        return b;
    }

    private static String shortDate(String iso) {
        if (iso == null || iso.length() < 10) return iso == null ? "" : iso;
        try {
            return iso.substring(0, 10);
        } catch (Exception e) {
            return iso;
        }
    }

    private static String formatSeconds(int sec) {
        if (sec < 60) return sec + "s";
        int m = sec / 60;
        if (m < 60) return m + "m";
        int h = m / 60;
        int rm = m % 60;
        return h + "h " + rm + "m";
    }

    public static class HistoryRow {
        private final ReadingHistoryRow src;

        HistoryRow(ReadingHistoryRow src) {
            this.src = src;
        }

        ReadingHistoryRow source() {
            return src;
        }

        public String getTitle() { return src.title(); }
        public String getAuthor() { return src.author(); }
        public String getGenre() { return src.genre(); }
        public String getBorrowedDisplay() { return shortDate(src.borrowedAt()); }
        public String getReturnedDisplay() {
            return src.active() ? "—" : shortDate(src.returnedAt());
        }
        public String getReadTimeDisplay() { return formatSeconds(src.accumulatedReadSeconds()); }
        public int getReadSeconds() { return src.accumulatedReadSeconds(); }
        public String getProgressDisplay() {
            if (src.lastPage() == null) return "—";
            return "Page " + (src.lastPage() + 1);
        }

        public boolean isActive() { return src.active(); }

        public long getBorrowId() { return src.borrowId(); }
        public long getBookId() { return src.bookId(); }

        public Integer getLastPage0() {
            return src.lastPage();
        }

        public boolean canOpenReader() {
            return src.active() && src.filePath() != null && src.filePath().toLowerCase(Locale.ROOT).endsWith(".pdf");
        }
    }
}
