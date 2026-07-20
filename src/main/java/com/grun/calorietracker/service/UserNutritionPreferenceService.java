package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.UserNutritionPreferenceDto;

public interface UserNutritionPreferenceService {

    UserNutritionPreferenceDto get(String email);

    UserNutritionPreferenceDto update(String email, UserNutritionPreferenceDto request);
}
