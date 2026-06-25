package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;

public final class FoodPortionCalculator {

    private static final double FALLBACK_SERVING_SIZE_GRAMS = 100.0;
    private static final double TABLESPOON_GRAMS = 15.0;
    private static final double TEASPOON_GRAMS = 5.0;

    private FoodPortionCalculator() {
    }

    public static FoodPortionUnit resolveUnit(FoodPortionUnit portionUnit) {
        return portionUnit == null ? FoodPortionUnit.GRAM : portionUnit;
    }

    public static Double normalizeToGrams(Double portionSize, FoodPortionUnit portionUnit, FoodItemEntity foodItem) {
        return normalizeToGrams(portionSize, portionUnit, foodItem, null);
    }

    public static Double normalizeToGrams(
            Double portionSize,
            FoodPortionUnit portionUnit,
            FoodItemEntity foodItem,
            FoodItemServingOptionEntity servingOption
    ) {
        if (portionSize == null) {
            return null;
        }
        if (servingOption != null) {
            return portionSize * servingOptionWeightInGrams(servingOption);
        }
        FoodPortionUnit resolvedUnit = resolveUnit(portionUnit);
        if (resolvedUnit == FoodPortionUnit.SERVING || resolvedUnit == FoodPortionUnit.PIECE || resolvedUnit == FoodPortionUnit.SLICE) {
            return portionSize * servingSizeGrams(foodItem);
        }
        if (resolvedUnit == FoodPortionUnit.TABLESPOON) {
            return portionSize * TABLESPOON_GRAMS;
        }
        if (resolvedUnit == FoodPortionUnit.TEASPOON) {
            return portionSize * TEASPOON_GRAMS;
        }
        return portionSize;
    }

    private static double servingOptionWeightInGrams(FoodItemServingOptionEntity servingOption) {
        if (servingOption.getGramWeight() != null && servingOption.getGramWeight() > 0) {
            return servingOption.getGramWeight();
        }
        if (servingOption.getMlVolume() != null && servingOption.getMlVolume() > 0) {
            return servingOption.getMlVolume();
        }
        throw new IllegalArgumentException("Serving option must define a positive gram weight or milliliter volume.");
    }

    private static double servingSizeGrams(FoodItemEntity foodItem) {
        if (foodItem != null && foodItem.getServingSizeGrams() != null && foodItem.getServingSizeGrams() > 0) {
            return foodItem.getServingSizeGrams();
        }
        return FALLBACK_SERVING_SIZE_GRAMS;
    }
}
