package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminRecipeDto;
import com.grun.calorietracker.dto.AdminRecipeReviewRequestDto;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.repository.RecipeUserInteractionRepository;
import com.grun.calorietracker.service.impl.AdminRecipeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRecipeServiceImplTest {

    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private RecipeUserInteractionRepository recipeUserInteractionRepository;
    @Mock
    private AdminAuditService adminAuditService;
    @InjectMocks
    private AdminRecipeServiceImpl service;

    @Test
    void updateRecipeReview_updatesStateAndRecordsAudit() {
        RecipeEntity recipe = new RecipeEntity();
        recipe.setId(12L);
        recipe.setName("Admin checked recipe");
        recipe.setVisibility(RecipeVisibility.PRIVATE);
        recipe.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        recipe.setArchived(false);
        AdminRecipeReviewRequestDto request = new AdminRecipeReviewRequestDto();
        request.setVerificationStatus(VerificationStatus.VERIFIED);
        request.setArchived(true);
        request.setReviewNote("Checked from admin UI.");
        when(recipeRepository.findById(12L)).thenReturn(Optional.of(recipe));
        when(recipeRepository.save(recipe)).thenReturn(recipe);

        AdminRecipeDto result = service.updateRecipeReview(12L, request, "admin@grun.local");

        assertEquals(VerificationStatus.VERIFIED, result.getVerificationStatus());
        assertEquals(true, result.getArchived());
        verify(adminAuditService).record(
                eq("admin@grun.local"),
                eq(AdminAuditActionType.RECIPE_REVIEW_UPDATE),
                eq(AdminAuditTargetType.RECIPE),
                eq("12"),
                any(),
                any(),
                eq(null)
        );
    }

    @Test
    void updateRecipeReview_updatesImageModerationState() {
        RecipeEntity recipe = new RecipeEntity();
        recipe.setId(12L);
        recipe.setName("Recipe with image");
        recipe.setVisibility(RecipeVisibility.PRIVATE);
        recipe.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        recipe.setArchived(false);
        recipe.setImageUrl("https://old.example/recipe.jpg");
        recipe.setImageSource(ImageSource.USER_UPLOAD);
        recipe.setImageStatus(ImageStatus.NEEDS_REVIEW);
        AdminRecipeReviewRequestDto request = new AdminRecipeReviewRequestDto();
        request.setImageUrl("https://cdn.grun.app/reviewed/recipe.jpg");
        request.setImageSource(ImageSource.ADMIN_UPLOAD);
        request.setImageStatus(ImageStatus.APPROVED);
        request.setReviewNote("Image approved.");
        when(recipeRepository.findById(12L)).thenReturn(Optional.of(recipe));
        when(recipeRepository.save(recipe)).thenReturn(recipe);

        AdminRecipeDto result = service.updateRecipeReview(12L, request, "admin@grun.local");

        assertEquals("https://cdn.grun.app/reviewed/recipe.jpg", result.getImageUrl());
        assertEquals(ImageSource.ADMIN_UPLOAD, result.getImageSource());
        assertEquals(ImageStatus.APPROVED, result.getImageStatus());
        assertEquals("admin@grun.local", result.getImageReviewedBy());
        verify(adminAuditService).record(
                eq("admin@grun.local"),
                eq(AdminAuditActionType.RECIPE_REVIEW_UPDATE),
                eq(AdminAuditTargetType.RECIPE),
                eq("12"),
                any(),
                any(),
                eq(null)
        );
    }
}
