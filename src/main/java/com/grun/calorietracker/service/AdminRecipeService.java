package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminRecipeDto;
import com.grun.calorietracker.dto.AdminRecipePageDto;
import com.grun.calorietracker.dto.AdminRecipeReviewRequestDto;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.VerificationStatus;

public interface AdminRecipeService {
    AdminRecipePageDto listRecipes(String query,
                                   VerificationStatus verificationStatus,
                                   RecipeVisibility visibility,
                                   Boolean archived,
                                   String ownerEmail,
                                   String mealType,
                                   MarketRegion marketRegion,
                                   ImageStatus imageStatus,
                                   ImageSource imageSource,
                                   int page,
                                   int size);

    AdminRecipeDto getRecipe(Long id);

    AdminRecipeDto updateRecipeReview(Long id, AdminRecipeReviewRequestDto request, String adminEmail);
}
