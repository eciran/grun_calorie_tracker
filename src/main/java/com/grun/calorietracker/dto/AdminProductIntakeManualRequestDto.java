package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodNutritionBasis;
import com.grun.calorietracker.enums.MarketRegion;
import jakarta.validation.constraints.*;

public record AdminProductIntakeManualRequestDto(
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
        @NotNull FoodNutritionBasis nutritionBasis
) {
}