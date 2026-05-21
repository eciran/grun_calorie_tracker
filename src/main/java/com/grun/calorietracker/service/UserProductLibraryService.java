package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.CustomFoodRequestDto;

import java.util.List;

public interface UserProductLibraryService {
    List<FoodProductDto> getRecentProducts(String email, int limit);
    List<FoodProductDto> getFavoriteProducts(String email);
    FoodProductDto addFavoriteProduct(String email, Long productId);
    void removeFavoriteProduct(String email, Long productId);
    FoodProductDto createCustomFood(String email, CustomFoodRequestDto request);
    List<FoodProductDto> getCustomFoods(String email);
}
