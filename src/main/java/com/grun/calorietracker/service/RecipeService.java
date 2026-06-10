package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.RecipeDto;
import com.grun.calorietracker.dto.RecipeInteractionDto;
import com.grun.calorietracker.dto.RecipeInteractionRequestDto;
import com.grun.calorietracker.dto.RecipePageDto;
import com.grun.calorietracker.dto.RecipeRequestDto;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.RecipeCategory;

import java.util.List;
import java.util.Set;

public interface RecipeService {
    RecipeDto createRecipe(String email, RecipeRequestDto request);

    List<RecipeDto> getMyRecipes(String email, String query, String mealType);

    RecipeDto getRecipe(String email, Long recipeId);

    RecipeDto updateRecipe(String email, Long recipeId, RecipeRequestDto request);

    void archiveRecipe(String email, Long recipeId);

    RecipeDto requestPublication(String email, Long recipeId);

    RecipePageDto getPublicRecipes(String email,
                                   String query,
                                   String mealType,
                                   MarketRegion marketRegion,
                                   String language,
                                   Set<RecipeCategory> categories,
                                   int page,
                                   int size);

    RecipeDto getPublicRecipe(String email, Long recipeId);

    RecipeDto copyPublicRecipe(String email, Long recipeId);

    RecipeInteractionDto updateInteraction(String email, Long recipeId, RecipeInteractionRequestDto request);

    void clearInteraction(String email, Long recipeId);

    List<RecipeDto> getSavedRecipes(String email);

    List<RecipeDto> getFavoriteRecipes(String email);
}
