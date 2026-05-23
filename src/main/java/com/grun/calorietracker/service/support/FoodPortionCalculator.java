package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;

public final class FoodPortionCalculator {

    private static final double FALLBACK_SERVING_SIZE_GRAMS = 100.0;

    private FoodPortionCalculator() {
    }

    public static FoodPortionUnit resolveUnit(FoodPortionUnit portionUnit) {
        return portionUnit == null ? FoodPortionUnit.GRAM : portionUnit;
    }

    public static Double normalizeToGrams(Double portionSize, FoodPortionUnit portionUnit, FoodItemEntity foodItem) {
        if (portionSize == null) {
            return null;
        }
        FoodPortionUnit resolvedUnit = resolveUnit(portionUnit);
        if (resolvedUnit == FoodPortionUnit.SERVING || resolvedUnit == FoodPortionUnit.PIECE) {
            return portionSize * servingSizeGrams(foodItem);
        }
        return portionSize;
    }

    private static double servingSizeGrams(FoodItemEntity foodItem) {
        if (foodItem != null && foodItem.getServingSizeGrams() != null && foodItem.getServingSizeGrams() > 0) {
            return foodItem.getServingSizeGrams();
        }
        return FALLBACK_SERVING_SIZE_GRAMS;
    }
}
