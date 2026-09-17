package com.grun.calorietracker.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import com.grun.calorietracker.service.support.ManualContributionNutrition;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
class ManualContributionNutritionTest {
 @Test void legacyDefaultsAndNewCandidatePreserveUnits() {
   assertEquals(FoodNutritionReferenceUnit.PER_100G, ManualContributionNutrition.reference(Map.of()));
   var food = new FoodItemEntity();
   ManualContributionNutrition.apply(food, Map.of("nutritionReferenceUnit", "PER_100ML", "calcium", 120.0, "vitaminD", 2.5, "transFat", 0.0));
   assertEquals(FoodNutritionReferenceUnit.PER_100ML, food.getNutritionReferenceUnit());
   assertEquals(120.0, food.getCalcium()); assertEquals(2.5, food.getVitaminD());
   assertEquals(0.0, food.getTransFat()); assertNull(food.getIron());
 }
 @Test void rejectsInvalidValuesBeforePersistence() {
   for (Object value : new Object[]{-1.0, Double.NaN, Double.POSITIVE_INFINITY, "12", 100001.0})
     assertThrows(IllegalArgumentException.class, () -> ManualContributionNutrition.validate(Map.of("calcium", value)));
   assertThrows(IllegalArgumentException.class, () -> ManualContributionNutrition.validate(Map.of("nutritionReferenceUnit", "LITER")));
   assertThrows(IllegalArgumentException.class, () -> ManualContributionNutrition.validate(Map.of("servingAmount", 0)));
 }
}
