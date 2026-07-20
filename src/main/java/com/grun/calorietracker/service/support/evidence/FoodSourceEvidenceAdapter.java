package com.grun.calorietracker.service.support.evidence;

import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;

public interface FoodSourceEvidenceAdapter {
    boolean supports(FoodDataSource source);
    int confidence(FoodDataSource source);
    int staleAfterDays();
    int precedence(FoodCatalogType catalogType, FoodDataSource source);
}
