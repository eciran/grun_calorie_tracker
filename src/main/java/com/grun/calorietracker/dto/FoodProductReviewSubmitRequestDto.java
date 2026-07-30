package com.grun.calorietracker.dto;
import com.grun.calorietracker.enums.*;
import jakarta.validation.constraints.*;
import java.util.Map;
public record FoodProductReviewSubmitRequestDto(
 @NotBlank @Size(max=100) String idempotencyKey, @NotBlank @Size(max=64) String barcode,
 @NotNull MarketRegion marketRegion, @NotBlank @Size(max=255) String productName,
 @Size(max=255) String brand, @NotNull @PositiveOrZero Double calories,
 @PositiveOrZero Double protein, @PositiveOrZero Double fat, @PositiveOrZero Double carbs,
 @PositiveOrZero Double fiber, @PositiveOrZero Double sugar, @PositiveOrZero Double sodium,
 @NotNull FoodNutritionBasis nutritionBasis, FoodProductReviewRiskLevel riskLevel,
 @NotNull Map<String,Object> submittedFields, @NotNull Map<String,Object> fieldConfidence,
 @NotNull Map<String,Object> correctionSummary, @NotBlank @Size(max=50) String consentVersion,
 @AssertTrue boolean temporaryEvidenceAllowed, boolean publicMediaAllowed) {}
