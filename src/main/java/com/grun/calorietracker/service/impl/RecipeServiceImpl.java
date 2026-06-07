package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.RecipeDto;
import com.grun.calorietracker.dto.RecipeIngredientDto;
import com.grun.calorietracker.dto.RecipeIngredientRequestDto;
import com.grun.calorietracker.dto.RecipeInteractionDto;
import com.grun.calorietracker.dto.RecipeInteractionRequestDto;
import com.grun.calorietracker.dto.RecipeNutritionDto;
import com.grun.calorietracker.dto.RecipeRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.RecipeIngredientEntity;
import com.grun.calorietracker.entity.RecipeUserInteractionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.repository.RecipeUserInteractionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.RecipeImageModerationService;
import com.grun.calorietracker.service.RecipeService;
import com.grun.calorietracker.service.support.FoodPortionCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RecipeServiceImpl implements RecipeService {

    private static final int MAX_INGREDIENTS = 50;
    private static final List<String> ALLOWED_MEAL_TYPES = List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK");

    private final UserRepository userRepository;
    private final FoodItemRepository foodItemRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeUserInteractionRepository recipeUserInteractionRepository;
    private final RecipeImageModerationService recipeImageModerationService;

    @Override
    @Transactional
    public RecipeDto createRecipe(String email, RecipeRequestDto request) {
        validateRecipeRequest(request);
        UserEntity user = getUser(email);
        RecipeEntity recipe = new RecipeEntity();
        recipe.setOwnerUser(user);
        recipe.setVisibility(RecipeVisibility.PRIVATE);
        recipe.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        applyRequest(recipe, request, user);
        return toDto(recipeRepository.save(recipe), user);
    }

    @Override
    public List<RecipeDto> getMyRecipes(String email, String query, String mealType) {
        UserEntity user = getUser(email);
        return recipeRepository.searchOwnedRecipes(
                        user,
                        trimToNull(query),
                        normalizeMealType(mealType)
                ).stream()
                .map(recipe -> toDto(recipe, user))
                .toList();
    }

    @Override
    public RecipeDto getRecipe(String email, Long recipeId) {
        UserEntity user = getUser(email);
        return toDto(getOwnedRecipe(user, recipeId), user);
    }

    @Override
    @Transactional
    public RecipeDto updateRecipe(String email, Long recipeId, RecipeRequestDto request) {
        validateRecipeRequest(request);
        UserEntity user = getUser(email);
        RecipeEntity recipe = recipeRepository.findByIdAndOwnerUserAndArchivedFalse(recipeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
        recipe.getIngredients().clear();
        applyRequest(recipe, request, user);
        return toDto(recipeRepository.save(recipe), user);
    }

    @Override
    @Transactional
    public void archiveRecipe(String email, Long recipeId) {
        RecipeEntity recipe = getOwnedRecipe(email, recipeId);
        recipe.setArchived(true);
        recipeRepository.save(recipe);
    }

    @Override
    @Transactional
    public RecipeInteractionDto updateInteraction(String email, Long recipeId, RecipeInteractionRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Recipe interaction request is required.");
        }
        UserEntity user = getUser(email);
        RecipeEntity recipe = getAccessibleRecipe(user, recipeId);
        RecipeUserInteractionEntity interaction = recipeUserInteractionRepository.findByUserAndRecipe(user, recipe)
                .orElseGet(() -> {
                    RecipeUserInteractionEntity entity = new RecipeUserInteractionEntity();
                    entity.setUser(user);
                    entity.setRecipe(recipe);
                    return entity;
                });
        if (request.getSaved() != null) {
            interaction.setSaved(request.getSaved());
        }
        if (request.getFavorite() != null) {
            interaction.setFavorite(request.getFavorite());
        }
        if (Boolean.TRUE.equals(request.getClearRating())) {
            interaction.setRating(null);
        } else if (request.getRating() != null) {
            interaction.setRating(request.getRating());
        }
        return toInteractionDto(recipeUserInteractionRepository.save(interaction));
    }

    @Override
    @Transactional
    public void clearInteraction(String email, Long recipeId) {
        UserEntity user = getUser(email);
        RecipeEntity recipe = getAccessibleRecipe(user, recipeId);
        recipeUserInteractionRepository.findByUserAndRecipe(user, recipe)
                .ifPresent(recipeUserInteractionRepository::delete);
    }

    @Override
    public List<RecipeDto> getSavedRecipes(String email) {
        UserEntity user = getUser(email);
        return recipeUserInteractionRepository.findByUserAndSavedTrueOrderByUpdatedAtDesc(user).stream()
                .filter(interaction -> Boolean.FALSE.equals(interaction.getRecipe().getArchived()))
                .map(interaction -> toDto(interaction.getRecipe(), user))
                .toList();
    }

    @Override
    public List<RecipeDto> getFavoriteRecipes(String email) {
        UserEntity user = getUser(email);
        return recipeUserInteractionRepository.findByUserAndFavoriteTrueOrderByUpdatedAtDesc(user).stream()
                .filter(interaction -> Boolean.FALSE.equals(interaction.getRecipe().getArchived()))
                .map(interaction -> toDto(interaction.getRecipe(), user))
                .toList();
    }

    private void applyRequest(RecipeEntity recipe, RecipeRequestDto request, UserEntity user) {
        recipe.setName(request.getName().trim());
        recipe.setDescription(trimToNull(request.getDescription()));
        recipe.setMealType(normalizeMealType(request.getMealType()));
        recipe.setMarketRegion(request.getMarketRegion());
        recipe.setLanguage(trimToNull(request.getLanguage()));
        applyImageUrl(recipe, trimToNull(request.getImageUrl()));
        recipe.setServingCount(request.getServingCount());

        for (int index = 0; index < request.getIngredients().size(); index++) {
            recipe.getIngredients().add(toIngredient(recipe, request.getIngredients().get(index), user, index));
        }
        recalculateNutrition(recipe, request);
    }

    private void validateRecipeRequest(RecipeRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Recipe request is required.");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Recipe name is required.");
        }
        if (request.getIngredients() == null || request.getIngredients().isEmpty()) {
            throw new IllegalArgumentException("Recipe must contain at least one ingredient.");
        }
        if (request.getIngredients().size() > MAX_INGREDIENTS) {
            throw new IllegalArgumentException("Recipe can contain at most " + MAX_INGREDIENTS + " ingredients.");
        }
        String mealType = normalizeMealType(request.getMealType());
        if (mealType != null && !ALLOWED_MEAL_TYPES.contains(mealType)) {
            throw new IllegalArgumentException("Meal type must be one of BREAKFAST, LUNCH, DINNER, or SNACK.");
        }
        if (request.getTotalYieldGrams() != null
                && request.getDefaultServingGrams() != null
                && request.getDefaultServingGrams() > request.getTotalYieldGrams()) {
            throw new IllegalArgumentException("Default serving grams must not exceed total yield grams.");
        }
        if (request.getServingCount() != null
                && request.getServingCount() > 0
                && request.getTotalYieldGrams() != null
                && request.getDefaultServingGrams() != null) {
            double expectedYield = request.getDefaultServingGrams() * request.getServingCount();
            if (expectedYield > request.getTotalYieldGrams() * 1.25) {
                throw new IllegalArgumentException("Serving count and default serving grams are not consistent with total yield grams.");
            }
        }
    }

    private RecipeIngredientEntity toIngredient(RecipeEntity recipe,
                                                RecipeIngredientRequestDto request,
                                                UserEntity user,
                                                int itemOrder) {
        FoodItemEntity foodItem = foodItemRepository.findById(request.getFoodItemId())
                .orElseThrow(() -> new ProductNotFoundException("Food item not found"));
        ensureFoodAvailable(foodItem, user);
        FoodPortionUnit unit = FoodPortionCalculator.resolveUnit(request.getPortionUnit());
        RecipeIngredientEntity ingredient = new RecipeIngredientEntity();
        ingredient.setRecipe(recipe);
        ingredient.setFoodItem(foodItem);
        ingredient.setPortionSize(request.getPortionSize());
        ingredient.setPortionUnit(unit);
        ingredient.setNormalizedPortionGrams(FoodPortionCalculator.normalizeToGrams(request.getPortionSize(), unit, foodItem));
        ingredient.setItemOrder(itemOrder);
        return ingredient;
    }

    private void recalculateNutrition(RecipeEntity recipe, RecipeRequestDto request) {
        double ingredientYield = recipe.getIngredients().stream()
                .map(RecipeIngredientEntity::getNormalizedPortionGrams)
                .filter(value -> value != null && value > 0)
                .mapToDouble(Double::doubleValue)
                .sum();
        double totalYield = request.getTotalYieldGrams() != null && request.getTotalYieldGrams() > 0
                ? request.getTotalYieldGrams()
                : ingredientYield;
        recipe.setTotalYieldGrams(totalYield);
        recipe.setDefaultServingGrams(resolveDefaultServingGrams(request, totalYield));
        recipe.setSnapshotCalories(total("calories", recipe));
        recipe.setSnapshotProtein(total("protein", recipe));
        recipe.setSnapshotCarbs(total("carbs", recipe));
        recipe.setSnapshotFat(total("fat", recipe));
        recipe.setSnapshotFiber(total("fiber", recipe));
        recipe.setSnapshotSugar(total("sugar", recipe));
        recipe.setSnapshotSodium(total("sodium", recipe));
    }

    private Double resolveDefaultServingGrams(RecipeRequestDto request, double totalYield) {
        if (request.getDefaultServingGrams() != null && request.getDefaultServingGrams() > 0) {
            return request.getDefaultServingGrams();
        }
        if (request.getServingCount() != null && request.getServingCount() > 0 && totalYield > 0) {
            return totalYield / request.getServingCount();
        }
        return totalYield > 0 ? totalYield : null;
    }

    private Double total(String nutrient, RecipeEntity recipe) {
        return recipe.getIngredients().stream()
                .mapToDouble(ingredient -> nutrientValue(nutrient, ingredient.getFoodItem()) * factor(ingredient))
                .sum();
    }

    private double nutrientValue(String nutrient, FoodItemEntity foodItem) {
        Double value = switch (nutrient) {
            case "calories" -> foodItem.getCalories();
            case "protein" -> foodItem.getProtein();
            case "carbs" -> foodItem.getCarbs();
            case "fat" -> foodItem.getFat();
            case "fiber" -> foodItem.getFiber();
            case "sugar" -> foodItem.getSugar();
            case "sodium" -> foodItem.getSodium();
            default -> null;
        };
        return value == null ? 0.0 : value;
    }

    private double factor(RecipeIngredientEntity ingredient) {
        return ingredient.getNormalizedPortionGrams() == null ? 0.0 : ingredient.getNormalizedPortionGrams() / 100.0;
    }

    private RecipeDto toDto(RecipeEntity recipe, UserEntity viewer) {
        RecipeDto dto = new RecipeDto();
        dto.setId(recipe.getId());
        dto.setName(recipe.getName());
        dto.setDescription(recipe.getDescription());
        dto.setMealType(recipe.getMealType());
        dto.setVisibility(recipe.getVisibility());
        dto.setVerificationStatus(recipe.getVerificationStatus());
        dto.setMarketRegion(recipe.getMarketRegion());
        dto.setLanguage(recipe.getLanguage());
        dto.setImageUrl(recipe.getImageUrl());
        dto.setImageSource(recipe.getImageSource());
        dto.setImageStatus(recipe.getImageStatus());
        dto.setTotalYieldGrams(recipe.getTotalYieldGrams());
        dto.setDefaultServingGrams(recipe.getDefaultServingGrams());
        dto.setServingCount(recipe.getServingCount());
        dto.setCalories(recipe.getSnapshotCalories());
        dto.setProtein(recipe.getSnapshotProtein());
        dto.setCarbs(recipe.getSnapshotCarbs());
        dto.setFat(recipe.getSnapshotFat());
        dto.setFiber(recipe.getSnapshotFiber());
        dto.setSugar(recipe.getSnapshotSugar());
        dto.setSodium(recipe.getSnapshotSodium());
        RecipeNutritionDto totalNutrition = nutrition(
                recipe.getSnapshotCalories(),
                recipe.getSnapshotProtein(),
                recipe.getSnapshotCarbs(),
                recipe.getSnapshotFat(),
                recipe.getSnapshotFiber(),
                recipe.getSnapshotSugar(),
                recipe.getSnapshotSodium()
        );
        dto.setTotalNutrition(totalNutrition);
        dto.setPerServingNutrition(scaleNutrition(totalNutrition, servingFactor(recipe)));
        dto.setPer100gNutrition(scaleNutrition(totalNutrition, per100gFactor(recipe)));
        applyInteractionSummary(dto, recipe, viewer);
        dto.setCreatedAt(recipe.getCreatedAt());
        dto.setUpdatedAt(recipe.getUpdatedAt());
        dto.setIngredients(recipe.getIngredients().stream().map(this::toIngredientDto).toList());
        return dto;
    }

    private void applyInteractionSummary(RecipeDto dto, RecipeEntity recipe, UserEntity viewer) {
        dto.setSavedCount(recipeUserInteractionRepository.countByRecipeAndSavedTrue(recipe));
        dto.setFavoriteCount(recipeUserInteractionRepository.countByRecipeAndFavoriteTrue(recipe));
        dto.setRatingCount(recipeUserInteractionRepository.countByRecipeAndRatingIsNotNull(recipe));
        dto.setAverageRating(round(recipeUserInteractionRepository.averageRating(recipe)));
        if (viewer == null) {
            dto.setSavedByMe(false);
            dto.setFavoriteByMe(false);
            return;
        }
        recipeUserInteractionRepository.findByUserAndRecipe(viewer, recipe).ifPresentOrElse(interaction -> {
            dto.setSavedByMe(Boolean.TRUE.equals(interaction.getSaved()));
            dto.setFavoriteByMe(Boolean.TRUE.equals(interaction.getFavorite()));
            dto.setMyRating(interaction.getRating());
        }, () -> {
            dto.setSavedByMe(false);
            dto.setFavoriteByMe(false);
        });
    }

    private RecipeInteractionDto toInteractionDto(RecipeUserInteractionEntity interaction) {
        RecipeInteractionDto dto = new RecipeInteractionDto();
        dto.setRecipeId(interaction.getRecipe().getId());
        dto.setSaved(Boolean.TRUE.equals(interaction.getSaved()));
        dto.setFavorite(Boolean.TRUE.equals(interaction.getFavorite()));
        dto.setRating(interaction.getRating());
        dto.setSavedCount(recipeUserInteractionRepository.countByRecipeAndSavedTrue(interaction.getRecipe()));
        dto.setFavoriteCount(recipeUserInteractionRepository.countByRecipeAndFavoriteTrue(interaction.getRecipe()));
        dto.setRatingCount(recipeUserInteractionRepository.countByRecipeAndRatingIsNotNull(interaction.getRecipe()));
        dto.setAverageRating(round(recipeUserInteractionRepository.averageRating(interaction.getRecipe())));
        dto.setUpdatedAt(interaction.getUpdatedAt());
        return dto;
    }

    private RecipeIngredientDto toIngredientDto(RecipeIngredientEntity ingredient) {
        RecipeIngredientDto dto = new RecipeIngredientDto();
        dto.setFoodItemId(ingredient.getFoodItem().getId());
        dto.setFoodName(ingredient.getFoodItem().getName());
        dto.setPortionSize(ingredient.getPortionSize());
        dto.setPortionUnit(FoodPortionCalculator.resolveUnit(ingredient.getPortionUnit()));
        dto.setNormalizedPortionGrams(ingredient.getNormalizedPortionGrams());
        return dto;
    }

    private RecipeNutritionDto nutrition(Double calories,
                                         Double protein,
                                         Double carbs,
                                         Double fat,
                                         Double fiber,
                                         Double sugar,
                                         Double sodium) {
        return new RecipeNutritionDto(
                round(calories),
                round(protein),
                round(carbs),
                round(fat),
                round(fiber),
                round(sugar),
                round(sodium)
        );
    }

    private RecipeNutritionDto scaleNutrition(RecipeNutritionDto nutrition, double factor) {
        return new RecipeNutritionDto(
                round(nutrition.getCalories() * factor),
                round(nutrition.getProtein() * factor),
                round(nutrition.getCarbs() * factor),
                round(nutrition.getFat() * factor),
                round(nutrition.getFiber() * factor),
                round(nutrition.getSugar() * factor),
                round(nutrition.getSodium() * factor)
        );
    }

    private double servingFactor(RecipeEntity recipe) {
        if (recipe.getTotalYieldGrams() == null || recipe.getTotalYieldGrams() <= 0
                || recipe.getDefaultServingGrams() == null || recipe.getDefaultServingGrams() <= 0) {
            return 1.0;
        }
        return recipe.getDefaultServingGrams() / recipe.getTotalYieldGrams();
    }

    private double per100gFactor(RecipeEntity recipe) {
        if (recipe.getTotalYieldGrams() == null || recipe.getTotalYieldGrams() <= 0) {
            return 0.0;
        }
        return 100.0 / recipe.getTotalYieldGrams();
    }

    private Double round(Double value) {
        return value == null ? 0.0 : Math.round(value * 100.0) / 100.0;
    }

    private RecipeEntity getOwnedRecipe(String email, Long recipeId) {
        return getOwnedRecipe(getUser(email), recipeId);
    }

    private RecipeEntity getOwnedRecipe(UserEntity user, Long recipeId) {
        return recipeRepository.findByIdAndOwnerUserAndArchivedFalse(recipeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
    }

    private RecipeEntity getAccessibleRecipe(UserEntity user, Long recipeId) {
        return recipeRepository.findAccessibleRecipe(recipeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private void ensureFoodAvailable(FoodItemEntity foodItem, UserEntity user) {
        if (Boolean.TRUE.equals(foodItem.getIsCustom())
                && (foodItem.getCreatedByUser() == null || !user.getId().equals(foodItem.getCreatedByUser().getId()))) {
            throw new ProductNotFoundException("Custom food item is not available to this user");
        }
        if (foodItem.getVerificationStatus() == VerificationStatus.REJECTED) {
            throw new ProductNotFoundException("Food item is not available");
        }
    }

    private String normalizeMealType(String mealType) {
        return mealType == null || mealType.isBlank() ? null : mealType.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void applyImageUrl(RecipeEntity recipe, String imageUrl) {
        if (Objects.equals(recipe.getImageUrl(), imageUrl)) {
            return;
        }
        recipe.setImageUrl(imageUrl);
        if (imageUrl == null) {
            recipe.setImageSource(null);
            recipe.setImageStatus(null);
            recipe.setImageReviewNote(null);
            recipe.setImageReviewedBy(null);
            recipe.setImageReviewedAt(null);
            return;
        }
        recipe.setImageSource(ImageSource.USER_UPLOAD);
        RecipeImageModerationService.Result moderation = recipeImageModerationService.moderate(imageUrl, ImageSource.USER_UPLOAD);
        recipe.setImageStatus(moderation.status());
        recipe.setImageReviewNote(moderation.note());
        recipe.setImageReviewedBy(null);
        recipe.setImageReviewedAt(null);
    }
}
