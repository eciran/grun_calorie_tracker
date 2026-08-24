package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
import com.grun.calorietracker.enums.FoodServingOptionUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoodPortionCalculatorTest {

    @Test
    void normalizeToGrams_whenTablespoonHasNoProductConversion_rejectsUnsafeFallback() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> FoodPortionCalculator.normalizeToGrams(2.0, FoodPortionUnit.TABLESPOON, null)
        );

        assertEquals(
                "TABLESPOON requires a product-specific serving option with a verified weight or volume.",
                error.getMessage()
        );
    }

    @Test
    void normalizeToGrams_whenTeaspoonHasNoProductConversion_rejectsUnsafeFallback() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FoodPortionCalculator.normalizeToGrams(3.0, FoodPortionUnit.TEASPOON, null)
        );
    }

    @Test
    void normalizeToGrams_whenSliceProvided_usesFoodServingSizeLikePiece() {
        FoodItemEntity food = new FoodItemEntity();
        food.setServingSizeGrams(28.0);

        Double grams = FoodPortionCalculator.normalizeToGrams(2.0, FoodPortionUnit.SLICE, food);

        assertEquals(56.0, grams);
    }

    @Test
    void normalizeToGrams_whenServingSizeIsMissing_rejectsHundredGramFallback() {
        FoodItemEntity food = new FoodItemEntity();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> FoodPortionCalculator.normalizeToGrams(1.25, FoodPortionUnit.SERVING, food)
        );

        assertEquals(
                "SERVING requires a product-specific serving option or a positive serving size in grams.",
                error.getMessage()
        );
    }

    @Test
    void normalizeToGrams_whenTablespoonServingOptionProvided_usesProductSpecificWeight() {
        FoodItemServingOptionEntity tablespoon = new FoodItemServingOptionEntity();
        tablespoon.setGramWeight(18.0);

        Double grams = FoodPortionCalculator.normalizeToGrams(
                2.0,
                FoodPortionUnit.TABLESPOON,
                new FoodItemEntity(),
                tablespoon
        );

        assertEquals(36.0, grams);
    }

    @Test
    void normalize_whenPerHundredMlProductUsesMilliliters_preservesVolumeWithoutPretendingItIsGrams() {
        FoodItemEntity drink = new FoodItemEntity();
        drink.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100ML);

        NormalizedFoodPortion normalized = FoodPortionCalculator.normalize(
                330.0, FoodPortionUnit.MILLILITER, drink, null
        );

        assertEquals(null, normalized.grams());
        assertEquals(330.0, normalized.milliliters());
        assertEquals(330.0, normalized.nutritionReferenceAmount());
    }

    @Test
    void normalize_whenPerHundredGramProductReceivesMillilitersWithoutDensity_rejectsUnsafeConversion() {
        FoodItemEntity product = new FoodItemEntity();
        product.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100G);

        assertThrows(IllegalArgumentException.class, () -> FoodPortionCalculator.normalize(
                250.0, FoodPortionUnit.MILLILITER, product, null
        ));
    }

    @Test
    void validateServingOptionUnit_whenGenericServingSelected_acceptsProductSpecificOption() {
        FoodItemServingOptionEntity slice = new FoodItemServingOptionEntity();
        slice.setUnitType(FoodServingOptionUnit.SLICE);

        FoodPortionCalculator.validateServingOptionUnit(FoodPortionUnit.SERVING, slice);
    }

    @Test
    void validateServingOptionUnit_whenExactSemanticUnitMatches_acceptsOption() {
        FoodItemServingOptionEntity tablespoon = new FoodItemServingOptionEntity();
        tablespoon.setUnitType(FoodServingOptionUnit.TABLESPOON);

        FoodPortionCalculator.validateServingOptionUnit(FoodPortionUnit.TABLESPOON, tablespoon);
    }

    @Test
    void validateServingOptionUnit_whenSemanticUnitsConflict_rejectsOption() {
        FoodItemServingOptionEntity bottle = new FoodItemServingOptionEntity();
        bottle.setUnitType(FoodServingOptionUnit.BOTTLE);

        assertThrows(
                IllegalArgumentException.class,
                () -> FoodPortionCalculator.validateServingOptionUnit(FoodPortionUnit.SLICE, bottle)
        );
    }

    @Test
    void validateServingOptionUnit_whenBaseUnitIncludesServingOption_rejectsAmbiguousRequest() {
        FoodItemServingOptionEntity piece = new FoodItemServingOptionEntity();
        piece.setUnitType(FoodServingOptionUnit.PIECE);

        assertThrows(
                IllegalArgumentException.class,
                () -> FoodPortionCalculator.validateServingOptionUnit(FoodPortionUnit.GRAM, piece)
        );
    }
}
