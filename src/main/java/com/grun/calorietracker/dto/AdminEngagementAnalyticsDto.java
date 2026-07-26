package com.grun.calorietracker.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminEngagementAnalyticsDto(
        int eventContractVersion,
        int hours,
        LocalDateTime since,
        LocalDateTime generatedAt,
        Filters filters,
        OnboardingFunnel onboarding,
        SearchQuality search,
        FlowMetric foodLogging,
        FlowMetric barcode,
        List<FeatureAdoption> featureAdoption,
        List<SegmentMetric> regionComparison,
        List<SegmentMetric> languageComparison,
        List<SegmentMetric> planComparison
) {
    public record Filters(String region, String language, String plan) {}

    public record OnboardingFunnel(
            long started, long stepViewed, long stepCompleted, long stepFailed,
            long resumed, long previewed, long completed, long abandoned,
            double completionRate, double failureRate, long averageCompletionDurationMs
    ) {}

    public record SearchQuality(
            long searches, long zeroResultSearches, long selectedSearches,
            long noSelectionSearches, double zeroResultRate, double selectionRate,
            boolean planFilterApplied,
            List<SearchQuery> topZeroResultQueries
    ) {}

    public record SearchQuery(String query, long searches) {}

    public record FlowMetric(
            long started, long completed, long firstCompletions, long failed, long uniqueUsers,
            double completionRate, long averageDurationMs
    ) {}

    public record FeatureAdoption(
            String feature, long events, long uniqueUsers,
            long repeatEvents, long averageDurationMs
    ) {}

    public record SegmentMetric(String segment, long events, long uniqueUsers) {}
}
