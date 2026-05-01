package org.example.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.example.app.Navigator;
import org.example.db.AuthorStatsDao;
import org.example.db.AuthorStatsDao.AuthorStatsSnapshot;
import org.example.domain.User;

import java.sql.SQLException;
import java.util.List;

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

        VBox header = new VBox(6, title, subtitle);
        header.setPadding(new Insets(0, 0, 12, 0));
        root.setTop(header);

        VBox body = new VBox(18);
        body.setPadding(new Insets(6));

        try {
            AuthorStatsSnapshot stats = AuthorStatsDao.load(user.getId());
            body.getChildren().add(createMetricGrid(stats));
            body.getChildren().add(createBorrowsBarChart(stats.topBorrowedBooks()));
            body.getChildren().add(createDistributionRow(stats));
            body.getChildren().add(createNotesCard(stats));
        } catch (SQLException ex) {
            body.getChildren().add(new Label("Could not load stats right now."));
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to load author stats: " + ex.getMessage());
            alert.showAndWait();
        }

        ScrollPane scrollPane = new ScrollPane(body);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        // Navigation handled by global menu; remove per-screen Back button
        VBox center = new VBox(10, scrollPane);
        root.setCenter(center);

        Scene scene = new Scene(root, Navigator.getPreferredWidth(), Navigator.getPreferredHeight());
        var css = AuthorStatsScreen.class.getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }

    private static GridPane createMetricGrid(AuthorStatsSnapshot stats) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);

        grid.add(metricCard("Published Books", String.valueOf(stats.publishedBooks())), 0, 0);
        grid.add(metricCard("Total Reads", String.valueOf(stats.totalReads())), 1, 0);
        grid.add(metricCard("Total Borrows", String.valueOf(stats.totalBorrows())), 2, 0);
        grid.add(metricCard("Active Borrows", String.valueOf(stats.activeBorrows())), 0, 1);
        grid.add(metricCard("Unique Readers", String.valueOf(stats.distinctReaders())), 1, 1);

        String ratingText = stats.reviewCount() > 0
                ? String.format("%.2f / 5", stats.averageRating())
                : "N/A";
        grid.add(metricCard("Average Rating", ratingText), 2, 1);
        grid.add(metricCard("Review Count", String.valueOf(stats.reviewCount())), 0, 2);
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

    private static HBox createDistributionRow(AuthorStatsSnapshot stats) {
        VBox left = createGenrePie(stats.genreDistribution());
        VBox right = createBorrowStatusPie(stats.borrowStatusDistribution());
        HBox row = new HBox(16, left, right);
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
