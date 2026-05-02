package org.example.domain;

import java.util.Properties;

/**
 * Visibility toggles for sections on the author statistics dashboard.
 * Defaults keep every section visible (current full-dashboard behavior).
 */
public record AuthorStatsDashboardPrefs(
        boolean kpiPublishedBooks,
        boolean kpiTotalReads,
        boolean kpiTotalBorrows,
        boolean kpiActiveBorrows,
        boolean kpiUniqueReaders,
        boolean kpiAverageRating,
        boolean kpiReviewCount,
        boolean chartTopBorrowed,
        boolean chartGenrePie,
        boolean chartBorrowStatusPie,
        boolean chartBorrowTrend,
        boolean sectionNotes
) {
    public static final AuthorStatsDashboardPrefs ALL_VISIBLE = new AuthorStatsDashboardPrefs(
            true, true, true, true, true, true, true,
            true, true, true, true, true
    );

    private static boolean readBool(Properties p, String key, boolean defaultValue) {
        String v = p.getProperty(key);
        if (v == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(v.trim());
    }

    /**
     * Parses stored properties; any missing key defaults to {@code true}.
     */
    public static AuthorStatsDashboardPrefs fromProperties(Properties p) {
        return new AuthorStatsDashboardPrefs(
                readBool(p, "kpi.publishedBooks", true),
                readBool(p, "kpi.totalReads", true),
                readBool(p, "kpi.totalBorrows", true),
                readBool(p, "kpi.activeBorrows", true),
                readBool(p, "kpi.uniqueReaders", true),
                readBool(p, "kpi.averageRating", true),
                readBool(p, "kpi.reviewCount", true),
                readBool(p, "chart.topBorrowed", true),
                readBool(p, "chart.genrePie", true),
                readBool(p, "chart.borrowStatusPie", true),
                readBool(p, "chart.borrowTrend", true),
                readBool(p, "section.notes", true)
        );
    }

    public void storeInto(Properties p) {
        p.setProperty("kpi.publishedBooks", Boolean.toString(kpiPublishedBooks));
        p.setProperty("kpi.totalReads", Boolean.toString(kpiTotalReads));
        p.setProperty("kpi.totalBorrows", Boolean.toString(kpiTotalBorrows));
        p.setProperty("kpi.activeBorrows", Boolean.toString(kpiActiveBorrows));
        p.setProperty("kpi.uniqueReaders", Boolean.toString(kpiUniqueReaders));
        p.setProperty("kpi.averageRating", Boolean.toString(kpiAverageRating));
        p.setProperty("kpi.reviewCount", Boolean.toString(kpiReviewCount));
        p.setProperty("chart.topBorrowed", Boolean.toString(chartTopBorrowed));
        p.setProperty("chart.genrePie", Boolean.toString(chartGenrePie));
        p.setProperty("chart.borrowStatusPie", Boolean.toString(chartBorrowStatusPie));
        p.setProperty("chart.borrowTrend", Boolean.toString(chartBorrowTrend));
        p.setProperty("section.notes", Boolean.toString(sectionNotes));
    }
}
