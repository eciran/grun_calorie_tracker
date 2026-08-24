package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
import com.grun.calorietracker.enums.FoodServingOptionUnit;

public final class FoodPortionCalculator {

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
            return portionSize * requireServingSizeGrams(foodItem, resolvedUnit);
        }
        if (resolvedUnit == FoodPortionUnit.TABLESPOON || resolvedUnit == FoodPortionUnit.TEASPOON) {
            throw new IllegalArgumentException(
                    resolvedUnit + " requires a product-specific serving option with a verified weight or volume."
            );
        }
        return portionSize;
    }

    public static NormalizedFoodPortion normalize(
            Double portionSize,
            FoodPortionUnit portionUnit,
            FoodItemEntity foodItem,
            FoodItemServingOptionEntity servingOption
    ) {
        if (portionSize == null) {
            return new NormalizedFoodPortion(null, null, null);
        }
        FoodNutritionReferenceUnit referenceUnit = foodItem != null && foodItem.getNutritionReferenceUnit() != null
                ? foodItem.getNutritionReferenceUnit()
                : FoodNutritionReferenceUnit.PER_100G;
        Double grams = null;
        Double milliliters = null;
        if (servingOption != null) {
            grams = positiveScaled(portionSize, servingOption.getGramWeight());
            milliliters = positiveScaled(portionSize, servingOption.getMlVolume());
        } else {
            FoodPortionUnit resolvedUnit = resolveUnit(portionUnit);
            if (resolvedUnit == FoodPortionUnit.GRAM) {
                grams = portionSize;
            } else if (resolvedUnit == FoodPortionUnit.MILLILITER) {
                milliliters = portionSize;
            } else if (resolvedUnit == FoodPortionUnit.SERVING
                    || resolvedUnit == FoodPortionUnit.PIECE
                    || resolvedUnit == FoodPortionUnit.SLICE) {
                grams = portionSize * requireServingSizeGrams(foodItem, resolvedUnit);
            } else {
                throw new IllegalArgumentException(
                        resolvedUnit + " requires a product-specific serving option with a verified weight or volume."
                );
            }
        }
        Double referenceAmount = referenceUnit == FoodNutritionReferenceUnit.PER_100ML ? milliliters : grams;
        if (referenceAmount == null) {
            throw new IllegalArgumentException(
                    "Portion conversion does not provide the product's required nutrition reference unit " + referenceUnit + "."
            );
        }
        return new NormalizedFoodPortion(grams, milliliters, referenceAmount);
    }

    public static void validateServingOptionUnit(
            FoodPortionUnit portionUnit,
            FoodItemServingOptionEntity servingOption
    ) {
        if (servingOption == null) {
            return;
        }
        FoodPortionUnit resolvedUnit = resolveUnit(portionUnit);
        FoodServingOptionUnit optionUnit = servingOption.getUnitType();
        if (optionUnit == null) {
            throw new IllegalArgumentException("Serving option must define a unit type.");
        }
        if (resolvedUnit == FoodPortionUnit.SERVING) {
            return;
        }
        boolean compatible = switch (resolvedUnit) {
            case PIECE -> optionUnit == FoodServingOptionUnit.PIECE;
            case SLICE -> optionUnit == FoodServingOptionUnit.SLICE;
            case TABLESPOON -> optionUnit == FoodServingOptionUnit.TABLESPOON;
            case TEASPOON -> optionUnit == FoodServingOptionUnit.TEASPOON;
            case GRAM, MILLILITER, SERVING -> false;
        };
        if (!compatible) {
            throw new IllegalArgumentException(
                    "Portion unit " + resolvedUnit + " is not compatible with serving option unit " + optionUnit + "."
            );
        }
    }

    private static Double positiveScaled(Double portionSize, Double conversion) {
        return conversion != null && conversion > 0 ? portionSize * conversion : null;
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

    private static double requireServingSizeGrams(FoodItemEntity foodItem, FoodPortionUnit portionUnit) {
        if (foodItem != null && foodItem.getServingSizeGrams() != null && foodItem.getServingSizeGrams() > 0) {
            return foodItem.getServingSizeGrams();
        }
        throw new IllegalArgumentException(
                portionUnit + " requires a product-specific serving option or a positive serving size in grams."
        );
    }
}
