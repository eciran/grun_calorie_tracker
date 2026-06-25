package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.RecipeDto;
import com.grun.calorietracker.dto.RecipeIngredientDto;
import com.grun.calorietracker.dto.RecipeIngredientRequestDto;
import com.grun.calorietracker.dto.RecipeInteractionDto;
import com.grun.calorietracker.dto.RecipeInteractionRequestDto;
import com.grun.calorietracker.dto.RecipeNutritionDto;
import com.grun.calorietracker.dto.RecipePageDto;
import com.grun.calorietracker.dto.RecipeReportDto;
import com.grun.calorietracker.dto.RecipeReportRequestDto;
import com.grun.calorietracker.dto.RecipeRequestDto;
import com.grun.calorietracker.dto.RecipeStepDto;
import com.grun.calorietracker.dto.RecipeStepRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.RecipeCookingStepEntity;
import com.grun.calorietracker.entity.RecipeIngredientEntity;
import com.grun.calorietracker.entity.RecipeReportEntity;
import com.grun.calorietracker.entity.RecipeUserInteractionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.RecipeAllergen;
import com.grun.calorietracker.enums.RecipeCategory;
import com.grun.calorietracker.enums.RecipePublicSort;
import com.grun.calorietracker.enums.RecipeReportStatus;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.DuplicateRecipePublicationRequestException;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.repository.RecipeReportRepository;
import com.grun.calorietracker.repository.RecipeUserInteractionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.RecipeImageModerationService;
import com.grun.calorietracker.service.RecipeService;
import com.grun.calorietracker.service.support.FoodPortionCalculator;
import com.grun.calorietracker.service.support.RecipeAllergenResolver;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RecipeServiceImpl implements RecipeService {

    private static final int MAX_INGREDIENTS = 50;
    private static final int MAX_CATEGORIES = 8;
    private static final int MAX_COOKING_STEPS = 30;
    private static final List<String> ALLOWED_MEAL_TYPES = List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK");

    private final UserRepository userRepository;
    private final FoodItemRepository foodItemRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeReportRepository recipeReportRepository;
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
    @Transactional(readOnly = true)
    public List<RecipeDto> getMyRecipes(String email, String query, String mealType) {
        UserEntity user = getUser(email);
        String normalizedQuery = trimToNull(query);
        String normalizedMealType = normalizeMealType(mealType);
        List<RecipeEntity> recipes;
        if (normalizedQuery != null && normalizedMealType != null) {
            recipes = recipeRepository.findByOwnerUserAndArchivedFalseAndNameContainingIgnoreCaseAndMealTypeOrderByUpdatedAtDesc(
                    user,
                    normalizedQuery,
                    normalizedMealType
            );
        } else if (normalizedQuery != null) {
            recipes = recipeRepository.findByOwnerUserAndArchivedFalseAndNameContainingIgnoreCaseOrderByUpdatedAtDesc(
                    user,
                    normalizedQuery
            );
        } else if (normalizedMealType != null) {
            recipes = recipeRepository.findByOwnerUserAndArchivedFalseAndMealTypeOrderByUpdatedAtDesc(
                    user,
                    normalizedMealType
            );
        } else {
            recipes = recipeRepository.findByOwnerUserAndArchivedFalseOrderByUpdatedAtDesc(user);
        }
        return recipes.stream()
                .map(recipe -> toDto(recipe, user))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
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
        recipe.getCookingSteps().clear();
        applyRequest(recipe, request, user);
        markForReviewIfPublic(recipe);
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
    public RecipeDto requestPublication(String email, Long recipeId) {
        UserEntity user = getUser(email);
        RecipeEntity recipe = getOwnedRecipe(user, recipeId);
        if (recipe.getVisibility() == RecipeVisibility.COMMUNITY_PENDING) {
            throw new DuplicateRecipePublicationRequestException("Recipe is already waiting for admin review.");
        }
        if (recipe.getVisibility() == RecipeVisibility.PUBLIC_ADMIN
                && recipe.getVerificationStatus() == VerificationStatus.VERIFIED) {
            throw new DuplicateRecipePublicationRequestException("Recipe is already published.");
        }
        if (recipe.getCategories() == null || recipe.getCategories().isEmpty()) {
            throw new IllegalArgumentException("At least one recipe category is required before publishing.");
        }
        validateRecipeReadyForPublicationRequest(recipe);
        recipe.setVisibility(RecipeVisibility.COMMUNITY_PENDING);
        recipe.setVerificationStatus(VerificationStatus.NEEDS_REVIEW);
        return toDto(recipeRepository.save(recipe), user);
    }

    @Override
    @Transactional(readOnly = true)
    public RecipePageDto getPublicRecipes(String email,
                                          String query,
                                          String mealType,
                                          MarketRegion marketRegion,
                                          String language,
                                          Set<RecipeCategory> categories,
                                          Set<RecipeAllergen> excludeAllergens,
                                          RecipePublicSort sort,
                                          int page,
                                          int size) {
        UserEntity viewer = email == null ? null : getUser(email);
        RecipePublicSort resolvedSort = sort == null ? RecipePublicSort.NEWEST : sort;
        if (resolvedSort != RecipePublicSort.NEWEST) {
            return getSortedPublicRecipes(viewer, query, mealType, marketRegion, language, categories, excludeAllergens, resolvedSort, page, size);
        }
        Page<RecipeEntity> recipes = recipeRepository.findAll(
                publicRecipeSpecification(query, mealType, marketRegion, language, categories, excludeAllergens),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.DESC, "updatedAt"))
        );
        RecipePageDto dto = new RecipePageDto();
        dto.setContent(recipes.getContent().stream().map(recipe -> toDto(recipe, viewer)).toList());
        dto.setPage(recipes.getNumber());
        dto.setSize(recipes.getSize());
        dto.setTotalElements(recipes.getTotalElements());
        dto.setTotalPages(recipes.getTotalPages());
        dto.setFirst(recipes.isFirst());
        dto.setLast(recipes.isLast());
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public RecipeDto getPublicRecipe(String email, Long recipeId) {
        UserEntity viewer = email == null ? null : getUser(email);
        RecipeEntity recipe = recipeRepository.findById(recipeId)
                .filter(this::isPublicVerifiedRecipe)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
        return toDto(recipe, viewer);
    }

    @Override
    @Transactional
    public RecipeDto copyPublicRecipe(String email, Long recipeId) {
        UserEntity user = getUser(email);
        RecipeEntity source = recipeRepository.findById(recipeId)
                .filter(this::isPublicVerifiedRecipe)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
        RecipeEntity copy = new RecipeEntity();
        copy.setOwnerUser(user);
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setMealType(source.getMealType());
        copy.setVisibility(RecipeVisibility.PRIVATE);
        copy.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        copy.setMarketRegion(source.getMarketRegion());
        copy.setLanguage(source.getLanguage());
        copy.setImageUrl(source.getImageUrl());
        copy.setImageSource(source.getImageSource());
        copy.setImageStatus(source.getImageStatus());
        copy.setTotalYieldGrams(source.getTotalYieldGrams());
        copy.setDefaultServingGrams(source.getDefaultServingGrams());
        copy.setServingCount(source.getServingCount());
        copy.setSnapshotCalories(source.getSnapshotCalories());
        copy.setSnapshotProtein(source.getSnapshotProtein());
        copy.setSnapshotCarbs(source.getSnapshotCarbs());
        copy.setSnapshotFat(source.getSnapshotFat());
        copy.setSnapshotFiber(source.getSnapshotFiber());
        copy.setSnapshotSugar(source.getSnapshotSugar());
        copy.setSnapshotSaturatedFat(source.getSnapshotSaturatedFat());
        copy.setSnapshotSodium(source.getSnapshotSodium());
        copy.setSnapshotPotassium(source.getSnapshotPotassium());
        copy.setSnapshotCholesterol(source.getSnapshotCholesterol());
        copy.setSnapshotCalcium(source.getSnapshotCalcium());
        copy.setSnapshotIron(source.getSnapshotIron());
        copy.setSnapshotMagnesium(source.getSnapshotMagnesium());
        copy.setSnapshotZinc(source.getSnapshotZinc());
        copy.setSnapshotVitaminA(source.getSnapshotVitaminA());
        copy.setSnapshotVitaminC(source.getSnapshotVitaminC());
        copy.setSnapshotVitaminD(source.getSnapshotVitaminD());
        copy.setSnapshotVitaminE(source.getSnapshotVitaminE());
        copy.setSnapshotVitaminB12(source.getSnapshotVitaminB12());
        copy.setCategories(new LinkedHashSet<>(source.getCategories()));
        copy.setAllergens(source.getAllergens() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(source.getAllergens()));
        for (int index = 0; index < source.getCookingSteps().size(); index++) {
            RecipeCookingStepEntity sourceStep = source.getCookingSteps().get(index);
            RecipeCookingStepEntity step = new RecipeCookingStepEntity();
            step.setRecipe(copy);
            step.setInstruction(sourceStep.getInstruction());
            step.setStepOrder(index);
            copy.getCookingSteps().add(step);
        }
        for (int index = 0; index < source.getIngredients().size(); index++) {
            RecipeIngredientEntity sourceIngredient = source.getIngredients().get(index);
            RecipeIngredientEntity ingredient = new RecipeIngredientEntity();
            ingredient.setRecipe(copy);
            ingredient.setFoodItem(sourceIngredient.getFoodItem());
            ingredient.setPortionSize(sourceIngredient.getPortionSize());
            ingredient.setPortionUnit(sourceIngredient.getPortionUnit());
            ingredient.setNormalizedPortionGrams(sourceIngredient.getNormalizedPortionGrams());
            ingredient.setItemOrder(index);
            copy.getIngredients().add(ingredient);
        }
        return toDto(recipeRepository.save(copy), user);
    }

    @Override
    @Transactional
    public RecipeReportDto reportPublicRecipe(String email, Long recipeId, RecipeReportRequestDto request) {
        if (request == null || request.getReason() == null) {
            throw new IllegalArgumentException("Recipe report reason is required.");
        }
        UserEntity user = getUser(email);
        RecipeEntity recipe = recipeRepository.findById(recipeId)
                .filter(this::isPublicVerifiedRecipe)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
        RecipeReportEntity report = recipeReportRepository
                .findByUserAndRecipeAndStatus(user, recipe, RecipeReportStatus.OPEN)
                .orElseGet(() -> {
                    RecipeReportEntity entity = new RecipeReportEntity();
                    entity.setUser(user);
                    entity.setRecipe(recipe);
                    entity.setStatus(RecipeReportStatus.OPEN);
                    return entity;
                });
        report.setReason(request.getReason());
        report.setNote(trimToNull(request.getNote()));
        return toReportDto(recipeReportRepository.save(report));
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
    @Transactional(readOnly = true)
    public List<RecipeDto> getSavedRecipes(String email) {
        UserEntity user = getUser(email);
        return recipeUserInteractionRepository.findByUserAndSavedTrueOrderByUpdatedAtDesc(user).stream()
                .filter(interaction -> Boolean.FALSE.equals(interaction.getRecipe().getArchived()))
                .map(interaction -> toDto(interaction.getRecipe(), user))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
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
        if (request.getImageUrl() != null) {
            applyImageUrl(recipe, trimToNull(request.getImageUrl()));
        }
        recipe.setServingCount(request.getServingCount());
        recipe.setCategories(normalizeCategories(request.getCategories()));

        for (int index = 0; index < request.getIngredients().size(); index++) {
            recipe.getIngredients().add(toIngredient(recipe, request.getIngredients().get(index), user, index));
        }
        applyCookingSteps(recipe, request.getCookingSteps());
        applyAllergens(recipe, request.getAllergens());
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
        if (request.getCategories() != null && request.getCategories().size() > MAX_CATEGORIES) {
            throw new IllegalArgumentException("Recipe can contain at most " + MAX_CATEGORIES + " categories.");
        }
        validateCookingSteps(request.getCookingSteps());
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
        recipe.setSnapshotFiber(totalNullable("fiber", recipe));
        recipe.setSnapshotSugar(totalNullable("sugar", recipe));
        recipe.setSnapshotSaturatedFat(totalNullable("saturatedFat", recipe));
        recipe.setSnapshotSodium(totalNullable("sodium", recipe));
        recipe.setSnapshotPotassium(totalNullable("potassium", recipe));
        recipe.setSnapshotCholesterol(totalNullable("cholesterol", recipe));
        recipe.setSnapshotCalcium(totalNullable("calcium", recipe));
        recipe.setSnapshotIron(totalNullable("iron", recipe));
        recipe.setSnapshotMagnesium(totalNullable("magnesium", recipe));
        recipe.setSnapshotZinc(totalNullable("zinc", recipe));
        recipe.setSnapshotVitaminA(totalNullable("vitaminA", recipe));
        recipe.setSnapshotVitaminC(totalNullable("vitaminC", recipe));
        recipe.setSnapshotVitaminD(totalNullable("vitaminD", recipe));
        recipe.setSnapshotVitaminE(totalNullable("vitaminE", recipe));
        recipe.setSnapshotVitaminB12(totalNullable("vitaminB12", recipe));
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

    private Double totalNullable(String nutrient, RecipeEntity recipe) {
        boolean hasValue = recipe.getIngredients().stream()
                .anyMatch(ingredient -> nullableNutrientValue(nutrient, ingredient.getFoodItem()) != null);
        if (!hasValue) {
            return null;
        }
        return round(recipe.getIngredients().stream()
                .mapToDouble(ingredient -> {
                    Double value = nullableNutrientValue(nutrient, ingredient.getFoodItem());
                    return (value == null ? 0.0 : value) * factor(ingredient);
                })
                .sum());
    }

    private double nutrientValue(String nutrient, FoodItemEntity foodItem) {
        Double value = nullableNutrientValue(nutrient, foodItem);
        return value == null ? 0.0 : value;
    }

    private Double nullableNutrientValue(String nutrient, FoodItemEntity foodItem) {
        return switch (nutrient) {
            case "calories" -> foodItem.getCalories();
            case "protein" -> foodItem.getProtein();
            case "carbs" -> foodItem.getCarbs();
            case "fat" -> foodItem.getFat();
            case "fiber" -> foodItem.getFiber();
            case "sugar" -> foodItem.getSugar();
            case "saturatedFat" -> foodItem.getSaturatedFat();
            case "sodium" -> foodItem.getSodium();
            case "potassium" -> foodItem.getPotassium();
            case "cholesterol" -> foodItem.getCholesterol();
            case "calcium" -> foodItem.getCalcium();
            case "iron" -> foodItem.getIron();
            case "magnesium" -> foodItem.getMagnesium();
            case "zinc" -> foodItem.getZinc();
            case "vitaminA" -> foodItem.getVitaminA();
            case "vitaminC" -> foodItem.getVitaminC();
            case "vitaminD" -> foodItem.getVitaminD();
            case "vitaminE" -> foodItem.getVitaminE();
            case "vitaminB12" -> foodItem.getVitaminB12();
            default -> null;
        };
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
        dto.setSaturatedFat(recipe.getSnapshotSaturatedFat());
        dto.setSodium(recipe.getSnapshotSodium());
        dto.setPotassium(recipe.getSnapshotPotassium());
        dto.setCholesterol(recipe.getSnapshotCholesterol());
        dto.setCalcium(recipe.getSnapshotCalcium());
        dto.setIron(recipe.getSnapshotIron());
        dto.setMagnesium(recipe.getSnapshotMagnesium());
        dto.setZinc(recipe.getSnapshotZinc());
        dto.setVitaminA(recipe.getSnapshotVitaminA());
        dto.setVitaminC(recipe.getSnapshotVitaminC());
        dto.setVitaminD(recipe.getSnapshotVitaminD());
        dto.setVitaminE(recipe.getSnapshotVitaminE());
        dto.setVitaminB12(recipe.getSnapshotVitaminB12());
        RecipeNutritionDto totalNutrition = nutrition(
                recipe.getSnapshotCalories(),
                recipe.getSnapshotProtein(),
                recipe.getSnapshotCarbs(),
                recipe.getSnapshotFat(),
                recipe.getSnapshotFiber(),
                recipe.getSnapshotSugar(),
                recipe.getSnapshotSaturatedFat(),
                recipe.getSnapshotSodium(),
                recipe.getSnapshotPotassium(),
                recipe.getSnapshotCholesterol(),
                recipe.getSnapshotCalcium(),
                recipe.getSnapshotIron(),
                recipe.getSnapshotMagnesium(),
                recipe.getSnapshotZinc(),
                recipe.getSnapshotVitaminA(),
                recipe.getSnapshotVitaminC(),
                recipe.getSnapshotVitaminD(),
                recipe.getSnapshotVitaminE(),
                recipe.getSnapshotVitaminB12()
        );
        dto.setTotalNutrition(totalNutrition);
        dto.setPerServingNutrition(scaleNutrition(totalNutrition, servingFactor(recipe)));
        dto.setPer100gNutrition(scaleNutrition(totalNutrition, per100gFactor(recipe)));
        applyInteractionSummary(dto, recipe, viewer);
        dto.setCreatedAt(recipe.getCreatedAt());
        dto.setUpdatedAt(recipe.getUpdatedAt());
        dto.setIngredients(recipe.getIngredients().stream().map(this::toIngredientDto).toList());
        dto.setCookingSteps(recipe.getCookingSteps().stream().map(this::toStepDto).toList());
        dto.setAllergens(recipe.getAllergens() == null ? Set.of() : new LinkedHashSet<>(recipe.getAllergens()));
        return dto;
    }

    private void applyInteractionSummary(RecipeDto dto, RecipeEntity recipe, UserEntity viewer) {
        dto.setSavedCount(recipeUserInteractionRepository.countByRecipeAndSavedTrue(recipe));
        dto.setFavoriteCount(recipeUserInteractionRepository.countByRecipeAndFavoriteTrue(recipe));
        dto.setRatingCount(recipeUserInteractionRepository.countByRecipeAndRatingIsNotNull(recipe));
        dto.setAverageRating(round(recipeUserInteractionRepository.averageRating(recipe)));
        dto.setCategories(recipe.getCategories() == null ? Set.of() : new LinkedHashSet<>(recipe.getCategories()));
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

    private void applyAllergens(RecipeEntity recipe, Set<RecipeAllergen> requestedAllergens) {
        LinkedHashSet<RecipeAllergen> resolvedAllergens = new LinkedHashSet<>();
        if (recipe.getIngredients() != null) {
            for (RecipeIngredientEntity ingredient : recipe.getIngredients()) {
                if (ingredient.getFoodItem() != null) {
                    resolvedAllergens.addAll(RecipeAllergenResolver.resolve(ingredient.getFoodItem().getAllergens()));
                }
            }
        }
        if (requestedAllergens != null) {
            resolvedAllergens.addAll(requestedAllergens);
        }
        recipe.setAllergens(resolvedAllergens);
    }
    private void applyCookingSteps(RecipeEntity recipe, List<RecipeStepRequestDto> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        for (int index = 0; index < steps.size(); index++) {
            RecipeStepRequestDto request = steps.get(index);
            RecipeCookingStepEntity step = new RecipeCookingStepEntity();
            step.setRecipe(recipe);
            step.setStepOrder(index);
            step.setInstruction(request.getInstruction().trim());
            recipe.getCookingSteps().add(step);
        }
    }

    private void validateCookingSteps(List<RecipeStepRequestDto> steps) {
        if (steps == null) {
            return;
        }
        if (steps.size() > MAX_COOKING_STEPS) {
            throw new IllegalArgumentException("Recipe can contain at most " + MAX_COOKING_STEPS + " cooking steps.");
        }
        for (RecipeStepRequestDto step : steps) {
            if (step == null || step.getInstruction() == null || step.getInstruction().isBlank()) {
                throw new IllegalArgumentException("Recipe cooking steps must not be blank.");
            }
            if (step.getInstruction().length() > 1000) {
                throw new IllegalArgumentException("Recipe cooking step can contain at most 1000 characters.");
            }
        }
    }

    private RecipeStepDto toStepDto(RecipeCookingStepEntity step) {
        RecipeStepDto dto = new RecipeStepDto();
        dto.setStepNumber(step.getStepOrder() == null ? null : step.getStepOrder() + 1);
        dto.setInstruction(step.getInstruction());
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
                                         Double saturatedFat,
                                         Double sodium,
                                         Double potassium,
                                         Double cholesterol,
                                         Double calcium,
                                         Double iron,
                                         Double magnesium,
                                         Double zinc,
                                         Double vitaminA,
                                         Double vitaminC,
                                         Double vitaminD,
                                         Double vitaminE,
                                         Double vitaminB12) {
        RecipeNutritionDto dto = new RecipeNutritionDto();
        dto.setCalories(round(calories));
        dto.setProtein(round(protein));
        dto.setCarbs(round(carbs));
        dto.setFat(round(fat));
        dto.setFiber(roundNullable(fiber));
        dto.setSugar(roundNullable(sugar));
        dto.setSaturatedFat(roundNullable(saturatedFat));
        dto.setSodium(roundNullable(sodium));
        dto.setPotassium(roundNullable(potassium));
        dto.setCholesterol(roundNullable(cholesterol));
        dto.setCalcium(roundNullable(calcium));
        dto.setIron(roundNullable(iron));
        dto.setMagnesium(roundNullable(magnesium));
        dto.setZinc(roundNullable(zinc));
        dto.setVitaminA(roundNullable(vitaminA));
        dto.setVitaminC(roundNullable(vitaminC));
        dto.setVitaminD(roundNullable(vitaminD));
        dto.setVitaminE(roundNullable(vitaminE));
        dto.setVitaminB12(roundNullable(vitaminB12));
        return dto;
    }

    private RecipeNutritionDto scaleNutrition(RecipeNutritionDto nutrition, double factor) {
        RecipeNutritionDto dto = new RecipeNutritionDto();
        dto.setCalories(scale(nutrition.getCalories(), factor));
        dto.setProtein(scale(nutrition.getProtein(), factor));
        dto.setCarbs(scale(nutrition.getCarbs(), factor));
        dto.setFat(scale(nutrition.getFat(), factor));
        dto.setFiber(scaleNullable(nutrition.getFiber(), factor));
        dto.setSugar(scaleNullable(nutrition.getSugar(), factor));
        dto.setSaturatedFat(scaleNullable(nutrition.getSaturatedFat(), factor));
        dto.setSodium(scaleNullable(nutrition.getSodium(), factor));
        dto.setPotassium(scaleNullable(nutrition.getPotassium(), factor));
        dto.setCholesterol(scaleNullable(nutrition.getCholesterol(), factor));
        dto.setCalcium(scaleNullable(nutrition.getCalcium(), factor));
        dto.setIron(scaleNullable(nutrition.getIron(), factor));
        dto.setMagnesium(scaleNullable(nutrition.getMagnesium(), factor));
        dto.setZinc(scaleNullable(nutrition.getZinc(), factor));
        dto.setVitaminA(scaleNullable(nutrition.getVitaminA(), factor));
        dto.setVitaminC(scaleNullable(nutrition.getVitaminC(), factor));
        dto.setVitaminD(scaleNullable(nutrition.getVitaminD(), factor));
        dto.setVitaminE(scaleNullable(nutrition.getVitaminE(), factor));
        dto.setVitaminB12(scaleNullable(nutrition.getVitaminB12(), factor));
        return dto;
    }

    private Double scale(Double value, double factor) {
        return round((value == null ? 0.0 : value) * factor);
    }

    private Double scaleNullable(Double value, double factor) {
        return value == null ? null : round(value * factor);
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

    private Double roundNullable(Double value) {
        return value == null ? null : Math.round(value * 100.0) / 100.0;
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

    private RecipePageDto getSortedPublicRecipes(UserEntity viewer,
                                                 String query,
                                                 String mealType,
                                                 MarketRegion marketRegion,
                                                 String language,
                                                 Set<RecipeCategory> categories,
                                                 Set<RecipeAllergen> excludeAllergens,
                                                 RecipePublicSort sort,
                                                 int page,
                                                 int size) {
        List<RecipeEntity> recipes = new ArrayList<>(recipeRepository.findAll(
                publicRecipeSpecification(query, mealType, marketRegion, language, categories, excludeAllergens)
        ));
        Comparator<RecipeEntity> comparator = switch (sort) {
            case POPULAR -> Comparator
                    .comparingLong((RecipeEntity recipe) -> recipeUserInteractionRepository.countByRecipeAndSavedTrue(recipe)
                            + recipeUserInteractionRepository.countByRecipeAndFavoriteTrue(recipe))
                    .thenComparing(recipe -> safeDate(recipe.getUpdatedAt()));
            case RATING -> Comparator
                    .comparingDouble((RecipeEntity recipe) -> {
                        Double averageRating = recipeUserInteractionRepository.averageRating(recipe);
                        return averageRating == null ? 0.0 : averageRating;
                    })
                    .thenComparingLong(recipe -> recipeUserInteractionRepository.countByRecipeAndRatingIsNotNull(recipe))
                    .thenComparing(recipe -> safeDate(recipe.getUpdatedAt()));
            case NEWEST -> Comparator.comparing(recipe -> safeDate(recipe.getUpdatedAt()));
        };
        recipes.sort(comparator.reversed());
        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.min(Math.max(size, 1), 50);
        int from = Math.min(resolvedPage * resolvedSize, recipes.size());
        int to = Math.min(from + resolvedSize, recipes.size());
        RecipePageDto dto = new RecipePageDto();
        dto.setContent(recipes.subList(from, to).stream().map(recipe -> toDto(recipe, viewer)).toList());
        dto.setPage(resolvedPage);
        dto.setSize(resolvedSize);
        dto.setTotalElements(recipes.size());
        dto.setTotalPages((int) Math.ceil((double) recipes.size() / resolvedSize));
        dto.setFirst(resolvedPage == 0);
        dto.setLast(to >= recipes.size());
        return dto;
    }
    private Specification<RecipeEntity> publicRecipeSpecification(String query,
                                                                  String mealType,
                                                                  MarketRegion marketRegion,
                                                                  String language,
                                                                  Set<RecipeCategory> categories,
                                                                  Set<RecipeAllergen> excludeAllergens) {
        Set<RecipeCategory> normalizedCategories = normalizeCategories(categories);
        Set<RecipeAllergen> normalizedExcludedAllergens = normalizeAllergens(excludeAllergens);
        return (root, criteriaQuery, criteriaBuilder) -> {
            if (criteriaQuery != null && !Long.class.equals(criteriaQuery.getResultType())) {
                criteriaQuery.distinct(true);
            }
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("visibility"), RecipeVisibility.PUBLIC_ADMIN));
            predicates.add(criteriaBuilder.equal(root.get("verificationStatus"), VerificationStatus.VERIFIED));
            predicates.add(criteriaBuilder.isFalse(root.get("archived")));
            String normalizedQuery = trimToNull(query);
            if (normalizedQuery != null) {
                String like = "%" + normalizedQuery.toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), like),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), like)
                ));
            }
            String normalizedMealType = normalizeMealType(mealType);
            if (normalizedMealType != null) {
                predicates.add(criteriaBuilder.equal(root.get("mealType"), normalizedMealType));
            }
            if (marketRegion != null) {
                predicates.add(criteriaBuilder.equal(root.get("marketRegion"), marketRegion));
            }
            String normalizedLanguage = trimToNull(language);
            if (normalizedLanguage != null) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(root.get("language")), normalizedLanguage.toLowerCase(Locale.ROOT)));
            }
            for (RecipeCategory category : normalizedCategories) {
                Join<RecipeEntity, RecipeCategory> categoryJoin = root.joinSet("categories", JoinType.INNER);
                predicates.add(criteriaBuilder.equal(categoryJoin, category));
            }
            if (!normalizedExcludedAllergens.isEmpty() && criteriaQuery != null) {
                var allergenSubquery = criteriaQuery.subquery(Long.class);
                var allergenRoot = allergenSubquery.from(RecipeEntity.class);
                Join<RecipeEntity, RecipeAllergen> allergenJoin = allergenRoot.joinSet("allergens", JoinType.INNER);
                allergenSubquery.select(allergenRoot.get("id"));
                allergenSubquery.where(
                        criteriaBuilder.equal(allergenRoot.get("id"), root.get("id")),
                        allergenJoin.in(normalizedExcludedAllergens)
                );
                predicates.add(criteriaBuilder.not(criteriaBuilder.exists(allergenSubquery)));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private boolean isPublicVerifiedRecipe(RecipeEntity recipe) {
        return recipe != null
                && recipe.getVisibility() == RecipeVisibility.PUBLIC_ADMIN
                && recipe.getVerificationStatus() == VerificationStatus.VERIFIED
                && !Boolean.TRUE.equals(recipe.getArchived());
    }

    private void validateRecipeReadyForPublicationRequest(RecipeEntity recipe) {
        validateCommonPublicRecipeFields(recipe);
        if (recipe.getImageUrl() != null && recipe.getImageStatus() == ImageStatus.REJECTED) {
            throw new IllegalArgumentException("Recipe image is rejected and must be replaced before publishing.");
        }
    }

    private void validateCommonPublicRecipeFields(RecipeEntity recipe) {
        if (recipe.getCategories() == null || recipe.getCategories().isEmpty()) {
            throw new IllegalArgumentException("At least one recipe category is required before publishing.");
        }
        if (recipe.getTotalYieldGrams() == null || recipe.getTotalYieldGrams() <= 0) {
            throw new IllegalArgumentException("Recipe total yield grams must be greater than zero before publishing.");
        }
        if (recipe.getDefaultServingGrams() == null || recipe.getDefaultServingGrams() <= 0) {
            throw new IllegalArgumentException("Recipe default serving grams must be greater than zero before publishing.");
        }
        if (recipe.getIngredients() == null || recipe.getIngredients().isEmpty()) {
            throw new IllegalArgumentException("Recipe must contain at least one ingredient before publishing.");
        }
        if (recipe.getSnapshotCalories() == null || recipe.getSnapshotCalories() <= 0) {
            throw new IllegalArgumentException("Recipe nutrition must be calculated before publishing.");
        }
    }

    private void markForReviewIfPublic(RecipeEntity recipe) {
        if (recipe.getVisibility() == RecipeVisibility.PUBLIC_ADMIN
                || recipe.getVisibility() == RecipeVisibility.COMMUNITY_PENDING) {
            recipe.setVisibility(RecipeVisibility.COMMUNITY_PENDING);
            recipe.setVerificationStatus(VerificationStatus.NEEDS_REVIEW);
        }
    }

    private Set<RecipeCategory> normalizeCategories(Set<RecipeCategory> categories) {
        return categories == null ? new LinkedHashSet<>() : new LinkedHashSet<>(categories);
    }

    private Set<RecipeAllergen> normalizeAllergens(Set<RecipeAllergen> allergens) {
        return allergens == null ? new LinkedHashSet<>() : new LinkedHashSet<>(allergens);
    }

    private RecipeReportDto toReportDto(RecipeReportEntity report) {
        RecipeReportDto dto = new RecipeReportDto();
        dto.setId(report.getId());
        dto.setRecipeId(report.getRecipe().getId());
        dto.setReason(report.getReason());
        dto.setStatus(report.getStatus());
        dto.setCreatedAt(report.getCreatedAt());
        return dto;
    }

    private java.time.LocalDateTime safeDate(java.time.LocalDateTime value) {
        return value == null ? java.time.LocalDateTime.MIN : value;
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
