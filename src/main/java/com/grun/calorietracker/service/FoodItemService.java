package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodItemEntity;

import java.util.List;
import java.util.Optional;

public interface FoodItemService {
    FoodItemEntity getOrSaveFoodItemByBarcode(String barcode);
    List<FoodProductDto> searchFoodItems(FoodSearchCriteriaDto criteria);
}
