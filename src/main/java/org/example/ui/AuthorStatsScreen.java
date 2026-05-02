package org.example.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleGroup;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.app.Navigator;
import org.example.db.AuthorStatsDao;
import org.example.db.AuthorStatsDao.AuthorStatsSnapshot;
import org.example.db.AuthorStatsDao.BorrowTrendPoint;
import org.example.db.AuthorStatsDao.TrendGranularity;
import org.example.domain.AuthorStatsDashboardPrefs;
import org.example.domain.User;
import org.example.service.AuthorStatsPrefsStore;
import org.example.util.AuthorStatsReportExporter;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Displays dashboard statistics for an author's published books.
 */
public final class AuthorStatsScreen {

    private AuthorStatsScreen() {}

    public static Scene create(Navigator navigator, User user) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));
        root.getStyleClass().add("app-root");

        Label title = new Label("Author Book Statistics");
        title.getStyleClass().add("screen-title");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));

        Label subtitle = new Label("Overview of reads, borrows, ratings, and reviews for your published books.");
        subtitle.setStyle("-fx-text-fill: #6c7a89;");

        VBox titleBlock = new VBox(6, title, subtitle);

        AtomicReference<AuthorStatsDashboardPrefs> prefsRef = new AtomicReference<>(loadPrefsSafe(user.getId()));
        VBox body = new VBox(18);
        body.setPadding(new Insets(6));

        Runnable rebuild = () -> rebuildBody(user, body, prefsRef.get());
        rebuild.run();

        Button customizeBtn = new Button("Customize");
        customizeBtn.getStyleClass().add("secondary-button");
        customizeBtn.setOnAction(e -> {
            AuthorStatsDashboardPrefs next = openCustomizeDialog(customizeBtn.getScene().getWindow(), prefsRef.get());
            if (next != null) {
                prefsRef.set(next);
                try {
                    AuthorStatsPrefsStore.save(user.getId(), next);
                } catch (IOException ex) {
                    new Alert(Alert.AlertType.WARNING, "Could not save dashboard preferences: " + ex.getMessage()).showAndWait();
                }
                rebuild.run();
            }
        });

        Button exportPdfBtn = new Button("Export PDF");
        exportPdfBtn.getStyleClass().add("secondary-button");
        exportPdfBtn.setOnAction(e -> exportReport(exportPdfBtn.getScene().getWindow(), user, true));

        Button exportXlsxBtn = new Button("Export Excel");
        exportXlsxBtn.getStyleClass().add("secondary-button");
        exportXlsxBtn.setOnAction(e -> exportReport(exportXlsxBtn.getScene().getWindow(), user, false));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(10, customizeBtn, exportPdfBtn, exportXlsxBtn, spacer);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 0, 8, 0));

        VBox header = new VBox(8, titleBlock, new Separator(), toolbar);
        header.setPadding(new Insets(0, 0, 12, 0));
        root.setTop(header);

        ScrollPane scrollPane = new ScrollPane(body);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Button backBtn = new Button("Back to Dashboard");
        backBtn.getStyleClass().add("secondary-button");
        backBtn.setOnAction(ev -> navigator.showAuthorDashboard(user));

        HBox footer = new HBox(backBtn);
        footer.setPadding(new Insets(12, 0, 0, 0));
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox center = new VBox(10, scrollPane, footer);
        root.setCenter(center);

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        var css = AuthorStatsScreen.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }

    private static AuthorStatsDashboardPrefs loadPrefsSafe(long authorUserId) {
        try {
            return AuthorStatsPrefsStore.load(authorUserId);
        } catch (IOException ex) {
            return AuthorStatsDashboardPrefs.ALL_VISIBLE;
        }
    }

    private static void rebuildBody(User user, VBox body, AuthorStatsDashboardPrefs prefs) {
        body.getChildren().clear();
        try {
            AuthorStatsSnapshot stats = AuthorStatsDao.load(user.getId());
            List<BorrowTrendPoint> weekly = AuthorStatsDao.loadBorrowTrend(user.getId(), TrendGranularity.WEEK);
            List<BorrowTrendPoint> monthly = AuthorStatsDao.loadBorrowTrend(user.getId(), TrendGranularity.MONTH);

            addIfVisible(body, prefs.kpiPublishedBooks() || prefs.kpiTotalReads() || prefs.kpiTotalBorrows()
                            || prefs.kpiActiveBorrows() || prefs.kpiUniqueReaders() || prefs.kpiAverageRating()
                            || prefs.kpiReviewCount(),
                    () -> createMetricGrid(stats, prefs));

            addIfVisible(body, prefs.chartTopBorrowed(), () -> createBorrowsBarChart(stats.topBorrowedBooks()));

            NodeFactory dist = () -> createDistributionRow(stats, prefs);
            if (prefs.chartGenrePie() || prefs.chartBorrowStatusPie()) {
                body.getChildren().add(dist.create());
            }

            addIfVisible(body, prefs.chartBorrowTrend(),
                    () -> createBorrowTrendChart(weekly, monthly));

            addIfVisible(body, prefs.sectionNotes(), () -> createNotesCard(stats));
        } catch (SQLException ex) {
            body.getChildren().add(new Label("Could not load stats right now."));
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to load author stats: " + ex.getMessage());
            alert.showAndWait();
        }
    }

    @FunctionalInterface
    private interface NodeFactory {
        Node create();
    }

    private static void addIfVisible(VBox body, boolean visible, NodeFactory factory) {
        if (visible) {
            body.getChildren().add(factory.create());
        }
    }

    private static AuthorStatsDashboardPrefs openCustomizeDialog(Window owner, AuthorStatsDashboardPrefs current) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Customize dashboard");
        dialog.setHeaderText("Choose which sections appear on your statistics screen.");

        CheckBox kpiPub = new CheckBox("Published books");
        CheckBox kpiReads = new CheckBox("Total reads");
        CheckBox kpiBorrows = new CheckBox("Total borrows");
        CheckBox kpiActive = new CheckBox("Active borrows");
        CheckBox kpiReaders = new CheckBox("Unique readers");
        CheckBox kpiRating = new CheckBox("Average rating");
        CheckBox kpiReviews = new CheckBox("Review count");
        CheckBox chTop = new CheckBox("Top borrowed books chart");
        CheckBox chGenre = new CheckBox("Genre pie chart");
        CheckBox chStatus = new CheckBox("Borrow status pie chart");
        CheckBox chTrend = new CheckBox("Borrow trend chart");
        CheckBox secNotes = new CheckBox("Notes card");

        kpiPub.setSelected(current.kpiPublishedBooks());
        kpiReads.setSelected(current.kpiTotalReads());
        kpiBorrows.setSelected(current.kpiTotalBorrows());
        kpiActive.setSelected(current.kpiActiveBorrows());
        kpiReaders.setSelected(current.kpiUniqueReaders());
        kpiRating.setSelected(current.kpiAverageRating());
        kpiReviews.setSelected(current.kpiReviewCount());
        chTop.setSelected(current.chartTopBorrowed());
        chGenre.setSelected(current.chartGenrePie());
        chStatus.setSelected(current.chartBorrowStatusPie());
        chTrend.setSelected(current.chartBorrowTrend());
        secNotes.setSelected(current.sectionNotes());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        int r = 0;
        grid.add(new Label("KPI cards"), 0, r++, 2, 1);
        grid.add(kpiPub, 0, r);
        grid.add(kpiReads, 1, r++);
        grid.add(kpiBorrows, 0, r);
        grid.add(kpiActive, 1, r++);
        grid.add(kpiReaders, 0, r);
        grid.add(kpiRating, 1, r++);
        grid.add(kpiReviews, 0, r++);
        grid.add(new Label("Charts"), 0, r++, 2, 1);
        grid.add(chTop, 0, r++, 2, 1);
        grid.add(chGenre, 0, r);
        grid.add(chStatus, 1, r++);
        grid.add(chTrend, 0, r++, 2, 1);
        grid.add(new Label("Other"), 0, r++, 2, 1);
        grid.add(secNotes, 0, r++, 2, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        return dialog.showAndWait()
                .filter(ButtonType.OK::equals)
                .map(bt -> new AuthorStatsDashboardPrefs(
                        kpiPub.isSelected(),
                        kpiReads.isSelected(),
                        kpiBorrows.isSelected(),
                        kpiActive.isSelected(),
                        kpiReaders.isSelected(),
                        kpiRating.isSelected(),
                        kpiReviews.isSelected(),
                        chTop.isSelected(),
                        chGenre.isSelected(),
                        chStatus.isSelected(),
                        chTrend.isSelected(),
                        secNotes.isSelected()
                ))
                .orElse(null);
    }

    private static void exportReport(Window owner, User user, boolean pdf) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(pdf ? "Export author statistics (PDF)" : "Export author statistics (Excel)");
        if (pdf) {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            chooser.setInitialFileName("author-stats.pdf");
        } else {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
            chooser.setInitialFileName("author-stats.xlsx");
        }
        File dest = chooser.showSaveDialog(owner);
        if (dest == null) {
            return;
        }
        try {
            AuthorStatsSnapshot stats = AuthorStatsDao.load(user.getId());
            List<BorrowTrendPoint> weekly = AuthorStatsDao.loadBorrowTrend(user.getId(), TrendGranularity.WEEK);
            List<BorrowTrendPoint> monthly = AuthorStatsDao.loadBorrowTrend(user.getId(), TrendGranularity.MONTH);
            if (pdf) {
                AuthorStatsReportExporter.exportPdf(dest.toPath(), stats, weekly, monthly);
            } else {
                AuthorStatsReportExporter.exportExcel(dest.toPath(), stats, weekly, monthly);
            }
            new Alert(Alert.AlertType.INFORMATION, "Report saved to:\n" + dest.getAbsolutePath()).showAndWait();
            tryOpenFile(dest);
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Export failed: " + ex.getMessage()).showAndWait();
        }
    }

    private static void tryOpenFile(File file) {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        Desktop d = Desktop.getDesktop();
        if (d.isSupported(Desktop.Action.OPEN)) {
            try {
                d.open(file);
            } catch (IOException ignored) {
            }
        }
    }

    private static GridPane createMetricGrid(AuthorStatsSnapshot stats, AuthorStatsDashboardPrefs p) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);

        List<Node> cards = new ArrayList<>();
        if (p.kpiPublishedBooks()) {
            cards.add(metricCard("Published Books", String.valueOf(stats.publishedBooks())));
        }
        if (p.kpiTotalReads()) {
            cards.add(metricCard("Total Reads", String.valueOf(stats.totalReads())));
        }
        if (p.kpiTotalBorrows()) {
            cards.add(metricCard("Total Borrows", String.valueOf(stats.totalBorrows())));
        }
        if (p.kpiActiveBorrows()) {
            cards.add(metricCard("Active Borrows", String.valueOf(stats.activeBorrows())));
        }
        if (p.kpiUniqueReaders()) {
            cards.add(metricCard("Unique Readers", String.valueOf(stats.distinctReaders())));
        }
        if (p.kpiAverageRating()) {
            String ratingText = stats.reviewCount() > 0
                    ? String.format("%.2f / 5", stats.averageRating())
                    : "N/A";
            cards.add(metricCard("Average Rating", ratingText));
        }
        if (p.kpiReviewCount()) {
            cards.add(metricCard("Review Count", String.valueOf(stats.reviewCount())));
        }
        for (int i = 0; i < cards.size(); i++) {
            grid.add(cards.get(i), i % 3, i / 3);
        }
        return grid;
    }

    private static VBox metricCard(String label, String value) {
        Label kpiLabel = new Label(label);
        kpiLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px; -fx-font-weight: bold;");

        Label kpiValue = new Label(value);
        kpiValue.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 22px; -fx-font-weight: bold;");

        VBox card = new VBox(6, kpiLabel, kpiValue);
        card.setMinWidth(220);
        card.setPadding(new Insets(12));
        card.setStyle(
                "-fx-background-color: white;" +
                "-fx-background-radius: 8;" +
                "-fx-border-color: #dfe6ee;" +
                "-fx-border-radius: 8;"
        );
        return card;
    }

    private static VBox createBorrowsBarChart(List<AuthorStatsDao.BookBorrowStat> topBorrowedBooks) {
        Label label = new Label("Top Borrowed Books");
        label.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Book");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Borrow Count");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setTitle("Borrow Count by Book");
        chart.setCategoryGap(10);
        chart.setBarGap(4);
        chart.setPrefHeight(320);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        if (topBorrowedBooks.isEmpty()) {
            series.getData().add(new XYChart.Data<>("No published books", 0));
        } else {
            for (AuthorStatsDao.BookBorrowStat stat : topBorrowedBooks) {
                series.getData().add(new XYChart.Data<>(trim(stat.title(), 22), stat.borrowCount()));
            }
        }
        chart.getData().add(series);

        return new VBox(8, label, chart);
    }

    private static HBox createDistributionRow(AuthorStatsSnapshot stats, AuthorStatsDashboardPrefs p) {
        List<Node> parts = new ArrayList<>();
        if (p.chartGenrePie()) {
            parts.add(createGenrePie(stats.genreDistribution()));
        }
        if (p.chartBorrowStatusPie()) {
            parts.add(createBorrowStatusPie(stats.borrowStatusDistribution()));
        }
        HBox row = new HBox(16);
        row.getChildren().addAll(parts);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static VBox createGenrePie(List<AuthorStatsDao.GenreStat> data) {
        Label label = new Label("Published Books by Genre");
        label.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        PieChart chart = new PieChart();
        chart.setTitle("Genre Distribution");
        chart.setLegendVisible(true);
        chart.setLabelsVisible(true);
        chart.setPrefSize(400, 320);

        if (data.isEmpty()) {
            chart.setData(FXCollections.observableArrayList(new PieChart.Data("No data", 1)));
        } else {
            var slices = FXCollections.<PieChart.Data>observableArrayList();
            for (AuthorStatsDao.GenreStat stat : data) {
                slices.add(new PieChart.Data(stat.genre(), stat.count()));
            }
            chart.setData(slices);
        }

        VBox box = new VBox(8, label, chart);
        box.setPadding(new Insets(8));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #dfe6ee; -fx-border-radius: 8;");
        return box;
    }

    private static VBox createBorrowStatusPie(List<AuthorStatsDao.BorrowStatusStat> data) {
        Label label = new Label("Borrow Status Split");
        label.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        PieChart chart = new PieChart();
        chart.setTitle("Active vs Returned");
        chart.setLegendVisible(true);
        chart.setLabelsVisible(true);
        chart.setPrefSize(400, 320);

        if (data.isEmpty()) {
            chart.setData(FXCollections.observableArrayList(new PieChart.Data("No borrows", 1)));
        } else {
            var slices = FXCollections.<PieChart.Data>observableArrayList();
            for (AuthorStatsDao.BorrowStatusStat stat : data) {
                slices.add(new PieChart.Data(stat.status(), stat.count()));
            }
            chart.setData(slices);
        }

        VBox box = new VBox(8, label, chart);
        box.setPadding(new Insets(8));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #dfe6ee; -fx-border-radius: 8;");
        return box;
    }

    private static VBox createBorrowTrendChart(List<BorrowTrendPoint> weekly, List<BorrowTrendPoint> monthly) {
        Label heading = new Label("Borrow trend");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        ToggleGroup group = new ToggleGroup();
        RadioButton rbWeek = new RadioButton("Weekly");
        RadioButton rbMonth = new RadioButton("Monthly");
        rbWeek.setToggleGroup(group);
        rbMonth.setToggleGroup(group);
        rbMonth.setSelected(true);
        HBox modeRow = new HBox(12, rbWeek, rbMonth);
        modeRow.setAlignment(Pos.CENTER_LEFT);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Borrows");
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Borrows over time");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(320);
        chart.setCreateSymbols(true);

        Runnable applyMonth = () -> applyTrendSeries(chart, monthly);
        Runnable applyWeek = () -> applyTrendSeries(chart, weekly);
        rbMonth.setOnAction(e -> applyMonth.run());
        rbWeek.setOnAction(e -> applyWeek.run());
        applyMonth.run();

        return new VBox(8, heading, modeRow, chart);
    }

    private static void applyTrendSeries(LineChart<String, Number> chart, List<BorrowTrendPoint> points) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Borrows");
        if (points.isEmpty()) {
            series.getData().add(new XYChart.Data<>("No data", 0));
        } else {
            for (BorrowTrendPoint p : points) {
                series.getData().add(new XYChart.Data<>(p.periodLabel(), p.borrowCount()));
            }
        }
        chart.getData().clear();
        chart.getData().add(series);
    }

    private static VBox createNotesCard(AuthorStatsSnapshot stats) {
        Label note = new Label(
                stats.reviewCount() == 0
                        ? "Ratings and review metrics show N/A/0 because no review data source is currently stored in the database."
                        : "Ratings and review metrics are computed from stored review entries."
        );
        note.setWrapText(true);
        note.setStyle("-fx-text-fill: #5d6d7e;");

        VBox card = new VBox(note);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: #f8fbff; -fx-background-radius: 8; -fx-border-color: #d6e7ff; -fx-border-radius: 8;");
        return card;
    }

    private static String trim(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLen - 3)) + "...";
    }
}
