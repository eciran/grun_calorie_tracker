package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.CatalogClassificationQualityDto;
import com.grun.calorietracker.dto.FoodBrandSummaryDto;
import com.grun.calorietracker.dto.FoodCategoryTreeDto;

import java.util.List;
import java.util.Optional;

public interface CatalogClassificationService {
    List<FoodBrandSummaryDto> autocompleteBrands(String query, int limit);
    Optional<FoodBrandSummaryDto> resolveBrand(String value);
    List<FoodCategoryTreeDto> activeCategoryTree();
    CatalogClassificationQualityDto qualitySummary();
}
