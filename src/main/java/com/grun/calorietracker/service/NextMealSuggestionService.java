package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.NextMealSuggestionDto;

public interface NextMealSuggestionService {
    NextMealSuggestionDto getNextMeal(String email);
}
