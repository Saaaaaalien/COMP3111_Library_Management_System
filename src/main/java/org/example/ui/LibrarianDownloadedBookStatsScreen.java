package org.example.ui;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.example.db.BookRequestDao;
import org.example.domain.BookRequest;
import org.example.domain.BookRequest.RequestStatus;
import org.example.domain.User;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Standalone stats window that shows data only for books that have been
 * downloaded — those with status DOWNLOADED (awaiting approval) or APPROVED
 * (published to the catalog).  PENDING and REJECTED requests are excluded.
 */
public final class LibrarianDownloadedBookStatsScreen {

    private LibrarianDownloadedBookStatsScreen() {}

    public static void show(User librarian) {
        List<BookRequest> all;
        try {
            all = BookRequestDao.findAll();
        } catch (SQLException e) {
            new Alert(Alert.AlertType.ERROR, "Failed to load data: " + e.getMessage()).showAndWait();
            return;
        }

        // Only books that have been downloaded (awaiting approval OR already published).
        List<BookRequest> downloaded = all.stream()
            .filter(r -> r.getStatus() == RequestStatus.DOWNLOADED
                      || r.getStatus() == RequestStatus.APPROVED)
            .collect(Collectors.toList());

        long totalDownloaded   = downloaded.size();
        long awaitingApproval  = downloaded.stream()
            .filter(r -> r.getStatus() == RequestStatus.DOWNLOADED).count();
        long published         = downloaded.stream()
            .filter(r -> r.getStatus() == RequestStatus.APPROVED).count();

        // All genre / author / title counts are derived from the filtered list.
        Map<String, Long> genreCounts = downloaded.stream()
            .filter(r -> r.getGenre() != null && !r.getGenre().isBlank())
            .flatMap(r -> Arrays.stream(r.getGenre().split(",\\s*")))
            .collect(Collectors.groupingBy(String::trim, Collectors.counting()));

        Map<String, Long> authorCounts = downloaded.stream()
            .filter(r -> r.getAuthorName() != null && !r.getAuthorName().isBlank())
            .collect(Collectors.groupingBy(r -> r.getAuthorName().trim(), Collectors.counting()));

        Map<String, Long> titleCounts = downloaded.stream()
            .filter(r -> r.getTitle() != null && !r.getTitle().isBlank())
            .collect(Collectors.groupingBy(r -> r.getTitle().trim(), Collectors.counting()));

        // ── Header ────────────────────────────────────────────────────────────
        Label screenTitle = new Label("Downloaded Book Stats");
        screenTitle.setStyle("-fx-font-size: 20; -fx-font-weight: bold; -fx-text-fill: #1a237e;");
        Label subtitle = new Label(
                "Stats for books that have been downloaded — awaiting approval or already published");
        subtitle.setStyle("-fx-font-size: 12; -fx-text-fill: #666;");
        subtitle.setWrapText(true);
        VBox header = new VBox(4, screenTitle, subtitle);
        header.setPadding(new Insets(0, 0, 8, 0));

        // ── KPI row ───────────────────────────────────────────────────────────
        HBox kpiRow = new HBox(16,
            metricCard("Total Downloaded",   String.valueOf(totalDownloaded),  "#1565c0"),
            metricCard("Awaiting Approval",  String.valueOf(awaitingApproval), "#e65100"),
            metricCard("Published",          String.valueOf(published),         "#2e7d32")
        );
        kpiRow.setAlignment(Pos.CENTER);

        // ── Row 1: Genre bar chart + Author bar chart ─────────────────────────
        BarChart<String, Number> genreChart = makeBarChart("Downloads by Genre", genreCounts, 8);
        genreChart.setPrefSize(420, 290);

        BarChart<String, Number> authorChart = makeBarChart("Top Downloaded Authors", authorCounts, 8);
        authorChart.setPrefSize(410, 290);

        HBox row1 = new HBox(20, genreChart, authorChart);
        row1.setAlignment(Pos.CENTER);

        // ── Row 2: Top downloaded titles ──────────────────────────────────────
        BarChart<String, Number> titleChart = makeBarChart("Top Downloaded Titles", titleCounts, 10);
        titleChart.setPrefSize(860, 290);

        HBox row2 = new HBox(titleChart);
        row2.setAlignment(Pos.CENTER);

        // ── Layout ────────────────────────────────────────────────────────────
        VBox body = new VBox(16, header, kpiRow, new Separator(), row1, new Separator(), row2);
        body.setPadding(new Insets(20));
        VBox.setVgrow(body, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Stage stage = new Stage();
        stage.setTitle("Downloaded Book Stats — " + librarian.getFullName());
        Scene scene = new Scene(scroll, 920, 740);
        var css = LibrarianDownloadedBookStatsScreen.class.getResource("/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setScene(scene);
        stage.show();
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

    private static VBox metricCard(String label, String value, String color) {
        Label valLbl = new Label(value);
        valLbl.setStyle("-fx-font-size: 32; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        Label keyLbl = new Label(label);
        keyLbl.setStyle("-fx-font-size: 12; -fx-text-fill: #555;");
        VBox card = new VBox(4, valLbl, keyLbl);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(16, 32, 16, 32));
        card.setStyle("-fx-background-color: #f5f9ff; -fx-background-radius: 8; "
                    + "-fx-border-color: #c5cae9; -fx-border-radius: 8; -fx-border-width: 1;");
        card.setMinWidth(200);
        return card;
    }
}
