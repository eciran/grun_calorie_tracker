package com.grun.calorietracker.dto;

import java.time.LocalDate;
import java.util.List;

public record AdminPromotionOperationsAnalyticsDto(
        int windowDays,
        List<CountMetric> promotionStatuses,
        List<CountMetric> promotionTypes,
        List<CountMetric> redemptionStatuses,
        List<CountMetric> rejectionCategories,
        List<RedemptionTrendPoint> redemptionTrend
) {
    public record CountMetric(String name, long count) {
    }

    public record RedemptionTrendPoint(
            LocalDate date,
            long attempts,
            long converted,
            long rejected,
            long duplicateAttempts
    ) {
    }
}
