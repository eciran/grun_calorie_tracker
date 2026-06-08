package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.RecipeLogDto;
import com.grun.calorietracker.dto.RecipeLogRequestDto;

import java.time.LocalDateTime;
import java.util.List;

public interface RecipeLogService {
    RecipeLogDto logRecipe(String email, Long recipeId, RecipeLogRequestDto request);

    List<RecipeLogDto> getRecipeLogs(String email, LocalDateTime start, LocalDateTime end);

    RecipeLogDto updateRecipeLog(String email, Long logId, RecipeLogRequestDto request);

    void deleteRecipeLog(String email, Long logId);
}
