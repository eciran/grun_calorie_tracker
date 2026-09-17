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
        return product != null && product.getNutritionReferenceUnit() == FoodNutritionReferenceUnit.PER_100ML
                ? FoodPortionUnit.MILLILITER : FoodPortionUnit.GRAM;
    }

    public static boolean supportsOption(FoodItemEntity product, FoodItemServingOptionEntity option) {
        if (option == null || option.getUnitType() == null) return false;
        Double amount = defaultUnit(product) == FoodPortionUnit.MILLILITER
                ? option.getMlVolume() : option.getGramWeight();
        return amount != null && Double.isFinite(amount) && amount > 0;
    }
}
