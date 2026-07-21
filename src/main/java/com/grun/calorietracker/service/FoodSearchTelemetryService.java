package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;

public interface FoodSearchTelemetryService {
    String recordSearch(FoodSearchCriteriaDto criteria, FoodProductSearchPageDto result);
    void recordSelection(String searchRequestId, Long foodItemId, int rank);
}