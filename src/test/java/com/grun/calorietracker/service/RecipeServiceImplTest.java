package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.RecipeDto;
import com.grun.calorietracker.dto.RecipeIngredientRequestDto;
import com.grun.calorietracker.dto.RecipeInteractionDto;
import com.grun.calorietracker.dto.RecipeInteractionRequestDto;
import com.grun.calorietracker.dto.RecipeRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.RecipeUserInteractionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.RecipeRepository;
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
import java.util.List;
import java.util.Optional;

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
}
