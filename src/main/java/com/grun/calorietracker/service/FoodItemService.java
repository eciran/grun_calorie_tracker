package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodItemEntity;

import java.util.List;
public interface FoodItemService {
    FoodItemEntity getOrSaveFoodItemByBarcode(String barcode);
    FoodProductDto getFoodProductByBarcode(String barcode);
    FoodProductDto getFoodItemById(Long id, String email);
    List<FoodProductDto> searchFoodItems(FoodSearchCriteriaDto criteria);
    FoodProductSearchPageDto searchFoodItems(FoodSearchCriteriaDto criteria, int page, int size);
}
