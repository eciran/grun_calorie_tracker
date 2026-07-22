package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminRecipeDto;
import com.grun.calorietracker.dto.AdminRecipeReviewRequestDto;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.RecipeCategory;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.repository.RecipeUserInteractionRepository;
import com.grun.calorietracker.service.impl.AdminRecipeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
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
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private PushDeliveryService pushDeliveryService;
    @InjectMocks
    private AdminRecipeServiceImpl service;

    @Test
    void updateRecipeReview_updatesStateRecordsAuditAndNotifiesOwner() {
        UserEntity owner = owner();
        RecipeEntity recipe = new RecipeEntity();
        recipe.setId(12L);
        recipe.setName("Admin checked recipe");
        recipe.setOwnerUser(owner);
        makePublicReady(recipe);
        recipe.setVisibility(RecipeVisibility.COMMUNITY_PENDING);
        recipe.setVerificationStatus(VerificationStatus.NEEDS_REVIEW);
        recipe.setArchived(false);

        AdminRecipeReviewRequestDto request = new AdminRecipeReviewRequestDto();
        request.setVerificationStatus(VerificationStatus.VERIFIED);
        request.setVisibility(RecipeVisibility.PUBLIC_ADMIN);
        request.setArchived(false);
        request.setReviewNote("Checked from admin UI.");
        when(recipeRepository.findById(12L)).thenReturn(Optional.of(recipe));
        when(recipeRepository.save(recipe)).thenReturn(recipe);
        when(notificationRepository.save(any(NotificationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminRecipeDto result = service.updateRecipeReview(12L, request, "admin@grun.local");

        assertEquals(VerificationStatus.VERIFIED, result.getVerificationStatus());
        assertEquals(RecipeVisibility.PUBLIC_ADMIN, result.getVisibility());
        assertEquals(false, result.getArchived());
        verify(adminAuditService).record(
                eq("admin@grun.local"),
                eq(AdminAuditActionType.RECIPE_REVIEW_UPDATE),
                eq(AdminAuditTargetType.RECIPE),
                eq("12"),
                any(),
                any(),
                eq(null)
        );
        var notificationCaptor = forClass(NotificationEntity.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertEquals("recipe_review_approved", notificationCaptor.getValue().getType());
        assertEquals("VIEW_RECIPE", notificationCaptor.getValue().getPrimaryAction());
        assertEquals(
                "Your recipe \"Admin checked recipe\" was approved and published.",
                notificationCaptor.getValue().getMessage()
        );
        verify(pushDeliveryService).deliver(notificationCaptor.getValue());
    }

    @Test
    void updateRecipeReview_whenUserSubmissionIsRejected_notifiesOwnerWithReason() {
        RecipeEntity recipe = new RecipeEntity();
        recipe.setId(13L);
        recipe.setName("Recipe needing changes");
        recipe.setOwnerUser(owner());
        recipe.setVisibility(RecipeVisibility.COMMUNITY_PENDING);
        recipe.setVerificationStatus(VerificationStatus.NEEDS_REVIEW);
        recipe.setArchived(false);

        AdminRecipeReviewRequestDto request = new AdminRecipeReviewRequestDto();
        request.setVerificationStatus(VerificationStatus.REJECTED);
        request.setReviewNote("Add exact ingredient quantities.");
        when(recipeRepository.findById(13L)).thenReturn(Optional.of(recipe));
        when(recipeRepository.save(recipe)).thenReturn(recipe);
        when(notificationRepository.save(any(NotificationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminRecipeDto result = service.updateRecipeReview(13L, request, "admin@grun.local");

        assertEquals(RecipeVisibility.PRIVATE, result.getVisibility());
        var notificationCaptor = forClass(NotificationEntity.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertEquals("recipe_review_rejected", notificationCaptor.getValue().getType());
        assertEquals(
                "Your recipe \"Recipe needing changes\" was not approved.",
                notificationCaptor.getValue().getMessage()
        );
        assertEquals("Add exact ingredient quantities.", notificationCaptor.getValue().getNote());
        assertEquals("EDIT_RECIPE", notificationCaptor.getValue().getPrimaryAction());
        verify(pushDeliveryService).deliver(notificationCaptor.getValue());
    }

    @Test
    void updateRecipeReview_whenPublishedRecipeIsRejected_normalizesVisibilityToPrivate() {
        RecipeEntity recipe = new RecipeEntity();
        recipe.setId(14L);
        recipe.setName("Previously public recipe");
        recipe.setVisibility(RecipeVisibility.PUBLIC_ADMIN);
        recipe.setVerificationStatus(VerificationStatus.VERIFIED);
        recipe.setArchived(false);

        AdminRecipeReviewRequestDto request = new AdminRecipeReviewRequestDto();
        request.setVerificationStatus(VerificationStatus.REJECTED);
        request.setVisibility(RecipeVisibility.PUBLIC_ADMIN);
        request.setReviewNote("Removed after catalog review.");
        when(recipeRepository.findById(14L)).thenReturn(Optional.of(recipe));
        when(recipeRepository.save(recipe)).thenReturn(recipe);

        AdminRecipeDto result = service.updateRecipeReview(14L, request, "admin@grun.local");

        assertEquals(VerificationStatus.REJECTED, result.getVerificationStatus());
        assertEquals(RecipeVisibility.PRIVATE, result.getVisibility());
    }
    @Test
    void updateRecipeReview_whenUserSubmissionIsRejectedWithoutReason_rejectsRequest() {
        RecipeEntity recipe = new RecipeEntity();
        recipe.setId(13L);
        recipe.setName("Recipe needing changes");
        recipe.setOwnerUser(owner());
        recipe.setVisibility(RecipeVisibility.COMMUNITY_PENDING);
        recipe.setVerificationStatus(VerificationStatus.NEEDS_REVIEW);

        AdminRecipeReviewRequestDto request = new AdminRecipeReviewRequestDto();
        request.setVerificationStatus(VerificationStatus.REJECTED);
        when(recipeRepository.findById(13L)).thenReturn(Optional.of(recipe));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateRecipeReview(13L, request, "admin@grun.local")
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

    @Test
    void updateRecipeReview_whenPublicApprovalIsIncomplete_rejectsRequest() {
        RecipeEntity recipe = new RecipeEntity();
        recipe.setId(12L);
        recipe.setName("Incomplete public recipe");
        recipe.setVisibility(RecipeVisibility.COMMUNITY_PENDING);
        recipe.setVerificationStatus(VerificationStatus.NEEDS_REVIEW);
        recipe.setArchived(false);

        AdminRecipeReviewRequestDto request = new AdminRecipeReviewRequestDto();
        request.setVisibility(RecipeVisibility.PUBLIC_ADMIN);
        request.setVerificationStatus(VerificationStatus.VERIFIED);
        when(recipeRepository.findById(12L)).thenReturn(Optional.of(recipe));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateRecipeReview(12L, request, "admin@grun.local")
        );
    }

    private UserEntity owner() {
        UserEntity owner = new UserEntity();
        owner.setId(5L);
        owner.setEmail("owner@grun.app");
        return owner;
    }

    private void makePublicReady(RecipeEntity recipe) {
        recipe.setCategories(Set.of(RecipeCategory.VEGAN));
        recipe.setTotalYieldGrams(400.0);
        recipe.setDefaultServingGrams(100.0);
        recipe.setSnapshotCalories(250.0);
        recipe.setImageUrl("https://cdn.grun.app/reviewed/recipe.jpg");
        recipe.setImageSource(ImageSource.ADMIN_UPLOAD);
        recipe.setImageStatus(ImageStatus.APPROVED);
    }
}
