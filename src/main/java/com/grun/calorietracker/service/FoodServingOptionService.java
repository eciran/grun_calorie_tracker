package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodServingOptionDto;
import com.grun.calorietracker.enums.PreferredLanguage;

import java.util.List;

public interface FoodServingOptionService {

    List<FoodServingOptionDto> getServingOptions(Long foodItemId, String email);
    List<FoodServingOptionDto> getServingOptions(Long foodItemId, String email, PreferredLanguage language);
}
