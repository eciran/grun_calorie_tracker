package com.grun.calorietracker.service.support;

import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;

import java.util.Locale;

public final class FoodNutritionUnitContract {

    private FoodNutritionUnitContract() {
    }

    public static String normalizeServingUnit(String servingUnit) {
        if (servingUnit == null || servingUnit.isBlank()) {
            return null;
        }
        String normalized = servingUnit.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ML", "MILLILITRE", "MILLILITRES", "MILLILITER", "MILLILITERS" -> "MILLILITER";
            case "G", "GRAM", "GRAMS" -> "GRAM";
            case "SERVING", "SERVINGS", "PORTION", "PORTIONS" -> "SERVING";
            default -> normalized;
        };
    }

    public static void validate(FoodNutritionReferenceUnit referenceUnit, String servingUnit) {
        String normalized = normalizeServingUnit(servingUnit);
        if (referenceUnit == null) {
            if (normalized != null) {
                throw new IllegalArgumentException("Nutrition reference unit is required when a serving unit is set.");
            }
            return;
        }
        if (normalized == null) {
            return;
        }
        if (referenceUnit == FoodNutritionReferenceUnit.PER_100ML && !"MILLILITER".equals(normalized)) {
            throw new IllegalArgumentException("PER_100ML nutrition requires an ml serving unit.");
        }
        if (referenceUnit == FoodNutritionReferenceUnit.PER_100G && "MILLILITER".equals(normalized)) {
            throw new IllegalArgumentException("PER_100G nutrition cannot use an ml serving unit without a separately verified mass-volume bridge.");
        }
    }
}
