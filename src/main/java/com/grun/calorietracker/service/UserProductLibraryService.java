package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.CustomFoodRequestDto;
import com.grun.calorietracker.enums.PreferredLanguage;

import java.util.List;

public interface UserProductLibraryService {
    default List<FoodProductDto> getRecentProducts(String email, int limit) {
        return getRecentProducts(email, limit, null);
    }
    List<FoodProductDto> getRecentProducts(String email, int limit, PreferredLanguage language);
    void clearRecentProducts(String email);
    List<FoodProductDto> getFavoriteProducts(String email, int page, int size);
    FoodProductDto addFavoriteProduct(String email, Long productId);
    void removeFavoriteProduct(String email, Long productId);
    FoodProductDto createCustomFood(String email, CustomFoodRequestDto request);
    FoodProductDto updateCustomFood(String email, Long productId, CustomFoodRequestDto request);
    void deleteCustomFood(String email, Long productId);
    List<FoodProductDto> getCustomFoods(String email, int page, int size);
}
