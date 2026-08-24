package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodProductResolutionMode;
import com.grun.calorietracker.enums.FoodProductReviewCaseSource;
import com.grun.calorietracker.enums.FoodProductReviewCaseStatus;
import com.grun.calorietracker.enums.FoodProductReviewRiskLevel;
import com.grun.calorietracker.enums.MarketRegion;

import java.time.LocalDateTime;

public record AdminProductIntakeSummaryDto(
        Long id,
        FoodProductReviewCaseSource source,
        FoodProductReviewCaseStatus status,
        MarketRegion marketRegion,
        String barcode,
        FoodProductResolutionMode resolutionMode,
        FoodProductReviewRiskLevel riskLevel,
        String assignedAdminEmail,
        LocalDateTime reviewClaimedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}