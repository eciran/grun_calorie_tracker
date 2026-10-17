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
    void exactProductMlGramPairAllowsAbsoluteMlWithoutRelabelingItsMassBasis() {
        var product = new FoodItemEntity();
        product.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100G);
        var option = new FoodItemServingOptionEntity();
        option.setQuantity(1.0); option.setGramWeight(1.0); option.setMlVolume(1.0);
        var result = FoodPortionCalculator.normalize(250.0, FoodPortionUnit.MILLILITER, product, option);
        assertEquals(250.0, result.grams());
        assertEquals(250.0, result.milliliters());
        assertEquals(250.0, result.nutritionReferenceAmount());
        assertEquals(FoodNutritionReferenceUnit.PER_100G, product.getNutritionReferenceUnit());
        assertThrows(IllegalArgumentException.class,
                () -> FoodPortionCalculator.normalize(250.0, FoodPortionUnit.MILLILITER, product, null));
    }

    @Test
    void selectedNamedPortionDoesNotApplySourceCountOrFractionTwice() {
        var product = new FoodItemEntity();
        product.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100G);
        for (double[] source : new double[][] {{.5, 59}, {.25, 29.5}, {3, 85}, {4, 113}, {.5, 40}}) {
            var option = new FoodItemServingOptionEntity();
            option.setQuantity(source[0]); option.setGramWeight(source[1]);
            assertEquals(source[1], FoodPortionCalculator.normalize(1.0, FoodPortionUnit.SERVING, product, option).grams());
            assertEquals(2 * source[1], FoodPortionCalculator.normalize(2.0, FoodPortionUnit.SERVING, product, option).grams());
            assertThrows(IllegalArgumentException.class,
                    () -> FoodPortionCalculator.normalize(100.0, FoodPortionUnit.MILLILITER, product, option));
        }
    }

    @Test
    void reviewedSourceMeasuresScaleFractionsWithoutInventingVolume() {
        var product = new FoodItemEntity();
        product.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100G);
        for (double grams : new double[] {15, 243, 46, 61, 79, 28.35, 245, 6.5, 244, 151, 243, 244, 155}) {
            var option = new FoodItemServingOptionEntity();
            option.setQuantity(1.0);
            option.setGramWeight(grams);
            for (double count : new double[] {.25, .5, 1, 2}) {
                var result = FoodPortionCalculator.normalize(count, FoodPortionUnit.SERVING, product, option);
                assertEquals(count * grams, result.grams(), .000001);
                assertEquals(count * grams, result.nutritionReferenceAmount(), .000001);
                assertEquals(null, result.milliliters());
            }
            assertThrows(IllegalArgumentException.class,
                    () -> FoodPortionCalculator.normalize(200.0, FoodPortionUnit.MILLILITER, product, option));
        }
    }

    @Test
    void legacyGramHelperNeverRelabelsMillilitersAsGrams() {
        assertThrows(IllegalArgumentException.class,
                () -> FoodPortionCalculator.normalizeToGrams(250.0, FoodPortionUnit.MILLILITER, null));
        var option = new FoodItemServingOptionEntity();
        option.setMlVolume(250.0);
        assertThrows(IllegalArgumentException.class,
                () -> FoodPortionCalculator.normalizeToGrams(1.0, FoodPortionUnit.SERVING, null, option));
    }

    @Test
    void normalizationRejectsInvalidQuantitiesConversionsAndOverflow() {
        var option = new FoodItemServingOptionEntity();
        option.setGramWeight(100.0);
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0}) {
            assertThrows(IllegalArgumentException.class,
                    () -> FoodPortionCalculator.normalize(invalid, FoodPortionUnit.GRAM, null, null));
            assertThrows(IllegalArgumentException.class,
                    () -> FoodPortionCalculator.normalizeToGrams(invalid, FoodPortionUnit.GRAM, null));
        }
        option.setMlVolume(Double.NaN);
        assertThrows(IllegalArgumentException.class,
                () -> FoodPortionCalculator.normalize(1.0, FoodPortionUnit.SERVING, null, option));
        option.setMlVolume(null);
        assertThrows(IllegalArgumentException.class,
                () -> FoodPortionCalculator.normalize(Double.MAX_VALUE, FoodPortionUnit.SERVING, null, option));
    }

    @Test
    void customMlUsesSelectedExplicitPairNotAUniversalDensityOrServingCount() {
        var product = new FoodItemEntity();
        product.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100G);
        var option = new FoodItemServingOptionEntity();
        option.setUnitType(FoodServingOptionUnit.GLASS);
        option.setGramWeight(257.5);
        option.setMlVolume(250.0);
        FoodPortionCalculator.validateServingOptionUnit(FoodPortionUnit.MILLILITER, option);
        var result = FoodPortionCalculator.normalize(200.0, FoodPortionUnit.MILLILITER, product, option);
        assertEquals(206.0, result.grams(), 0.000001);
        assertEquals(200.0, result.milliliters());
        assertEquals(206.0, result.nutritionReferenceAmount(), 0.000001);
        assertEquals(206.0, FoodPortionCalculator.normalizeToGrams(200.0, FoodPortionUnit.MILLILITER, product, option), 0.000001);
        product.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100ML);
        var reverse = FoodPortionCalculator.normalize(206.0, FoodPortionUnit.GRAM, product, option);
        assertEquals(200.0, reverse.nutritionReferenceAmount(), 0.000001);
        option.setMlVolume(null);
        assertThrows(IllegalArgumentException.class, () -> FoodPortionCalculator.normalize(200.0, FoodPortionUnit.MILLILITER, product, option));
    }

    @Test
    void fractionalSourceMeasureIsOneSelectedOptionNotASecondQuantityMultiplier() {
        var sourceQuarterCup = new FoodItemServingOptionEntity();
        sourceQuarterCup.setQuantity(0.25);
        sourceQuarterCup.setGramWeight(48.0);
        assertEquals(48.0, FoodPortionCalculator.normalize(1.0, FoodPortionUnit.SERVING, null, sourceQuarterCup).grams());
        assertEquals(24.0, FoodPortionCalculator.normalize(0.5, FoodPortionUnit.SERVING, null, sourceQuarterCup).grams());
    }

    @Test
    void explicitMassVolumeBridgeRetainsBothDimensionsAndScalesFractions() {
        var product = new FoodItemEntity();
        product.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100G);
        var option = new FoodItemServingOptionEntity();
        option.setGramWeight(257.5);
        option.setMlVolume(250.0);
        var result = FoodPortionCalculator.normalize(0.5, FoodPortionUnit.SERVING, product, option);
        assertEquals(128.75, result.grams());
        assertEquals(125.0, result.milliliters());
        assertEquals(128.75, result.nutritionReferenceAmount());
    }

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
