package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.MealPlanNutritionSnapshotDto;
import com.grun.calorietracker.entity.WorkoutPlanEntity;
import com.grun.calorietracker.enums.MealPlanItemLinkState;
import com.grun.calorietracker.enums.MealPlanWorkoutRelation;
import com.grun.calorietracker.enums.NutritionPlanGenerationMode;
import com.grun.calorietracker.enums.WorkoutPlanStatus;
import com.grun.calorietracker.repository.WorkoutPlanRepository;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;

import com.grun.calorietracker.dto.AiNutritionPlanDraftResponseDto;
import com.grun.calorietracker.dto.GroceryListDto;
import com.grun.calorietracker.dto.MealPlanDayNutritionDto;
import com.grun.calorietracker.dto.GroceryListItemDto;
import com.grun.calorietracker.dto.MealPlanDto;
import com.grun.calorietracker.dto.MealPlanDuplicateRequestDto;
import com.grun.calorietracker.dto.MealPlanItemDto;
import com.grun.calorietracker.dto.MealPlanItemRequestDto;
import com.grun.calorietracker.dto.MealPlanRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.MealPlanEntity;
import com.grun.calorietracker.entity.MealPlanItemEntity;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.RecipeIngredientEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.MealPlanItemType;
import com.grun.calorietracker.enums.MealPlanStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.MealPlanRepository;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.MealPlanService;
import com.grun.calorietracker.service.SubscriptionService;
import com.grun.calorietracker.service.support.FoodPortionCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MealPlanServiceImpl implements MealPlanService {

    private static final int MAX_PLAN_DAYS = 31;
    private static final int MAX_ITEMS = 120;

    private final MealPlanRepository mealPlanRepository;
    private final UserRepository userRepository;
    private final FoodItemRepository foodItemRepository;
    private final RecipeRepository recipeRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final ObjectMapper objectMapper;
    private final SubscriptionService subscriptionService;

    @Override
    @Transactional
    public MealPlanDto createMealPlan(String email, MealPlanRequestDto request) {
        UserEntity user = getUser(email);
        validateRequest(request);
        MealPlanEntity plan = new MealPlanEntity();
        plan.setUser(user);
        applyRequest(plan, request, user);
        return toDto(mealPlanRepository.save(plan));
    }

    @Override
    @Transactional
    public MealPlanDto updateMealPlan(String email, Long planId, MealPlanRequestDto request) {
        UserEntity user = getUser(email);
        validateRequest(request);
        MealPlanEntity plan = getOwnedPlan(planId, user);
        boolean aiGenerated = plan.getSourceAiRequest() != null || plan.getItems().stream()
                .anyMatch(item -> item.getItemType() == MealPlanItemType.AI_SNAPSHOT);
        if (aiGenerated && request.getItems().size() > plan.getItems().size()) {
            throw new IllegalArgumentException("Items cannot be added to an AI-generated meal plan.");
        }
        plan.getItems().clear();
        applyRequest(plan, request, user);
        return toDto(mealPlanRepository.save(plan));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MealPlanDto> getMealPlans(String email) {
        UserEntity user = getUser(email);
        return mealPlanRepository.findByUserAndStatusNotOrderByStartDateDesc(user, MealPlanStatus.ARCHIVED)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MealPlanDto getMealPlan(String email, Long planId) {
        return toDto(getOwnedPlan(planId, getUser(email)));
    }

    @Override
    @Transactional
    public MealPlanDto duplicateMealPlan(String email, Long planId, MealPlanDuplicateRequestDto request) {
        if (request == null || request.getName() == null || request.getName().isBlank() || request.getStartDate() == null) {
            throw new IllegalArgumentException("Meal plan duplicate name and start date are required.");
        }
        UserEntity user = getUser(email);
        MealPlanEntity source = getOwnedPlan(planId, user);
        long planDays = ChronoUnit.DAYS.between(source.getStartDate(), source.getEndDate());

        MealPlanEntity copy = new MealPlanEntity();
        copy.setUser(user);
        copy.setName(request.getName().trim());
        copy.setStartDate(request.getStartDate());
        copy.setEndDate(request.getStartDate().plusDays(planDays));
        copy.setStatus(MealPlanStatus.DRAFT);

        copy.setGenerationMode(source.getGenerationMode());
        copy.setWorkoutPlan(source.getWorkoutPlan());
        copy.setSourceAiRequest(source.getSourceAiRequest());
        copy.setSchemaVersion(source.getSchemaVersion());
        copy.setPromptVersion(source.getPromptVersion());

        for (MealPlanItemEntity sourceItem : source.getItems()) {
            MealPlanItemEntity item = new MealPlanItemEntity();
            item.setMealPlan(copy);
            long offset = ChronoUnit.DAYS.between(source.getStartDate(), sourceItem.getPlanDate());
            item.setPlanDate(request.getStartDate().plusDays(offset));
            item.setMealType(sourceItem.getMealType());
            item.setItemType(sourceItem.getItemType());
            item.setFoodItem(sourceItem.getFoodItem());
            item.setRecipe(sourceItem.getRecipe());
            item.setPortionSize(sourceItem.getPortionSize());
            item.setPortionUnit(sourceItem.getPortionUnit());
            item.setServingCount(sourceItem.getServingCount());
            item.setItemOrder(sourceItem.getItemOrder());
            copySnapshotMetadata(sourceItem, item);
            copy.getItems().add(item);
        }

        return toDto(mealPlanRepository.save(copy));
    }

    @Override
    @Transactional(readOnly = true)
    public GroceryListDto getGroceryList(String email, Long planId) {
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.GROCERY_LIST);
        MealPlanEntity plan = getOwnedPlan(planId, getUser(email));
        Map<Long, GroceryAccumulator> accumulator = new LinkedHashMap<>();
        for (MealPlanItemEntity item : plan.getItems()) {
            if (item.getItemType() == MealPlanItemType.FOOD_ITEM && item.getFoodItem() != null) {
                addFood(accumulator, item.getFoodItem(),
                        toGrams(item.getPortionSize(), item.getPortionUnit(), item.getFoodItem()),
                        item.getPortionSize(), item.getPortionUnit(), 1);
            } else if (item.getItemType() == MealPlanItemType.RECIPE && item.getRecipe() != null) {
                double servings = item.getServingCount() == null ? 1.0 : item.getServingCount();
                double recipeServingGrams = item.getRecipe().getDefaultServingGrams() == null
                        ? 0.0
                        : item.getRecipe().getDefaultServingGrams();
                double factor = recipeServingGrams <= 0 || item.getRecipe().getTotalYieldGrams() == null || item.getRecipe().getTotalYieldGrams() <= 0
                        ? servings
                        : (recipeServingGrams * servings) / item.getRecipe().getTotalYieldGrams();
                for (RecipeIngredientEntity ingredient : item.getRecipe().getIngredients()) {
                    double ingredientGrams = safe(ingredient.getNormalizedPortionGrams()) * factor;
                    addFood(accumulator, ingredient.getFoodItem(), ingredientGrams, ingredientGrams, FoodPortionUnit.GRAM, 1);
                }
            }
        }

        GroceryListDto dto = new GroceryListDto();
        dto.setMealPlanId(plan.getId());
        dto.setMealPlanName(plan.getName());
        dto.setItems(accumulator.values().stream()
                .map(GroceryAccumulator::toDto)
                .sorted(Comparator.comparing(GroceryListItemDto::getName, String.CASE_INSENSITIVE_ORDER))
                .toList());
        return dto;
    }

    @Override
    @Transactional
    public void archiveMealPlan(String email, Long planId) {
        MealPlanEntity plan = getOwnedPlan(planId, getUser(email));
        plan.setStatus(MealPlanStatus.ARCHIVED);
        mealPlanRepository.save(plan);
    }

    private void applyRequest(MealPlanEntity plan, MealPlanRequestDto request, UserEntity user) {
        plan.setName(request.getName().trim());
        plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate());
        applyGenerationMode(plan, request, user);
        if (plan.getStatus() == null) {
            plan.setStatus(MealPlanStatus.DRAFT);
        }
        List<MealPlanItemEntity> items = new ArrayList<>();
        int order = 0;
        for (MealPlanItemRequestDto itemRequest : request.getItems()) {
            validateItem(itemRequest, request.getStartDate(), request.getEndDate());
            MealPlanItemEntity item = new MealPlanItemEntity();
            item.setMealPlan(plan);
            item.setPlanDate(itemRequest.getPlanDate());
            item.setMealType(itemRequest.getMealType().trim().toUpperCase(java.util.Locale.ROOT));
            item.setItemType(itemRequest.getItemType());
            item.setItemOrder(order++);
            if (itemRequest.getItemType() == MealPlanItemType.FOOD_ITEM) {
                FoodItemEntity foodItem = foodItemRepository.findById(itemRequest.getFoodItemId())
                        .orElseThrow(() -> new ResourceNotFoundException("Food item not found"));
                if (!isVisibleToUser(foodItem, user)) {
                    throw new ResourceNotFoundException("Food item not found");
                }
                item.setFoodItem(foodItem);
                item.setPortionSize(itemRequest.getPortionSize());
                item.setPortionUnit(itemRequest.getPortionUnit());
                setNutritionSnapshot(item, catalogNutritionSnapshot(foodItem, item.getPortionSize(), item.getPortionUnit()));
            } else if (itemRequest.getItemType() == MealPlanItemType.RECIPE) {
                RecipeEntity recipe = recipeRepository.findAccessibleRecipe(itemRequest.getRecipeId(), user)
                        .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
                item.setRecipe(recipe);
                item.setServingCount(itemRequest.getServingCount() == null ? 1.0 : itemRequest.getServingCount());
            } else if (itemRequest.getItemType() == MealPlanItemType.AI_SNAPSHOT) {
                applySnapshotItem(item, itemRequest, user);
            }
            items.add(item);
        }
        plan.getItems().addAll(items);
    }

    private void validateRequest(MealPlanRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Meal plan request is required.");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("Meal plan end date must not be before start date.");
        }
        long days = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1;
        if (days > MAX_PLAN_DAYS) {
            throw new IllegalArgumentException("Meal plan can cover at most " + MAX_PLAN_DAYS + " days.");
        }
        if (request.getItems().size() > MAX_ITEMS) {
            throw new IllegalArgumentException("Meal plan can contain at most " + MAX_ITEMS + " items.");
        }
    }

    private void validateItem(MealPlanItemRequestDto item, LocalDate startDate, LocalDate endDate) {
        if (item.getPlanDate().isBefore(startDate) || item.getPlanDate().isAfter(endDate)) {
            throw new IllegalArgumentException("Meal plan item date must be inside the plan date range.");
        }
        if (item.getItemType() == MealPlanItemType.FOOD_ITEM) {
            if (item.getFoodItemId() == null || item.getRecipeId() != null) {
                throw new IllegalArgumentException("FOOD_ITEM plan items require only foodItemId.");
            }
            if (item.getPortionSize() == null || item.getPortionUnit() == null) {
                throw new IllegalArgumentException("FOOD_ITEM plan items require portion size and unit.");
            }
        } else if (item.getItemType() == MealPlanItemType.RECIPE) {
            if (item.getRecipeId() == null || item.getFoodItemId() != null) {
                throw new IllegalArgumentException("RECIPE plan items require only recipeId.");
            }
        } else if (item.getItemType() == MealPlanItemType.AI_SNAPSHOT) {
            validateSnapshotItem(item);
        } else {
            throw new IllegalArgumentException("Unsupported meal plan item type.");
        }
    }

    private MealPlanDto toDto(MealPlanEntity plan) {
        MealPlanDto dto = new MealPlanDto();
        dto.setId(plan.getId());
        dto.setName(plan.getName());
        dto.setStartDate(plan.getStartDate());
        dto.setEndDate(plan.getEndDate());
        dto.setStatus(plan.getStatus());

        dto.setGenerationMode(plan.getGenerationMode());
        dto.setWorkoutPlanId(plan.getWorkoutPlan() == null ? null : plan.getWorkoutPlan().getId());
        dto.setSourceAiRequestId(plan.getSourceAiRequest() == null ? null : plan.getSourceAiRequest().getId());
        dto.setSchemaVersion(plan.getSchemaVersion());
        dto.setPromptVersion(plan.getPromptVersion());
        dto.setCreatedAt(plan.getCreatedAt());
        dto.setUpdatedAt(plan.getUpdatedAt());
        dto.setItems(plan.getItems().stream().map(this::toItemDto).toList());
        dto.setAiDayNutrition(aiDayNutrition(plan));
        return dto;
    }

    private List<MealPlanDayNutritionDto> aiDayNutrition(MealPlanEntity plan) {
        if (plan.getSourceAiRequest() == null
                || plan.getSourceAiRequest().getOutputPayload() == null
                || plan.getSourceAiRequest().getOutputPayload().isBlank()) {
            return List.of();
        }
        try {
            AiNutritionPlanDraftResponseDto draft = objectMapper.readValue(
                    plan.getSourceAiRequest().getOutputPayload(),
                    AiNutritionPlanDraftResponseDto.class);
            if (draft.getDays() == null) {
                return List.of();
            }
            return draft.getDays().stream().map(day -> {
                MealPlanDayNutritionDto summary = new MealPlanDayNutritionDto();
                summary.setDate(day.getDate());
                summary.setDayType(day.getDayType());
                summary.setTotalNutrition(day.getTotalNutrition());
                return summary;
            }).toList();
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private MealPlanItemDto toItemDto(MealPlanItemEntity item) {
        MealPlanItemDto dto = new MealPlanItemDto();
        dto.setId(item.getId());
        dto.setPlanDate(item.getPlanDate());
        dto.setMealType(item.getMealType());
        dto.setItemType(item.getItemType());
        dto.setPortionSize(item.getPortionSize());
        dto.setPortionUnit(item.getPortionUnit());
        dto.setServingCount(item.getServingCount());
        dto.setItemOrder(item.getItemOrder());

        dto.setLinkState(item.getLinkState());
        dto.setSnapshotName(item.getSnapshotName());
        dto.setSnapshotDescription(item.getSnapshotDescription());
        dto.setShortPreparationState(item.getShortPreparationState());
        dto.setSnapshotNutrition(toNutritionSnapshot(item));
        dto.setAllergens(readStringList(item.getAllergensPayload()));
        dto.setWarnings(readStringList(item.getWarningsPayload()));
        dto.setAssumptions(readStringList(item.getAssumptionsPayload()));
        dto.setWorkoutRelation(item.getWorkoutRelation());
        dto.setSourceAiRequestId(item.getSourceAiRequest() == null ? null : item.getSourceAiRequest().getId());
        dto.setSchemaVersion(item.getSchemaVersion());
        dto.setPromptVersion(item.getPromptVersion());
        if (item.getFoodItem() != null) {
            dto.setFoodItemId(item.getFoodItem().getId());
            dto.setFoodItemName(item.getFoodItem().getName());
        }
        if (isRecipeNavigable(item.getRecipe(), item.getMealPlan().getUser())) {
            dto.setRecipeId(item.getRecipe().getId());
            dto.setRecipeName(FoodProductNormalizationRules.normalizeProductDisplayName(item.getRecipe().getName()));
            dto.setRecipeNavigationAvailable(true);
            dto.setRecipeOwnedByUser(item.getRecipe().getOwnerUser() != null
                    && item.getRecipe().getOwnerUser().getId().equals(item.getMealPlan().getUser().getId()));
        }
        return dto;
    }

    private boolean isRecipeNavigable(RecipeEntity recipe, UserEntity user) {
        if (recipe == null || Boolean.TRUE.equals(recipe.getArchived())) {
            return false;
        }
        if (recipe.getOwnerUser() != null && recipe.getOwnerUser().getId().equals(user.getId())) {
            return true;
        }
        if (recipe.getVisibility() != RecipeVisibility.PUBLIC_ADMIN
                || recipe.getVerificationStatus() != VerificationStatus.VERIFIED) {
            return false;
        }
        boolean marketMatches = recipe.getMarketRegion() == MarketRegion.GLOBAL
                || recipe.getMarketRegion() != null && recipe.getMarketRegion() == user.getMarketRegion();
        boolean languageMatches = recipe.getLanguage() == null || recipe.getLanguage().isBlank()
                || user.getPreferredLanguage() != null
                && recipe.getLanguage().equalsIgnoreCase(user.getPreferredLanguage().name());
        return marketMatches && languageMatches;
    }

    private void applyGenerationMode(MealPlanEntity plan, MealPlanRequestDto request, UserEntity user) {
        NutritionPlanGenerationMode mode = request.getGenerationMode();
        if (mode == null) {
            if (request.getWorkoutPlanId() != null) {
                throw new IllegalArgumentException("workoutPlanId requires WORKOUT_ALIGNED generation mode.");
            }
            return;
        }
        plan.setGenerationMode(mode);
        if (mode == NutritionPlanGenerationMode.GENERAL) {
            if (request.getWorkoutPlanId() != null) {
                throw new IllegalArgumentException("GENERAL nutrition plans cannot reference a workout plan.");
            }
            plan.setWorkoutPlan(null);
            return;
        }
        if (request.getWorkoutPlanId() == null) {
            throw new IllegalArgumentException("WORKOUT_ALIGNED nutrition plans require workoutPlanId.");
        }
        WorkoutPlanEntity workoutPlan = workoutPlanRepository.findByIdAndUser(request.getWorkoutPlanId(), user)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getActive()))
                .filter(candidate -> candidate.getStatus() == WorkoutPlanStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Workout plan not found"));
        plan.setWorkoutPlan(workoutPlan);
    }

    private void applySnapshotItem(MealPlanItemEntity item, MealPlanItemRequestDto request, UserEntity user) {
        String snapshotName = FoodProductNormalizationRules.normalizeProductDisplayName(request.getSnapshotName());
        item.setSnapshotName(snapshotName);
        item.setSnapshotDescription(trimToNull(request.getSnapshotDescription()));
        item.setShortPreparationState(trimToNull(request.getShortPreparationState()));
        item.setPortionSize(request.getPortionSize());
        item.setPortionUnit(request.getPortionUnit());
        item.setWorkoutRelation(request.getWorkoutRelation() == null ? MealPlanWorkoutRelation.NONE : request.getWorkoutRelation());
        item.setLinkState(MealPlanItemLinkState.NONE);
        item.setSchemaVersion("meal_plan_item_v1");
        setNutritionSnapshot(item, request.getSnapshotNutrition());
        item.setAllergensPayload(writeStringList(request.getAllergens()));
        item.setWarningsPayload(writeStringList(request.getWarnings()));
        item.setAssumptionsPayload(writeStringList(request.getAssumptions()));
        item.setSnapshotPayload(writeJson(request));

        if (request.getFoodItemId() != null) {
            FoodItemEntity foodItem = foodItemRepository.findById(request.getFoodItemId())
                    .filter(candidate -> isVisibleToUser(candidate, user))
                    .orElseThrow(() -> new ResourceNotFoundException("Food item not found"));
            item.setFoodItem(foodItem);
            item.setLinkState(MealPlanItemLinkState.USER_CONFIRMED);
        } else if (request.getRecipeId() != null) {
            RecipeEntity recipe = recipeRepository.findAccessibleRecipe(request.getRecipeId(), user)
                    .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
            item.setRecipe(recipe);
            item.setLinkState(MealPlanItemLinkState.USER_CONFIRMED);
        }
    }

    private void validateSnapshotItem(MealPlanItemRequestDto item) {
        if (item.getFoodItemId() != null && item.getRecipeId() != null) {
            throw new IllegalArgumentException("AI_SNAPSHOT can link to at most one food item or recipe.");
        }
        if (item.getSnapshotName() == null || item.getSnapshotName().isBlank() || item.getSnapshotName().length() > 255) {
            throw new IllegalArgumentException("AI_SNAPSHOT requires a valid snapshot name.");
        }

        if (item.getSnapshotDescription() != null && item.getSnapshotDescription().length() > 2000) {
            throw new IllegalArgumentException("AI_SNAPSHOT description is too long.");
        }
        if (item.getShortPreparationState() != null && item.getShortPreparationState().length() > 120) {
            throw new IllegalArgumentException("AI_SNAPSHOT preparation state is too long.");
        }
        if (item.getPortionSize() == null || !Double.isFinite(item.getPortionSize())
                || item.getPortionSize() <= 0 || item.getPortionSize() > 100_000 || item.getPortionUnit() == null) {
            throw new IllegalArgumentException("AI_SNAPSHOT requires a positive portion size and unit.");
        }
        MealPlanNutritionSnapshotDto nutrition = item.getSnapshotNutrition();
        if (nutrition == null || nutrition.getCalories() == null || nutrition.getProtein() == null
                || nutrition.getCarbs() == null || nutrition.getFat() == null) {
            throw new IllegalArgumentException("AI_SNAPSHOT requires calories, protein, carbs, and fat.");
        }
        requireNonNegative(nutrition.getCalories(), "snapshot calories");
        requireNonNegative(nutrition.getProtein(), "snapshot protein");
        requireNonNegative(nutrition.getCarbs(), "snapshot carbs");
        requireNonNegative(nutrition.getFat(), "snapshot fat");
        requireNonNegative(nutrition.getFiber(), "snapshot fiber");
        requireNonNegative(nutrition.getSugar(), "snapshot sugar");
        requireNonNegative(nutrition.getSaturatedFat(), "snapshot saturated fat");
        requireNonNegative(nutrition.getSodium(), "snapshot sodium");
        requireNonNegative(nutrition.getPotassium(), "snapshot potassium");
        requireNonNegative(nutrition.getCholesterol(), "snapshot cholesterol");
        requireNonNegative(nutrition.getCalcium(), "snapshot calcium");
        requireNonNegative(nutrition.getIron(), "snapshot iron");
        requireNonNegative(nutrition.getMagnesium(), "snapshot magnesium");
        requireNonNegative(nutrition.getZinc(), "snapshot zinc");
        requireNonNegative(nutrition.getVitaminA(), "snapshot vitamin A");
        requireNonNegative(nutrition.getVitaminC(), "snapshot vitamin C");
        requireNonNegative(nutrition.getVitaminD(), "snapshot vitamin D");
        requireNonNegative(nutrition.getVitaminE(), "snapshot vitamin E");
        requireNonNegative(nutrition.getVitaminB12(), "snapshot vitamin B12");
        validateTextList(item.getAllergens(), "allergens");
        validateTextList(item.getWarnings(), "warnings");
        validateTextList(item.getAssumptions(), "assumptions");
    }

    private void validateTextList(List<String> values, String field) {
        if (values == null) {
            return;
        }
        if (values.size() > 20 || values.stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 300)) {
            throw new IllegalArgumentException(field + " contains invalid values.");
        }
    }

    private void requireNonNegative(Double value, String field) {
        if (value != null && (!Double.isFinite(value) || value < 0)) {
            throw new IllegalArgumentException(field + " must be a finite non-negative value.");
        }
    }

    private void setNutritionSnapshot(MealPlanItemEntity item, MealPlanNutritionSnapshotDto nutrition) {
        item.setSnapshotCalories(nutrition.getCalories());
        item.setSnapshotProtein(nutrition.getProtein());
        item.setSnapshotCarbs(nutrition.getCarbs());
        item.setSnapshotFat(nutrition.getFat());
        item.setSnapshotFiber(nutrition.getFiber());
        item.setSnapshotSugar(nutrition.getSugar());
        item.setSnapshotSaturatedFat(nutrition.getSaturatedFat());
        item.setSnapshotSodium(nutrition.getSodium());
        item.setSnapshotPotassium(nutrition.getPotassium());
        item.setSnapshotCholesterol(nutrition.getCholesterol());
        item.setSnapshotCalcium(nutrition.getCalcium());
        item.setSnapshotIron(nutrition.getIron());
        item.setSnapshotMagnesium(nutrition.getMagnesium());
        item.setSnapshotZinc(nutrition.getZinc());
        item.setSnapshotVitaminA(nutrition.getVitaminA());
        item.setSnapshotVitaminC(nutrition.getVitaminC());
        item.setSnapshotVitaminD(nutrition.getVitaminD());
        item.setSnapshotVitaminE(nutrition.getVitaminE());
        item.setSnapshotVitaminB12(nutrition.getVitaminB12());
    }

    private MealPlanNutritionSnapshotDto toNutritionSnapshot(MealPlanItemEntity item) {
        if (item.getSnapshotCalories() == null && item.getFoodItem() != null) {
            return catalogNutritionSnapshot(item.getFoodItem(), item.getPortionSize(), item.getPortionUnit());
        }
        if (item.getItemType() != MealPlanItemType.AI_SNAPSHOT && item.getSnapshotCalories() == null) {
            return null;
        }
        return new MealPlanNutritionSnapshotDto(
                item.getSnapshotCalories(), item.getSnapshotProtein(), item.getSnapshotCarbs(), item.getSnapshotFat(),
                item.getSnapshotFiber(), item.getSnapshotSugar(), item.getSnapshotSaturatedFat(), item.getSnapshotSodium(),
                item.getSnapshotPotassium(), item.getSnapshotCholesterol(), item.getSnapshotCalcium(), item.getSnapshotIron(),
                item.getSnapshotMagnesium(), item.getSnapshotZinc(), item.getSnapshotVitaminA(), item.getSnapshotVitaminC(),
                item.getSnapshotVitaminD(), item.getSnapshotVitaminE(), item.getSnapshotVitaminB12()
        );
    }

    private MealPlanNutritionSnapshotDto catalogNutritionSnapshot(
            FoodItemEntity foodItem,
            Double portionSize,
            FoodPortionUnit portionUnit
    ) {
        double grams = toGrams(portionSize, portionUnit, foodItem);
        return new MealPlanNutritionSnapshotDto(
                scaledNutrition(foodItem.getCalories(), grams), scaledNutrition(foodItem.getProtein(), grams),
                scaledNutrition(foodItem.getCarbs(), grams), scaledNutrition(foodItem.getFat(), grams),
                scaledNutrition(foodItem.getFiber(), grams), scaledNutrition(foodItem.getSugar(), grams),
                scaledNutrition(foodItem.getSaturatedFat(), grams), scaledNutrition(foodItem.getSodium(), grams),
                scaledNutrition(foodItem.getPotassium(), grams), scaledNutrition(foodItem.getCholesterol(), grams),
                scaledNutrition(foodItem.getCalcium(), grams), scaledNutrition(foodItem.getIron(), grams),
                scaledNutrition(foodItem.getMagnesium(), grams), scaledNutrition(foodItem.getZinc(), grams),
                scaledNutrition(foodItem.getVitaminA(), grams), scaledNutrition(foodItem.getVitaminC(), grams),
                scaledNutrition(foodItem.getVitaminD(), grams), scaledNutrition(foodItem.getVitaminE(), grams),
                scaledNutrition(foodItem.getVitaminB12(), grams)
        );
    }

    private Double scaledNutrition(Double perHundredGrams, double grams) {
        if (perHundredGrams == null) {
            return null;
        }
        return Math.round(perHundredGrams * grams) / 100.0;
    }
    private void copySnapshotMetadata(MealPlanItemEntity source, MealPlanItemEntity target) {
        target.setLinkState(source.getLinkState());
        target.setSnapshotName(source.getSnapshotName());
        target.setSnapshotDescription(source.getSnapshotDescription());
        target.setShortPreparationState(source.getShortPreparationState());
        target.setSnapshotCalories(source.getSnapshotCalories());
        target.setSnapshotProtein(source.getSnapshotProtein());
        target.setSnapshotCarbs(source.getSnapshotCarbs());
        target.setSnapshotFat(source.getSnapshotFat());
        target.setSnapshotFiber(source.getSnapshotFiber());
        target.setSnapshotSugar(source.getSnapshotSugar());
        target.setSnapshotSaturatedFat(source.getSnapshotSaturatedFat());
        target.setSnapshotSodium(source.getSnapshotSodium());
        target.setSnapshotPotassium(source.getSnapshotPotassium());
        target.setSnapshotCholesterol(source.getSnapshotCholesterol());
        target.setSnapshotCalcium(source.getSnapshotCalcium());
        target.setSnapshotIron(source.getSnapshotIron());
        target.setSnapshotMagnesium(source.getSnapshotMagnesium());
        target.setSnapshotZinc(source.getSnapshotZinc());
        target.setSnapshotVitaminA(source.getSnapshotVitaminA());
        target.setSnapshotVitaminC(source.getSnapshotVitaminC());
        target.setSnapshotVitaminD(source.getSnapshotVitaminD());
        target.setSnapshotVitaminE(source.getSnapshotVitaminE());
        target.setSnapshotVitaminB12(source.getSnapshotVitaminB12());
        target.setAllergensPayload(source.getAllergensPayload());
        target.setWarningsPayload(source.getWarningsPayload());
        target.setAssumptionsPayload(source.getAssumptionsPayload());
        target.setSnapshotPayload(source.getSnapshotPayload());
        target.setWorkoutRelation(source.getWorkoutRelation());
        target.setSourceAiRequest(source.getSourceAiRequest());
        target.setSchemaVersion(source.getSchemaVersion());
        target.setPromptVersion(source.getPromptVersion());
    }

    private String writeStringList(List<String> values) {
        return values == null || values.isEmpty() ? null : writeJson(values);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Meal plan snapshot could not be serialized.");
        }
    }

    private List<String> readStringList(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private MealPlanEntity getOwnedPlan(Long planId, UserEntity user) {
        return mealPlanRepository.findByIdAndUser(planId, user)
                .filter(plan -> plan.getStatus() != MealPlanStatus.ARCHIVED)
                .orElseThrow(() -> new ResourceNotFoundException("Meal plan not found"));
    }

    private boolean isVisibleToUser(FoodItemEntity foodItem, UserEntity user) {
        if (foodItem.getVerificationStatus() == VerificationStatus.REJECTED) {
            return false;
        }
        if (!Boolean.TRUE.equals(foodItem.getIsCustom())) {
            return true;
        }
        return foodItem.getCreatedByUser() != null
                && user.getId() != null
                && user.getId().equals(foodItem.getCreatedByUser().getId());
    }

    private void addFood(
            Map<Long, GroceryAccumulator> accumulator,
            FoodItemEntity foodItem,
            double grams,
            Double quantity,
            FoodPortionUnit unit,
            int uses
    ) {
        if (foodItem == null || foodItem.getId() == null || grams <= 0 || quantity == null || quantity <= 0) {
            return;
        }
        accumulator.computeIfAbsent(foodItem.getId(), ignored -> new GroceryAccumulator(foodItem.getId(), foodItem.getName()))
                .add(grams, quantity, FoodPortionCalculator.resolveUnit(unit), uses);
    }
    private double toGrams(Double portionSize, FoodPortionUnit unit, FoodItemEntity foodItem) {
        Double grams = FoodPortionCalculator.normalizeToGrams(portionSize, unit, foodItem);
        return grams == null ? 0.0 : grams;
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private static class GroceryAccumulator {
        private final Long foodItemId;
        private final String name;
        private double grams;
        private double quantity;
        private FoodPortionUnit quantityUnit;
        private boolean mixedUnits;
        private int uses;

        private GroceryAccumulator(Long foodItemId, String name) {
            this.foodItemId = foodItemId;
            this.name = name;
        }

        private GroceryAccumulator add(double grams, double quantity, FoodPortionUnit unit, int uses) {
            this.grams += grams;
            this.quantity += quantity;
            if (quantityUnit == null) {
                quantityUnit = unit;
            } else if (quantityUnit != unit) {
                mixedUnits = true;
            }
            this.uses += uses;
            return this;
        }

        private GroceryListItemDto toDto() {
            double roundedGrams = Math.round(grams * 10.0) / 10.0;
            double displayQuantity = mixedUnits ? roundedGrams : Math.round(quantity * 10.0) / 10.0;
            FoodPortionUnit displayUnit = mixedUnits ? FoodPortionUnit.GRAM : quantityUnit;
            return new GroceryListItemDto(foodItemId, name, roundedGrams, displayQuantity, displayUnit, uses);
        }
    }
}
