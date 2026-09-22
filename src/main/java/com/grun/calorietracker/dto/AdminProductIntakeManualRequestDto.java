package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodNutritionBasis;
import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.MarketRegion;
import jakarta.validation.constraints.*;
import java.util.Set;

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
        @PositiveOrZero Double saturatedFat,
        @PositiveOrZero Double transFat,
        @PositiveOrZero Double sugarAlcohol,
        @PositiveOrZero Double potassium,
        @PositiveOrZero Double cholesterol,
        @PositiveOrZero Double calcium,
        @PositiveOrZero Double iron,
        @PositiveOrZero Double magnesium,
        @PositiveOrZero Double zinc,
        @PositiveOrZero Double vitaminA,
        @PositiveOrZero Double vitaminC,
        @PositiveOrZero Double vitaminD,
        @PositiveOrZero Double vitaminE,
        @PositiveOrZero Double vitaminB12,
        @NotNull FoodNutritionBasis nutritionBasis,
        @NotNull FoodNutritionReferenceUnit nutritionReferenceUnit,
        @Positive @DecimalMax("100000") Double servingSizeGrams,
        @Size(max = 100) String servingUnit,
        @NotNull FoodCatalogType catalogType,
        FoodPreparationState preparationState,
        @Size(max = 5000) String ingredientsText,
        @Size(max = 1000) String allergens,
        @Size(max = 30) Set<@NotBlank @Size(max = 180) String> sourceCategoryTags,
        @Size(max = 160) String adminSourceName,
        @Size(max = 1000) String adminSourceUrl,
        @Size(max = 500) String adminCreationNote
) {
}
