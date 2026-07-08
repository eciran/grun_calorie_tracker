package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminRecipeCreateRequestDto;
import com.grun.calorietracker.dto.AdminRecipeDto;
import com.grun.calorietracker.dto.AdminRecipeImportBatchRequestDto;
import com.grun.calorietracker.dto.AdminRecipeImportCandidateDto;
import com.grun.calorietracker.dto.AdminRecipeImportIngredientUpdateRequestDto;
import com.grun.calorietracker.dto.AdminRecipeImportCandidatePageDto;
import com.grun.calorietracker.dto.AdminRecipeImportResultDto;
import com.grun.calorietracker.dto.AdminRecipeImportReviewRequestDto;
import com.grun.calorietracker.dto.AdminRecipePageDto;
import com.grun.calorietracker.dto.AdminRecipeReviewRequestDto;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.RecipeAllergen;
import com.grun.calorietracker.enums.RecipeImportCandidateStatus;
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
                                   RecipeAllergen allergen,
                                   int page,
                                   int size);

    AdminRecipeDto getRecipe(Long id);

    AdminRecipeDto createRecipe(AdminRecipeCreateRequestDto request, String adminEmail);

    AdminRecipeDto updateRecipeReview(Long id, AdminRecipeReviewRequestDto request, String adminEmail);

    void archiveRecipe(Long id, String adminEmail);

    AdminRecipeImportResultDto importRecipeCandidates(AdminRecipeImportBatchRequestDto request, String adminEmail);

    AdminRecipeImportCandidatePageDto listImportCandidates(RecipeImportCandidateStatus status, String batchId, int page, int size);

    AdminRecipeDto approveImportCandidate(Long id, AdminRecipeImportReviewRequestDto request, String adminEmail);

    AdminRecipeImportCandidateDto rejectImportCandidate(Long id, AdminRecipeImportReviewRequestDto request, String adminEmail);

    AdminRecipeImportCandidateDto updateImportCandidateIngredient(Long id, int ingredientIndex, AdminRecipeImportIngredientUpdateRequestDto request, String adminEmail);
}
