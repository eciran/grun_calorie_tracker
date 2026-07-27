package com.grun.calorietracker.dto;

import java.time.LocalDate;
import java.util.List;

public record AdminCatalogQualityAnalyticsDto(
        int windowDays,
        long totalProducts,
        long validatedProducts,
        Double averageQualityScore,
        List<CountMetric> verificationStatuses,
        List<CountMetric> openIssueTypes,
        List<ScanTrendPoint> scanTrend
) {
    public record CountMetric(String name, long count) {
    }

    public record ScanTrendPoint(
            LocalDate date,
            long scannedProducts,
            long createdSuggestions,
            long validatedProducts,
            long failedRuns
    ) {
    }
}
