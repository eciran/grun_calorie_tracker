package com.grun.calorietracker.dto;

import java.util.List;

public record FoodSearchTelemetrySummaryDto(
        int windowHours,
        long searches,
        long zeroResultSearches,
        long selectedSearches,
        long noSelectionSearches,
        double zeroResultRate,
        double selectionRate,
        List<ZeroResultQuery> topZeroResultQueries
) {
    public record ZeroResultQuery(String query, long searches) {}
}