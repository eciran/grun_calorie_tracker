package com.grun.calorietracker.dto;

import java.time.LocalDate;
import java.util.List;

public record AdminRecipeOperationsAnalyticsDto(
        int windowDays,
        long totalRecipes,
        long activeRecipes,
        long pendingReview,
        long publicVerified,
        long rejected,
        long archived,
        long overdueReview,
        long unassignedReview,
        List<CountMetric> verificationStatuses,
        List<CountMetric> visibilityStatuses,
        List<CountMetric> pendingAgeBands,
        List<CountMetric> importStatuses,
        EngagementMetric engagement,
        List<SubmissionTrendPoint> submissionTrend
) {
    public record CountMetric(String name, long count) {
    }

    public record EngagementMetric(long saved, long favorite, long rated, Double averageRating) {
    }

    public record SubmissionTrendPoint(LocalDate date, long createdRecipes) {
    }
}