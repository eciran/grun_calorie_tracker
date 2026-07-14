package com.grun.calorietracker.service.support.evidence;

import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;

import java.util.EnumSet;
import java.util.Set;

public final class CuratedFoodSourceEvidenceAdapter implements FoodSourceEvidenceAdapter {
    private static final Set<FoodDataSource> SOURCES = EnumSet.of(
            FoodDataSource.LOCAL_CURATED,
            FoodDataSource.ADMIN_IMPORT,
            FoodDataSource.MANUAL
    );

    @Override
    public boolean supports(FoodDataSource source) {
        return SOURCES.contains(source);
    }

    @Override
    public int confidence(FoodDataSource source) {
        return source == FoodDataSource.LOCAL_CURATED ? 98 : 90;
    }

    @Override
    public int staleAfterDays() {
        return 365;
    }

    @Override
    public int precedence(FoodCatalogType catalogType, FoodDataSource source) {
        if (catalogType == FoodCatalogType.LOCAL_DISH) {
            return 0;
        }
        if (catalogType == FoodCatalogType.GENERIC_INGREDIENT) {
            return source == FoodDataSource.LOCAL_CURATED ? 1 : 2;
        }
        return 1;
    }
}
