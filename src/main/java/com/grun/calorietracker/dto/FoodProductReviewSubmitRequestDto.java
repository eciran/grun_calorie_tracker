package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodNutritionBasis;
import com.grun.calorietracker.enums.FoodProductReviewRiskLevel;
import com.grun.calorietracker.enums.MarketRegion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record FoodProductReviewSubmitRequestDto(
        @NotBlank @Size(max = 100) String idempotencyKey,
        @NotBlank @Size(max = 64) String barcode,
        @NotNull MarketRegion marketRegion,
        @NotBlank @Size(max = 255) String productName,
        @Size(max = 255) String brand,
        @NotNull @PositiveOrZero Double calories,
        @PositiveOrZero Double protein,
        @PositiveOrZero Double fat,
        @PositiveOrZero Double carbs,
        @PositiveOrZero Double fiber,
        @PositiveOrZero Double sugar,
        @PositiveOrZero Double sodium,
        @NotNull FoodNutritionBasis nutritionBasis,
        FoodProductReviewRiskLevel riskLevel,
        @NotNull Map<String, Object> submittedFields,
        @NotNull Map<String, Object> fieldConfidence,
        @NotNull Map<String, Object> correctionSummary,
        @NotBlank @Size(max = 50) String consentVersion,
        @AssertTrue boolean temporaryEvidenceAllowed,
        boolean publicMediaAllowed,
        @Valid FoodProductOcrExtractionDto ocrExtraction
) {
}
