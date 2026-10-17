package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.dto.FoodExternalSearchResult;

import java.util.List;
import java.util.Optional;

public interface OpenFoodFactsService {
    List<FoodProductDto> searchProducts(String query);
    Optional<FoodProductDto> getProductByBarcode(String barcode);
    List<FoodProductDto> searchProductsByCriteria(FoodSearchCriteriaDto criteria);
    default FoodExternalSearchResult searchProductsOutcome(FoodSearchCriteriaDto criteria) {
        List<FoodProductDto> products = searchProductsByCriteria(criteria);
        return products == null ? FoodExternalSearchResult.unavailable() : FoodExternalSearchResult.success(products);
    }
}
