package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoodPortionUnitResolverTest {

    @Test
    void namedOptionsMustSupplyTheRequiredReferenceDimension() {
        FoodItemEntity food = new FoodItemEntity();
        food.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100ML);
        var option = new com.grun.calorietracker.entity.FoodItemServingOptionEntity();
        option.setUnitType(com.grun.calorietracker.enums.FoodServingOptionUnit.SERVING);
        option.setGramWeight(100.0);
        assertEquals(false, FoodPortionUnitResolver.supportsOption(food, option));
        option.setMlVolume(200.0);
        assertEquals(true, FoodPortionUnitResolver.supportsOption(food, option));
        option.setMlVolume(Double.NaN);
        assertEquals(false, FoodPortionUnitResolver.supportsOption(food, option));
    }

    @Test
    void defaultUnit_whenNameContainsTeaOnlyInsideSteak_usesGrams() {
        FoodItemEntity steak = new FoodItemEntity();
        steak.setName("Ribeye Steak");

        assertEquals(FoodPortionUnit.GRAM, FoodPortionUnitResolver.defaultUnit(steak));
    }

    @Test
    void defaultUnit_whenReferenceIsMilliliters_usesMilliliters() {
        FoodItemEntity tea = new FoodItemEntity();
        tea.setName("Iced Tea");
        tea.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100ML);

        assertEquals(FoodPortionUnit.MILLILITER, FoodPortionUnitResolver.defaultUnit(tea));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Cream of Rice Cooked with Water", "Alpro Almond Milk", "Iced Tea",
            "Milk Chocolate", "Egg", "Bread", "Olive Oil"})
    void namesNeverInventConversions(String name) {
        FoodItemEntity food = new FoodItemEntity();
        food.setName(name);
        food.setServingSizeGrams(null);
        food.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100G);
        assertEquals(List.of(FoodPortionUnit.GRAM), FoodPortionUnitResolver.allowedUnits(food));
    }

    @Test
    void milliliterReferenceDoesNotAdvertiseGramBasedServing() {
        FoodItemEntity food = new FoodItemEntity();
        food.setServingUnit("GRAM");
        food.setServingSizeGrams(100.0);
        food.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100ML);
        assertEquals(List.of(FoodPortionUnit.MILLILITER), FoodPortionUnitResolver.allowedUnits(food));
        assertEquals(330.0, FoodPortionCalculator.normalize(330.0,
                FoodPortionUnitResolver.defaultUnit(food), food, null).nutritionReferenceAmount());
    }

    @Test
    void everyAdvertisedLegacyUnitCanBeCalculated() {
        FoodItemEntity food = new FoodItemEntity();
        food.setServingSizeGrams(125.0);
        food.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100G);
        assertEquals(List.of(FoodPortionUnit.GRAM, FoodPortionUnit.SERVING),
                FoodPortionUnitResolver.allowedUnits(food));
        for (FoodPortionUnit unit : FoodPortionUnitResolver.allowedUnits(food)) {
            FoodPortionCalculator.normalize(1.0, unit, food, null);
        }
    }
}
