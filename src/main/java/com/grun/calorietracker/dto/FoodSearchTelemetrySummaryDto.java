package com.grun.calorietracker.dto;

public record FoodSearchTelemetrySummaryDto(
        int windowHours,
        long searches,
        long zeroResultSearches,
        long selectedSearches,
        long noSelectionSearches,
        double zeroResultRate,
        double selectionRate
) {
}