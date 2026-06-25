package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.RecipeDto;
import com.grun.calorietracker.dto.RecipeIngredientRequestDto;
import com.grun.calorietracker.dto.RecipeInteractionDto;
import com.grun.calorietracker.dto.RecipeInteractionRequestDto;
import com.grun.calorietracker.dto.RecipeRequestDto;
import com.grun.calorietracker.dto.RecipeReportDto;
import com.grun.calorietracker.dto.RecipeReportRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.RecipeIngredientEntity;
import com.grun.calorietracker.entity.RecipeReportEntity;
import com.grun.calorietracker.entity.RecipeUserInteractionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.RecipeCategory;
import com.grun.calorietracker.enums.RecipeReportReason;
import com.grun.calorietracker.enums.RecipeReportStatus;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.DuplicateRecipePublicationRequestException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.repository.RecipeReportRepository;
import com.grun.calorietracker.repository.RecipeUserInteractionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.RecipeImageModerationService;
import com.grun.calorietracker.service.impl.RecipeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FoodItemRepository foodItemRepository;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private RecipeReportRepository recipeReportRepository;
    @Mock
    private RecipeUserInteractionRepository recipeUserInteractionRepository;
    @Mock
    private RecipeImageModerationService recipeImageModerationService;
    @InjectMocks
    private RecipeServiceImpl service;

    @Test
    void createRecipe_calculatesTotalServingAndPer100gNutrition() {
        UserEntity user = user();
        FoodItemEntity lentils = product();
        RecipeRequestDto request = recipeRequest(400.0, 100.0, 4);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(foodItemRepository.findById(2L)).thenReturn(Optional.of(lentils));
        when(recipeRepository.save(any(RecipeEntity.class))).thenAnswer(invocation -> {
            RecipeEntity recipe = invocation.getArgument(0);
            recipe.setId(10L);
            return recipe;
        });

        RecipeDto result = service.createRecipe("user@test.com", request);

        assertEquals(10L, result.getId());
        assertEquals(160.0, result.getTotalNutrition().getCalories());
        assertEquals(12.0, result.getPerServingNutrition().getProtein());
        assertEquals(40.0, result.getPer100gNutrition().getCalories());
        assertEquals(1, result.getIngredients().size());
    }

    @Test
    void createRecipe_whenImageUrlProvided_marksImageNeedsReview() {
        UserEntity user = user();
        FoodItemEntity lentils = product();
        RecipeRequestDto request = recipeRequest(400.0, 100.0, 4);
        request.setImageUrl("https://cdn.grun.app/recipes/lentil-soup.jpg");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(foodItemRepository.findById(2L)).thenReturn(Optional.of(lentils));
        when(recipeImageModerationService.moderate("https://cdn.grun.app/recipes/lentil-soup.jpg", ImageSource.USER_UPLOAD))
                .thenReturn(new RecipeImageModerationService.Result(ImageStatus.NEEDS_REVIEW, "Image passed automatic URL moderation and requires visual review."));
        when(recipeRepository.save(any(RecipeEntity.class))).thenAnswer(invocation -> {
            RecipeEntity recipe = invocation.getArgument(0);
            recipe.setId(10L);
            return recipe;
        });

        RecipeDto result = service.createRecipe("user@test.com", request);

        assertEquals("https://cdn.grun.app/recipes/lentil-soup.jpg", result.getImageUrl());
        assertEquals(ImageSource.USER_UPLOAD, result.getImageSource());
        assertEquals(ImageStatus.NEEDS_REVIEW, result.getImageStatus());
    }

    @Test
    void createRecipe_whenImageModerationRejectsUrl_marksImageRejected() {
        UserEntity user = user();
        FoodItemEntity lentils = product();
        RecipeRequestDto request = recipeRequest(400.0, 100.0, 4);
        request.setImageUrl("http://cdn.grun.app/recipes/lentil-soup.jpg");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(foodItemRepository.findById(2L)).thenReturn(Optional.of(lentils));
        when(recipeImageModerationService.moderate("http://cdn.grun.app/recipes/lentil-soup.jpg", ImageSource.USER_UPLOAD))
                .thenReturn(new RecipeImageModerationService.Result(ImageStatus.REJECTED, "Recipe images must use HTTPS."));
        when(recipeRepository.save(any(RecipeEntity.class))).thenAnswer(invocation -> {
            RecipeEntity recipe = invocation.getArgument(0);
            recipe.setId(10L);
            return recipe;
        });

        RecipeDto result = service.createRecipe("user@test.com", request);

        assertEquals(ImageStatus.REJECTED, result.getImageStatus());
    }

    @Test
    void updateInteraction_savesFavoriteAndRating() {
        UserEntity user = user();
        RecipeEntity recipe = recipeEntity(user);
        RecipeInteractionRequestDto request = new RecipeInteractionRequestDto();
        request.setSaved(true);
        request.setFavorite(true);
        request.setRating(5);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(recipeRepository.findAccessibleRecipe(10L, user)).thenReturn(Optional.of(recipe));
        when(recipeUserInteractionRepository.findByUserAndRecipe(user, recipe)).thenReturn(Optional.empty());
        when(recipeUserInteractionRepository.save(any(RecipeUserInteractionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recipeUserInteractionRepository.countByRecipeAndSavedTrue(recipe)).thenReturn(1L);
        when(recipeUserInteractionRepository.countByRecipeAndFavoriteTrue(recipe)).thenReturn(1L);
        when(recipeUserInteractionRepository.countByRecipeAndRatingIsNotNull(recipe)).thenReturn(1L);
        when(recipeUserInteractionRepository.averageRating(recipe)).thenReturn(5.0);

        RecipeInteractionDto result = service.updateInteraction("user@test.com", 10L, request);

        assertEquals(true, result.getSaved());
        assertEquals(true, result.getFavorite());
        assertEquals(5, result.getRating());
        assertEquals(5.0, result.getAverageRating());
    }

    @Test
    void clearInteraction_deletesExistingInteraction() {
        UserEntity user = user();
        RecipeEntity recipe = recipeEntity(user);
        RecipeUserInteractionEntity interaction = new RecipeUserInteractionEntity();
        interaction.setUser(user);
        interaction.setRecipe(recipe);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(recipeRepository.findAccessibleRecipe(10L, user)).thenReturn(Optional.of(recipe));
        when(recipeUserInteractionRepository.findByUserAndRecipe(user, recipe)).thenReturn(Optional.of(interaction));

        service.clearInteraction("user@test.com", 10L);

        verify(recipeUserInteractionRepository).delete(interaction);
    }

    @Test
    void requestPublication_marksRecipePendingReview() {
        UserEntity user = user();
        RecipeEntity recipe = recipeEntity(user);
        recipe.setCategories(new LinkedHashSet<>(Set.of(RecipeCategory.VEGAN, RecipeCategory.HIGH_PROTEIN)));
        recipe.setTotalYieldGrams(400.0);
        recipe.setDefaultServingGrams(100.0);
        recipe.setSnapshotCalories(160.0);
        RecipeIngredientEntity ingredient = new RecipeIngredientEntity();
        ingredient.setRecipe(recipe);
        ingredient.setFoodItem(product());
        ingredient.setPortionSize(200.0);
        ingredient.setPortionUnit(FoodPortionUnit.GRAM);
        ingredient.setNormalizedPortionGrams(200.0);
        recipe.getIngredients().add(ingredient);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(recipeRepository.findByIdAndOwnerUserAndArchivedFalse(10L, user)).thenReturn(Optional.of(recipe));
        when(recipeRepository.save(recipe)).thenReturn(recipe);

        RecipeDto result = service.requestPublication("user@test.com", 10L);

        assertEquals(RecipeVisibility.COMMUNITY_PENDING, result.getVisibility());
        assertEquals(VerificationStatus.NEEDS_REVIEW, result.getVerificationStatus());
    }

    @Test
    void requestPublication_withoutCategories_rejectsRequest() {
        UserEntity user = user();
        RecipeEntity recipe = recipeEntity(user);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(recipeRepository.findByIdAndOwnerUserAndArchivedFalse(10L, user)).thenReturn(Optional.of(recipe));

        assertThrows(IllegalArgumentException.class, () -> service.requestPublication("user@test.com", 10L));
    }

    @Test
    void requestPublication_whenAlreadyPending_rejectsDuplicateRequest() {
        UserEntity user = user();
        RecipeEntity recipe = recipeEntity(user);
        recipe.setVisibility(RecipeVisibility.COMMUNITY_PENDING);
        recipe.setVerificationStatus(VerificationStatus.NEEDS_REVIEW);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(recipeRepository.findByIdAndOwnerUserAndArchivedFalse(10L, user)).thenReturn(Optional.of(recipe));

        assertThrows(DuplicateRecipePublicationRequestException.class, () -> service.requestPublication("user@test.com", 10L));
    }

    @Test
    void requestPublication_whenAlreadyPublished_rejectsDuplicateRequest() {
        UserEntity user = user();
        RecipeEntity recipe = recipeEntity(user);
        recipe.setVisibility(RecipeVisibility.PUBLIC_ADMIN);
        recipe.setVerificationStatus(VerificationStatus.VERIFIED);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(recipeRepository.findByIdAndOwnerUserAndArchivedFalse(10L, user)).thenReturn(Optional.of(recipe));

        assertThrows(DuplicateRecipePublicationRequestException.class, () -> service.requestPublication("user@test.com", 10L));
    }
    @Test
    void copyPublicRecipe_createsPrivateCopyForUser() {
        UserEntity user = user();
        RecipeEntity source = publicRecipeEntity();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(recipeRepository.findById(10L)).thenReturn(Optional.of(source));
        when(recipeRepository.save(any(RecipeEntity.class))).thenAnswer(invocation -> {
            RecipeEntity copy = invocation.getArgument(0);
            copy.setId(20L);
            return copy;
        });

        RecipeDto result = service.copyPublicRecipe("user@test.com", 10L);

        assertEquals(20L, result.getId());
        assertEquals(RecipeVisibility.PRIVATE, result.getVisibility());
        assertEquals(VerificationStatus.RAW_IMPORTED, result.getVerificationStatus());
        assertEquals(Set.of(RecipeCategory.VEGAN), result.getCategories());
        assertEquals(1, result.getIngredients().size());
    }

    @Test
    void reportPublicRecipe_updatesExistingOpenReport() {
        UserEntity user = user();
        RecipeEntity source = publicRecipeEntity();
        RecipeReportEntity report = new RecipeReportEntity();
        report.setId(55L);
        report.setUser(user);
        report.setRecipe(source);
        report.setStatus(RecipeReportStatus.OPEN);
        RecipeReportRequestDto request = new RecipeReportRequestDto();
        request.setReason(RecipeReportReason.INCORRECT_NUTRITION);
        request.setNote("Calories look too low.");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(recipeRepository.findById(10L)).thenReturn(Optional.of(source));
        when(recipeReportRepository.findByUserAndRecipeAndStatus(user, source, RecipeReportStatus.OPEN)).thenReturn(Optional.of(report));
        when(recipeReportRepository.save(report)).thenReturn(report);

        RecipeReportDto result = service.reportPublicRecipe("user@test.com", 10L, request);

        assertEquals(55L, result.getId());
        assertEquals(10L, result.getRecipeId());
        assertEquals(RecipeReportReason.INCORRECT_NUTRITION, result.getReason());
        assertEquals("Calories look too low.", report.getNote());
    }
    @Test
    void createRecipe_whenServingIsLargerThanYield_rejectsRequest() {
        RecipeRequestDto request = recipeRequest(200.0, 250.0, 1);

        assertThrows(IllegalArgumentException.class, () -> service.createRecipe("user@test.com", request));
    }

    @Test
    void createRecipe_whenTooManyIngredients_rejectsRequest() {
        RecipeRequestDto request = recipeRequest(1000.0, 100.0, 10);
        List<RecipeIngredientRequestDto> ingredients = new ArrayList<>();
        for (int index = 0; index < 51; index++) {
            ingredients.add(ingredient());
        }
        request.setIngredients(ingredients);

        assertThrows(IllegalArgumentException.class, () -> service.createRecipe("user@test.com", request));
    }

    private RecipeRequestDto recipeRequest(Double totalYieldGrams, Double defaultServingGrams, Integer servingCount) {
        RecipeRequestDto request = new RecipeRequestDto();
        request.setName("Lentil soup");
        request.setMealType("lunch");
        request.setTotalYieldGrams(totalYieldGrams);
        request.setDefaultServingGrams(defaultServingGrams);
        request.setServingCount(servingCount);
        request.setIngredients(List.of(ingredient()));
        return request;
    }

    private RecipeIngredientRequestDto ingredient() {
        RecipeIngredientRequestDto request = new RecipeIngredientRequestDto();
        request.setFoodItemId(2L);
        request.setPortionSize(200.0);
        request.setPortionUnit(FoodPortionUnit.GRAM);
        return request;
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@test.com");
        return user;
    }

    private FoodItemEntity product() {
        FoodItemEntity food = new FoodItemEntity();
        food.setId(2L);
        food.setName("Lentils");
        food.setCalories(80.0);
        food.setProtein(24.0);
        food.setCarbs(40.0);
        food.setFat(2.0);
        food.setFiber(16.0);
        food.setSugar(4.0);
        food.setSodium(10.0);
        food.setVerificationStatus(VerificationStatus.VERIFIED);
        return food;
    }

    private RecipeEntity recipeEntity(UserEntity user) {
        RecipeEntity recipe = new RecipeEntity();
        recipe.setId(10L);
        recipe.setOwnerUser(user);
        recipe.setName("Lentil soup");
        recipe.setArchived(false);
        return recipe;
    }

    private RecipeEntity publicRecipeEntity() {
        RecipeEntity recipe = recipeEntity(user());
        recipe.setVisibility(RecipeVisibility.PUBLIC_ADMIN);
        recipe.setVerificationStatus(VerificationStatus.VERIFIED);
        recipe.setCategories(new LinkedHashSet<>(Set.of(RecipeCategory.VEGAN)));
        recipe.setTotalYieldGrams(400.0);
        recipe.setDefaultServingGrams(100.0);
        recipe.setServingCount(4);
        recipe.setSnapshotCalories(160.0);
        recipe.setSnapshotProtein(48.0);
        RecipeIngredientEntity ingredient = new RecipeIngredientEntity();
        ingredient.setRecipe(recipe);
        ingredient.setFoodItem(product());
        ingredient.setPortionSize(200.0);
        ingredient.setPortionUnit(FoodPortionUnit.GRAM);
        ingredient.setNormalizedPortionGrams(200.0);
        ingredient.setItemOrder(0);
        recipe.getIngredients().add(ingredient);
        return recipe;
    }
}
