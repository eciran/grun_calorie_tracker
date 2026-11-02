package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
import com.grun.calorietracker.enums.FoodPortionUnit;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoodEffectiveMeasurementContractTest {

    @Test
    void explicitMlLegacyRowIsSafeForMlLoggingWithoutChangingNutritionValues() {
        FoodItemEntity drink = product("ml", Set.of());

        assertEquals(FoodNutritionReferenceUnit.PER_100ML,
                FoodEffectiveMeasurementContract.referenceUnit(drink));
        assertEquals(FoodPortionUnit.MILLILITER, FoodPortionUnitResolver.defaultUnit(drink));
        assertEquals(250.0, FoodPortionCalculator.normalize(
                250.0, FoodPortionUnit.MILLILITER, drink, null
        ).nutritionReferenceAmount());
    }

    @Test
    void strongLiquidCategoryRepairsMissingMlUnitButFormConflictFailsClosed() {
        FoodItemEntity milk = product("g", Set.of("en:milks"));
        assertEquals(FoodNutritionReferenceUnit.PER_100ML,
                FoodEffectiveMeasurementContract.referenceUnit(milk));

        FoodItemEntity powder = product("ml", Set.of("en:milks", "en:milk-powders"));
        assertEquals(FoodNutritionReferenceUnit.PER_100G,
                FoodEffectiveMeasurementContract.referenceUnit(powder));
        assertEquals(FoodPortionUnit.GRAM, FoodPortionUnitResolver.defaultUnit(powder));
    }

    @Test
    void namesAloneNeverTurnSolidsIntoLiquidsAndNativeReferencesStayUnchanged() {
        FoodItemEntity rice = product("g", Set.of("en:rice-dishes"));
        rice.setName("Rice cooked with water");
        assertEquals(FoodNutritionReferenceUnit.PER_100G,
                FoodEffectiveMeasurementContract.referenceUnit(rice));

        rice.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_SERVING);
        assertEquals(FoodNutritionReferenceUnit.PER_SERVING,
                FoodEffectiveMeasurementContract.referenceUnit(rice));
    }

    private FoodItemEntity product(String servingUnit, Set<String> categories) {
        FoodItemEntity product = new FoodItemEntity();
        product.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100G);
        product.setServingUnit(servingUnit);
        product.setSourceCategoryTags(categories);
        return product;
    }
}
