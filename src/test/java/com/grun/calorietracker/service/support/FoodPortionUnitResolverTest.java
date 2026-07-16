package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoodPortionUnitResolverTest {

    @Test
    void defaultUnit_whenNameContainsTeaOnlyInsideSteak_usesGrams() {
        FoodItemEntity steak = new FoodItemEntity();
        steak.setName("Ribeye Steak");

        assertEquals(FoodPortionUnit.GRAM, FoodPortionUnitResolver.defaultUnit(steak));
    }

    @Test
    void defaultUnit_whenNameContainsTeaAsAWord_usesMilliliters() {
        FoodItemEntity tea = new FoodItemEntity();
        tea.setName("Iced Tea");

        assertEquals(FoodPortionUnit.MILLILITER, FoodPortionUnitResolver.defaultUnit(tea));
    }
}