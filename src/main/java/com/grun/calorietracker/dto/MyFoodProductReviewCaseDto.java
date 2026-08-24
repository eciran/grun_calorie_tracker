package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodProductReviewCaseStatus;
import com.grun.calorietracker.enums.FoodProductReviewRiskLevel;
import com.grun.calorietracker.enums.MarketRegion;

import java.time.LocalDateTime;

public record MyFoodProductReviewCaseDto(
        Long id,
        String barcode,
        String productName,
        MarketRegion marketRegion,
        FoodProductReviewCaseStatus status,
        FoodProductReviewRiskLevel riskLevel,
        String reviewNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
