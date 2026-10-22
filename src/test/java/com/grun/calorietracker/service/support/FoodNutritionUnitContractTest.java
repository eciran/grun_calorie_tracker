package com.grun.calorietracker.service.support;

import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoodNutritionUnitContractTest {

    @Test
    void normalizesKnownMassAndVolumeAliases() {
        assertEquals("MILLILITER", FoodNutritionUnitContract.normalizeServingUnit("ml"));
        assertEquals("MILLILITER", FoodNutritionUnitContract.normalizeServingUnit("millilitres"));
        assertEquals("GRAM", FoodNutritionUnitContract.normalizeServingUnit("g"));
    }

    @Test
    void rejectsCrossDimensionLabelsWithoutVerifiedBridge() {
        assertThrows(IllegalArgumentException.class,
                () -> FoodNutritionUnitContract.validate(FoodNutritionReferenceUnit.PER_100G, "ml"));
        assertThrows(IllegalArgumentException.class,
                () -> FoodNutritionUnitContract.validate(FoodNutritionReferenceUnit.PER_100ML, "g"));
        assertDoesNotThrow(
                () -> FoodNutritionUnitContract.validate(FoodNutritionReferenceUnit.PER_100ML, "MILLILITER"));
    }
}
