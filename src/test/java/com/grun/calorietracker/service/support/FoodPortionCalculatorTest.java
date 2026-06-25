package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoodPortionCalculatorTest {

    @Test
    void normalizeToGrams_whenTablespoonProvided_usesFifteenGramEquivalent() {
        Double grams = FoodPortionCalculator.normalizeToGrams(2.0, FoodPortionUnit.TABLESPOON, null);

        assertEquals(30.0, grams);
    }

    @Test
    void normalizeToGrams_whenTeaspoonProvided_usesFiveGramEquivalent() {
        Double grams = FoodPortionCalculator.normalizeToGrams(3.0, FoodPortionUnit.TEASPOON, null);

        assertEquals(15.0, grams);
    }

    @Test
    void normalizeToGrams_whenSliceProvided_usesFoodServingSizeLikePiece() {
        FoodItemEntity food = new FoodItemEntity();
        food.setServingSizeGrams(28.0);

        Double grams = FoodPortionCalculator.normalizeToGrams(2.0, FoodPortionUnit.SLICE, food);

        assertEquals(56.0, grams);
    }
}