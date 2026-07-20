package com.grun.calorietracker.service.support.evidence;

import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;

public final class FallbackFoodSourceEvidenceAdapter implements FoodSourceEvidenceAdapter {
    @Override
    public boolean supports(FoodDataSource source) {
        return true;
    }

    @Override
    public int confidence(FoodDataSource source) {
        return 65;
    }

    @Override
    public int staleAfterDays() {
        return 365;
    }

    @Override
    public int precedence(FoodCatalogType catalogType, FoodDataSource source) {
        return catalogType == FoodCatalogType.BRANDED_PRODUCT ? 2 : 3;
    }
}
