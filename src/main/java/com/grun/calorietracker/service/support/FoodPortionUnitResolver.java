package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
import com.grun.calorietracker.enums.FoodPortionUnit;
import java.util.List;

public final class FoodPortionUnitResolver {
    private FoodPortionUnitResolver() { }

    public static List<FoodPortionUnit> allowedUnits(FoodItemEntity product) {
        FoodPortionUnit base = defaultUnit(product);
        // Named options carry their own conversion; legacy servings only provide grams.
        if (base == FoodPortionUnit.GRAM && product != null
                && product.getServingSizeGrams() != null
                && Double.isFinite(product.getServingSizeGrams())
                && product.getServingSizeGrams() > 0) {
            return List.of(base, FoodPortionUnit.SERVING);
        }
        return List.of(base);
    }

    public static FoodPortionUnit defaultUnit(FoodItemEntity product) {
        if (product != null && product.getNutritionReferenceUnit() == FoodNutritionReferenceUnit.PER_SERVING) {
            return FoodPortionUnit.SERVING;
        }
        return product != null && product.getNutritionReferenceUnit() == FoodNutritionReferenceUnit.PER_100ML
                ? FoodPortionUnit.MILLILITER : FoodPortionUnit.GRAM;
    }

    public static boolean supportsOption(FoodItemEntity product, FoodItemServingOptionEntity option) {
        if (option == null || option.getUnitType() == null || !positive(option.getQuantity())
                || (option.getGramWeight() != null && !positive(option.getGramWeight()))
                || (option.getMlVolume() != null && !positive(option.getMlVolume()))) return false;
        FoodPortionUnit base = defaultUnit(product);
        if (base == FoodPortionUnit.SERVING) {
            return option.getUnitType() == com.grun.calorietracker.enums.FoodServingOptionUnit.SERVING
                    || option.getUnitType() == com.grun.calorietracker.enums.FoodServingOptionUnit.PORTION
                    || option.getUnitType() == com.grun.calorietracker.enums.FoodServingOptionUnit.PIECE;
        }
        return positive(base == FoodPortionUnit.MILLILITER ? option.getMlVolume() : option.getGramWeight());
    }

    private static boolean positive(Double amount) {
        return amount != null && Double.isFinite(amount) && amount > 0;
    }

    public static List<FoodPortionUnit> allowedOptionUnits(FoodItemEntity product, FoodItemServingOptionEntity option) {
        if (!supportsOption(product, option) || option.getQualityStatus()
                != com.grun.calorietracker.enums.FoodServingOptionQualityStatus.VERIFIED
                || Boolean.FALSE.equals(option.getUserVisible())) return List.of();
        var units = new java.util.ArrayList<FoodPortionUnit>();
        units.add(FoodPortionUnit.SERVING);
        FoodPortionUnit semantic = switch (option.getUnitType()) {
            case PIECE -> FoodPortionUnit.PIECE;
            case SLICE -> FoodPortionUnit.SLICE;
            case TABLESPOON -> FoodPortionUnit.TABLESPOON;
            case TEASPOON -> FoodPortionUnit.TEASPOON;
            default -> null;
        };
        if (semantic != null) units.add(semantic);
        if (positive(option.getGramWeight()) && positive(option.getMlVolume())) {
            units.add(FoodPortionUnit.GRAM);
            units.add(FoodPortionUnit.MILLILITER);
        }
        return List.copyOf(units);
    }
}
