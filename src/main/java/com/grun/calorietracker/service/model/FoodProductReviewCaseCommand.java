package com.grun.calorietracker.service.model;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodNutritionBasis;
import com.grun.calorietracker.enums.FoodProductReviewCaseSource;
import com.grun.calorietracker.enums.FoodProductReviewRiskLevel;
import com.grun.calorietracker.enums.MarketRegion;

public record FoodProductReviewCaseCommand(
        String idempotencyKey,
        FoodProductReviewCaseSource source,
        String sourceReference,
        UserEntity submittedBy,
        String barcode,
        MarketRegion marketRegion,
        Long existingFoodItemId,
        String productName,
        String brand,
        Double calories,
        Double protein,
        Double fat,
        Double carbs,
        Double fiber,
        Double sugar,
        Double sodium,
        FoodNutritionBasis nutritionBasis,
        FoodProductReviewRiskLevel riskLevel,
        int schemaVersion,
        String submittedValuesJson,
        String fieldConfidenceJson,
        String correctionSummaryJson,
        String consentVersion,
        boolean temporaryEvidenceAllowed,
        boolean publicMediaAllowed
) {
}
