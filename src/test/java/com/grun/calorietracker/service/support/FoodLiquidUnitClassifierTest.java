package com.grun.calorietracker.service.support;

import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoodLiquidUnitClassifierTest {

    @Test
    void strongBeverageCategoryAndVolumeBasisSupportMl() {
        assertEquals(
                FoodLiquidUnitClassifier.Decision.ML_SOURCE_SUPPORTED,
                FoodLiquidUnitClassifier.classify(Set.of("en:beverages", "en:milk-drinks"), FoodNutritionReferenceUnit.PER_100ML)
        );
    }

    @Test
    void beverageWithMassBasisRequiresBridge() {
        assertEquals(
                FoodLiquidUnitClassifier.Decision.MASS_VOLUME_BRIDGE_REQUIRED,
                FoodLiquidUnitClassifier.classify(Set.of("en:beverages"), FoodNutritionReferenceUnit.PER_100G)
        );
    }

    @Test
    void preparationAndDessertCategoriesFailClosed() {
        assertEquals(
                FoodLiquidUnitClassifier.Decision.FORM_CONFLICT_REVIEW,
                FoodLiquidUnitClassifier.classify(Set.of("en:beverages", "en:drink-powders"), FoodNutritionReferenceUnit.PER_100ML)
        );
        assertEquals(
                FoodLiquidUnitClassifier.Decision.FORM_CONFLICT_REVIEW,
                FoodLiquidUnitClassifier.classify(Set.of("en:beverages", "en:ice-creams"), FoodNutritionReferenceUnit.PER_100ML)
        );
    }

    @Test
    void kefirDessertAncestryAndSyrupRemainLiquidForms() {
        assertEquals(
                FoodLiquidUnitClassifier.Decision.ML_SOURCE_SUPPORTED,
                FoodLiquidUnitClassifier.classify(Set.of("en:beverages", "en:desserts", "en:dairy-drinks", "en:fermented-drinks"), FoodNutritionReferenceUnit.PER_100ML)
        );
        assertEquals(
                FoodLiquidUnitClassifier.Decision.MASS_VOLUME_BRIDGE_REQUIRED,
                FoodLiquidUnitClassifier.classify(Set.of("en:syrups", "en:desserts"), FoodNutritionReferenceUnit.PER_100G)
        );
    }

    @Test
    void productNameWordsNeverEstablishLiquidForm() {
        assertEquals(
                FoodLiquidUnitClassifier.Decision.NOT_ESTABLISHED,
                FoodLiquidUnitClassifier.classify(Set.of("en:rice-dishes"), FoodNutritionReferenceUnit.PER_100ML)
        );
    }

    @Test
    void umbrellaCategoryIsNeutralAndCoffeeFoodCategoryIsNotLiquid() {
        assertEquals(
                FoodLiquidUnitClassifier.Decision.ML_SOURCE_SUPPORTED,
                FoodLiquidUnitClassifier.classify(Set.of("en:beverages-and-beverages-preparations", "en:beverages"), FoodNutritionReferenceUnit.PER_100ML)
        );
        assertEquals(
                FoodLiquidUnitClassifier.Decision.NOT_ESTABLISHED,
                FoodLiquidUnitClassifier.classify(Set.of("en:coffees"), FoodNutritionReferenceUnit.PER_100ML)
        );
    }
}
