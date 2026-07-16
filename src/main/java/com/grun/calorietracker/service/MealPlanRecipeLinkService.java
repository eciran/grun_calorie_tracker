package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.MealPlanRecipeCandidateDto;
import com.grun.calorietracker.dto.MealPlanRecipeLinkDto;

import java.util.List;

public interface MealPlanRecipeLinkService {
    List<MealPlanRecipeCandidateDto> findCandidates(String email, Long planId, Long itemId, int limit);

    MealPlanRecipeLinkDto linkRecipe(String email, Long planId, Long itemId, Long recipeId);

    MealPlanRecipeLinkDto unlinkRecipe(String email, Long planId, Long itemId);
}