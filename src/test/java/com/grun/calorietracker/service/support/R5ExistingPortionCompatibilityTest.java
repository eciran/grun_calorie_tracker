package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.FoodServingOptionQualityStatus;
import com.grun.calorietracker.enums.FoodServingOptionUnit;
import com.grun.calorietracker.mapper.FoodServingOptionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class R5ExistingPortionCompatibilityTest {
    private FoodItemServingOptionEntity option(FoodServingOptionUnit type) {
        var food = new FoodItemEntity();
        food.setId(180991L);
        food.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100G);
        var option = new FoodItemServingOptionEntity();
        option.setId(3708L);
        option.setFoodItem(food);
        option.setUnitType(type);
        option.setQuantity(1.0);
        option.setGramWeight(28.35);
        option.setMlVolume(null);
        option.setLabel("1 oz portion (28.35 g)");
        option.setQualityStatus(FoodServingOptionQualityStatus.VERIFIED);
        option.setUserVisible(true);
        option.setIsDefault(true);
        return option;
    }

    @Test
    void measuredPortionAndSourceServingHaveSameNamedCountConsumptionWithoutRetyping() {
        var current = option(FoodServingOptionUnit.PORTION);
        var sourceFixture = option(FoodServingOptionUnit.SERVING);
        for (double count : new double[] {.25, .5, 1, 2}) {
            for (var option : List.of(current, sourceFixture)) {
                FoodPortionCalculator.validateServingOptionUnit(FoodPortionUnit.SERVING, option);
                var normalized = FoodPortionCalculator.normalize(count, FoodPortionUnit.SERVING, option.getFoodItem(), option);
                assertEquals(count * 28.35, normalized.grams(), .000001);
                assertEquals(count * 28.35, normalized.nutritionReferenceAmount(), .000001);
                assertEquals(null, normalized.milliliters());
                assertEquals(List.of(FoodPortionUnit.SERVING), FoodServingOptionMapper.toDto(option).getAllowedInputUnits());
            }
        }
        assertEquals(FoodServingOptionUnit.PORTION, current.getUnitType());
        assertNotEquals(current.getUnitType(), sourceFixture.getUnitType());
    }

    @Test
    void bothTypesRejectUnprovenPhysicalAndDifferentSemanticUnits() {
        for (var type : List.of(FoodServingOptionUnit.PORTION, FoodServingOptionUnit.SERVING)) {
            var option = option(type);
            for (var unit : FoodPortionUnit.values()) {
                if (unit != FoodPortionUnit.SERVING) {
                    assertThrows(IllegalArgumentException.class,
                            () -> FoodPortionCalculator.validateServingOptionUnit(unit, option));
                }
            }
            assertThrows(IllegalArgumentException.class, () -> FoodPortionCalculator.normalize(
                    100.0, FoodPortionUnit.MILLILITER, option.getFoodItem(), option));
        }
    }

    @Test
    void sameSourceWeightDoesNotPublishAnUnapprovedCandidate() {
        var current = option(FoodServingOptionUnit.PORTION);
        var sourceFixture = option(FoodServingOptionUnit.SERVING);
        sourceFixture.setQualityStatus(FoodServingOptionQualityStatus.NEEDS_REVIEW);
        sourceFixture.setUserVisible(false);
        sourceFixture.setIsDefault(false);
        assertEquals(List.of(), FoodServingOptionMapper.toDto(sourceFixture).getAllowedInputUnits());
        assertEquals(List.of(FoodPortionUnit.SERVING), FoodServingOptionMapper.toDto(current).getAllowedInputUnits());
        assertEquals(3708L, current.getId());
        assertEquals(true, current.getUserVisible());
        assertEquals(true, current.getIsDefault());
    }

    @Test
    void explicitWaterSourcePairSupportsMlOnlyAfterItsSeparateOptionApproval() {
        var fixture = option(FoodServingOptionUnit.SERVING);
        fixture.getFoodItem().setId(49697L);
        fixture.setGramWeight(1.0); fixture.setMlVolume(1.0); fixture.setLabel("1 ml portion (1 g)");
        fixture.setQualityStatus(FoodServingOptionQualityStatus.NEEDS_REVIEW); fixture.setUserVisible(false);
        assertEquals(List.of(), FoodServingOptionMapper.toDto(fixture).getAllowedInputUnits());
        // Simulated approval only in this isolated consumer contract; no real option is published.
        fixture.setQualityStatus(FoodServingOptionQualityStatus.VERIFIED); fixture.setUserVisible(true);
        for (double ml : new double[] {1, 150, 250}) {
            var normalized = FoodPortionCalculator.normalize(ml, FoodPortionUnit.MILLILITER, fixture.getFoodItem(), fixture);
            assertEquals(ml, normalized.grams(), .000001);
            assertEquals(ml, normalized.milliliters(), .000001);
            assertEquals(ml, normalized.nutritionReferenceAmount(), .000001);
        }
        fixture.setGramWeight(null);
        assertThrows(IllegalArgumentException.class, () -> FoodPortionCalculator.normalize(
                250.0, FoodPortionUnit.MILLILITER, fixture.getFoodItem(), fixture));
    }
}
