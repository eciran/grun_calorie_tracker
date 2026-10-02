package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminRecipeReviewAnalysisDto;
import com.grun.calorietracker.enums.PreferredLanguage;

public interface RecipeReviewAnalysisService {
    AdminRecipeReviewAnalysisDto start(Long recipeId, boolean force, String requestedBy, PreferredLanguage responseLanguage);
    AdminRecipeReviewAnalysisDto latest(Long recipeId);
}
