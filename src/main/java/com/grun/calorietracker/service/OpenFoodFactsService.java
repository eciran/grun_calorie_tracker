package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;

import java.util.List;
import java.util.Optional;

public interface OpenFoodFactsService {
    List<FoodProductDto> searchProducts(String query);
    Optional<FoodProductDto> getProductByBarcode(String barcode);
    List<FoodProductDto> searchProductsByCriteria(FoodSearchCriteriaDto criteria);
}
