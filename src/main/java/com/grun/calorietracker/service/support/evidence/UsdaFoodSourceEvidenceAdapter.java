package com.grun.calorietracker.service.support.evidence;

import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;

public final class UsdaFoodSourceEvidenceAdapter implements FoodSourceEvidenceAdapter {
    @Override
    public boolean supports(FoodDataSource source) {
        return source == FoodDataSource.USDA_FOODDATA;
    }

    @Override
    public int confidence(FoodDataSource source) {
        return 95;
    }

    @Override
    public int staleAfterDays() {
        return 730;
    }

    @Override
    public int precedence(FoodCatalogType catalogType, FoodDataSource source) {
        if (catalogType == FoodCatalogType.GENERIC_INGREDIENT) {
            return 0;
        }
        if (catalogType == FoodCatalogType.LOCAL_DISH) {
            return 1;
        }
        return 2;
    }
}
