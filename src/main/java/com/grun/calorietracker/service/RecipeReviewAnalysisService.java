package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminRecipeReviewAnalysisDto;

public interface RecipeReviewAnalysisService {
    AdminRecipeReviewAnalysisDto start(Long recipeId, boolean force, String requestedBy);
    AdminRecipeReviewAnalysisDto latest(Long recipeId);
}
