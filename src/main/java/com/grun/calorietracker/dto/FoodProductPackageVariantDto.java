package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
import com.grun.calorietracker.enums.FoodPortionUnit;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A barcode-specific package inside a search presentation family.")
public record FoodProductPackageVariantDto(
        Long productId,
        String barcode,
        String displayName,
        Double packageQuantity,
        FoodPortionUnit packageUnit,
        String packageLabel,
        boolean selectedVariant,
        boolean defaultVariant,
        FoodNutritionReferenceUnit nutritionReferenceUnit,
        Double calories,
        Double protein,
        Double carbs,
        Double fat
) {}
