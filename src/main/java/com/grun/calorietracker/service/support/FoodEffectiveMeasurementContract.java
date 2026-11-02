package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;

import java.util.Set;

/** Resolves legacy liquid rows without using product-name guesses or universal density. */
public final class FoodEffectiveMeasurementContract {

    private FoodEffectiveMeasurementContract() {
    }

    public static FoodNutritionReferenceUnit referenceUnit(FoodItemEntity product) {
        if (product == null) {
            return FoodNutritionReferenceUnit.PER_100G;
        }
        return referenceUnit(
                product.getNutritionReferenceUnit(),
                product.getServingUnit(),
                product.getSourceCategoryTags()
        );
    }

    public static FoodNutritionReferenceUnit referenceUnit(
            FoodNutritionReferenceUnit storedReference,
            String servingUnit,
            Set<String> sourceCategoryTags
    ) {
        FoodNutritionReferenceUnit reference = storedReference == null
                ? FoodNutritionReferenceUnit.PER_100G
                : storedReference;
        if (reference != FoodNutritionReferenceUnit.PER_100G) {
            return reference;
        }

        FoodLiquidUnitClassifier.Decision liquid = FoodLiquidUnitClassifier.classify(
                sourceCategoryTags, reference
        );
        if (liquid == FoodLiquidUnitClassifier.Decision.FORM_CONFLICT_REVIEW) {
            return reference;
        }
        boolean explicitMl = "MILLILITER".equals(FoodNutritionUnitContract.normalizeServingUnit(servingUnit));
        boolean strongLiquidCategory = liquid == FoodLiquidUnitClassifier.Decision.MASS_VOLUME_BRIDGE_REQUIRED;
        return explicitMl || strongLiquidCategory
                ? FoodNutritionReferenceUnit.PER_100ML
                : reference;
    }
}
