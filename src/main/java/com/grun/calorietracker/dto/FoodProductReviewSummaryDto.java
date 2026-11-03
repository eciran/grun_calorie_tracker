package com.grun.calorietracker.dto;

public record FoodProductReviewSummaryDto(
        long totalElements,
        long highQualityCount,
        long reviewQualityCount,
        long lowQualityCount,
        long highPriorityCount,
        long missingImageCount,
        int averageQuality
) {
}
