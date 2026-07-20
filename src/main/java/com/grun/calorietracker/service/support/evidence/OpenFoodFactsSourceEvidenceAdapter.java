package com.grun.calorietracker.service.support.evidence;

import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;

public final class OpenFoodFactsSourceEvidenceAdapter implements FoodSourceEvidenceAdapter {
    @Override
    public boolean supports(FoodDataSource source) {
        return source == FoodDataSource.OPEN_FOOD_FACTS;
    }

    @Override
    public int confidence(FoodDataSource source) {
        return 75;
    }

    @Override
    public int staleAfterDays() {
        return 180;
    }

    @Override
    public int precedence(FoodCatalogType catalogType, FoodDataSource source) {
        return catalogType == FoodCatalogType.BRANDED_PRODUCT ? 0 : 3;
    }
}
