package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodServingOptionDto;

import java.util.List;

public interface FoodServingOptionService {

    List<FoodServingOptionDto> getServingOptions(Long foodItemId, String email);
}
