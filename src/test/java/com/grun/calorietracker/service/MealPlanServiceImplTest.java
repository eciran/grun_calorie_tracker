package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.MealPlanNutritionSnapshotDto;
import com.grun.calorietracker.entity.WorkoutPlanEntity;
import com.grun.calorietracker.enums.NutritionPlanGenerationMode;
import com.grun.calorietracker.enums.WorkoutPlanStatus;
import com.grun.calorietracker.repository.WorkoutPlanRepository;

import com.grun.calorietracker.dto.GroceryListDto;
import com.grun.calorietracker.dto.MealPlanDto;
import com.grun.calorietracker.dto.MealPlanDuplicateRequestDto;
import com.grun.calorietracker.dto.MealPlanItemRequestDto;
import com.grun.calorietracker.dto.MealPlanRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.MealPlanEntity;
import com.grun.calorietracker.entity.MealPlanItemEntity;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.RecipeIngredientEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.MealPlanItemType;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.MealPlanRepository;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.MealPlanServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MealPlanServiceImplTest {

    private final MealPlanRepository mealPlanRepository = mock(MealPlanRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FoodItemRepository foodItemRepository = mock(FoodItemRepository.class);
    private final RecipeRepository recipeRepository = mock(RecipeRepository.class);
    private final WorkoutPlanRepository workoutPlanRepository = mock(WorkoutPlanRepository.class);
    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final MealPlanServiceImpl service = new MealPlanServiceImpl(
            mealPlanRepository,
            userRepository,
            foodItemRepository,
            recipeRepository,
            workoutPlanRepository,
            new ObjectMapper().findAndRegisterModules(),
            subscriptionService
    );

    @Test
    void createMealPlan_whenRecipeAndFoodItemsProvided_createsPlan() {
        UserEntity user = user();
        FoodItemEntity yogurt = food(10L, "Greek yogurt");
        yogurt.setCalories(60.0);
        yogurt.setProtein(10.0);
        RecipeEntity recipe = recipe(20L, "Chicken bowl", food(11L, "Chicken"), 180.0);

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(foodItemRepository.findById(10L)).thenReturn(Optional.of(yogurt));
        when(recipeRepository.findAccessibleRecipe(20L, user)).thenReturn(Optional.of(recipe));
        when(mealPlanRepository.save(any(MealPlanEntity.class))).thenAnswer(invocation -> {
            MealPlanEntity plan = invocation.getArgument(0);
            plan.setId(99L);
            return plan;
        });

        MealPlanDto result = service.createMealPlan("user@test.com", request());

        assertEquals(99L, result.getId());
        assertEquals("High protein week", result.getName());
        assertEquals(2, result.getItems().size());
        assertEquals(MealPlanItemType.FOOD_ITEM, result.getItems().get(0).getItemType());
        assertEquals(90.0, result.getItems().get(0).getSnapshotNutrition().getCalories());
        assertEquals(15.0, result.getItems().get(0).getSnapshotNutrition().getProtein());
        assertEquals(MealPlanItemType.RECIPE, result.getItems().get(1).getItemType());
    }

    @Test
    void createMealPlan_whenFoodItemIsAnotherUsersCustomFood_rejects() {
        UserEntity user = user();
        UserEntity owner = new UserEntity();
        owner.setId(2L);
        owner.setEmail("owner@test.com");
        FoodItemEntity privateFood = food(10L, "Private soup");
        privateFood.setIsCustom(true);
        privateFood.setCreatedByUser(owner);

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(foodItemRepository.findById(10L)).thenReturn(Optional.of(privateFood));

        assertThrows(ResourceNotFoundException.class, () -> service.createMealPlan("user@test.com", request()));
    }

    @Test
    void getGroceryList_aggregatesFoodItemsAndRecipeIngredients() {
        UserEntity user = user();
        FoodItemEntity yogurt = food(10L, "Greek yogurt");
        FoodItemEntity chicken = food(11L, "Chicken");
        RecipeEntity recipe = recipe(20L, "Chicken bowl", chicken, 180.0);
        MealPlanEntity plan = new MealPlanEntity();
        plan.setId(99L);
        plan.setUser(user);
        plan.setName("High protein week");
        plan.setStartDate(LocalDate.of(2026, 6, 15));
        plan.setEndDate(LocalDate.of(2026, 6, 21));
        plan.getItems().add(foodItem(plan, yogurt, 150.0));
        plan.getItems().add(recipeItem(plan, recipe, 2.0));

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(mealPlanRepository.findByIdAndUser(99L, user)).thenReturn(Optional.of(plan));

        GroceryListDto result = service.getGroceryList("user@test.com", 99L);

        verify(subscriptionService).assertFeatureAccess("user@test.com", SubscriptionFeature.GROCERY_LIST);
        assertEquals(99L, result.getMealPlanId());
        assertEquals(2, result.getItems().size());
        assertEquals("Chicken", result.getItems().get(0).getName());
        assertEquals(360.0, result.getItems().get(0).getTotalGrams());
        assertEquals("Greek yogurt", result.getItems().get(1).getName());
        assertEquals(150.0, result.getItems().get(1).getTotalGrams());
    }

    @Test
    void getGroceryList_includesLinkedAndUnlinkedAiSnapshots() {
        UserEntity user = user();
        FoodItemEntity yogurt = food(10L, "Greek yogurt");
        MealPlanEntity plan = new MealPlanEntity();
        plan.setId(99L);
        plan.setUser(user);
        plan.setName("AI nutrition week");

        MealPlanItemEntity linked = new MealPlanItemEntity();
        linked.setItemType(MealPlanItemType.AI_SNAPSHOT);
        linked.setFoodItem(yogurt);
        linked.setSnapshotName("Greek yogurt");
        linked.setPortionSize(200.0);
        linked.setPortionUnit(FoodPortionUnit.GRAM);
        plan.getItems().add(linked);

        for (int i = 0; i < 2; i++) {
            MealPlanItemEntity snapshot = new MealPlanItemEntity();
            snapshot.setItemType(MealPlanItemType.AI_SNAPSHOT);
            snapshot.setSnapshotName(i == 0 ? "Baked salmon" : "Grilled salmon");
            snapshot.setGroceryName("Salmon");
            snapshot.setPreparationMethod(i == 0 ? FoodPreparationState.BAKED : FoodPreparationState.GRILLED);
            snapshot.setPortionSize(200.0);
            snapshot.setPortionUnit(FoodPortionUnit.GRAM);
            plan.getItems().add(snapshot);
        }

        MealPlanItemEntity potatoes = new MealPlanItemEntity();
        potatoes.setItemType(MealPlanItemType.AI_SNAPSHOT);
        potatoes.setSnapshotName("Boiled potatoes");
        potatoes.setGroceryName("Potatoes");
        potatoes.setPreparationMethod(FoodPreparationState.BOILED);
        potatoes.setPortionSize(300.0);
        potatoes.setPortionUnit(FoodPortionUnit.GRAM);
        plan.getItems().add(potatoes);

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(mealPlanRepository.findByIdAndUser(99L, user)).thenReturn(Optional.of(plan));

        GroceryListDto result = service.getGroceryList("user@test.com", 99L);

        assertEquals(3, result.getItems().size());
        var linkedItem = result.getItems().stream()
                .filter(item -> "Greek yogurt".equals(item.getName()))
                .findFirst().orElseThrow();
        assertEquals(10L, linkedItem.getFoodItemId());
        assertEquals(200.0, linkedItem.getTotalGrams());

        var snapshotItem = result.getItems().stream()
                .filter(item -> "Salmon".equals(item.getName()))
                .findFirst().orElseThrow();
        assertEquals(null, snapshotItem.getFoodItemId());
        assertEquals(400.0, snapshotItem.getTotalGrams());
        assertEquals(2, snapshotItem.getPlannedUses());

        var potatoesItem = result.getItems().stream()
                .filter(item -> "Potatoes".equals(item.getName()))
                .findFirst().orElseThrow();
        assertEquals(300.0, potatoesItem.getTotalGrams());
        assertEquals(1, potatoesItem.getPlannedUses());
    }
    @Test
    void getGroceryList_whenFoodItemUsesServing_usesProductServingSize() {
        UserEntity user = user();
        FoodItemEntity yogurt = food(10L, "Greek yogurt");
        yogurt.setServingSizeGrams(170.0);
        MealPlanEntity plan = new MealPlanEntity();
        plan.setId(99L);
        plan.setUser(user);
        plan.setName("High protein week");
        plan.setStartDate(LocalDate.of(2026, 6, 15));
        plan.setEndDate(LocalDate.of(2026, 6, 21));
        var item = foodItem(plan, yogurt, 2.0);
        item.setPortionUnit(FoodPortionUnit.SERVING);
        plan.getItems().add(item);

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(mealPlanRepository.findByIdAndUser(99L, user)).thenReturn(Optional.of(plan));

        GroceryListDto result = service.getGroceryList("user@test.com", 99L);

        assertEquals(340.0, result.getItems().get(0).getTotalGrams());
        assertEquals(2.0, result.getItems().get(0).getTotalQuantity());
        assertEquals(FoodPortionUnit.SERVING, result.getItems().get(0).getQuantityUnit());
        assertEquals(1, result.getItems().get(0).getPlannedUses());
    }

    @Test
    void getGroceryList_whenFoodUsesPieces_preservesPieceDisplayAndNormalizesGrams() {
        UserEntity user = user();
        FoodItemEntity banana = food(12L, "Banana");
        banana.setServingSizeGrams(80.0);
        MealPlanEntity plan = new MealPlanEntity();
        plan.setId(99L);
        plan.setUser(user);
        plan.setName("Piece plan");
        var item = foodItem(plan, banana, 3.0);
        item.setPortionUnit(FoodPortionUnit.PIECE);
        plan.getItems().add(item);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(mealPlanRepository.findByIdAndUser(99L, user)).thenReturn(Optional.of(plan));

        GroceryListDto result = service.getGroceryList("user@test.com", 99L);

        assertEquals(240.0, result.getItems().get(0).getTotalGrams());
        assertEquals(3.0, result.getItems().get(0).getTotalQuantity());
        assertEquals(FoodPortionUnit.PIECE, result.getItems().get(0).getQuantityUnit());
    }

    @Test
    void getGroceryList_whenSameFoodUsesMixedUnits_fallsBackToNormalizedGrams() {
        UserEntity user = user();
        FoodItemEntity yogurt = food(10L, "Greek yogurt");
        yogurt.setServingSizeGrams(170.0);
        MealPlanEntity plan = new MealPlanEntity();
        plan.setId(99L);
        plan.setUser(user);
        plan.setName("Mixed unit plan");
        var grams = foodItem(plan, yogurt, 100.0);
        var serving = foodItem(plan, yogurt, 1.0);
        serving.setPortionUnit(FoodPortionUnit.SERVING);
        plan.getItems().add(grams);
        plan.getItems().add(serving);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(mealPlanRepository.findByIdAndUser(99L, user)).thenReturn(Optional.of(plan));

        GroceryListDto result = service.getGroceryList("user@test.com", 99L);

        assertEquals(270.0, result.getItems().get(0).getTotalGrams());
        assertEquals(270.0, result.getItems().get(0).getTotalQuantity());
        assertEquals(FoodPortionUnit.GRAM, result.getItems().get(0).getQuantityUnit());
        assertEquals(2, result.getItems().get(0).getPlannedUses());
    }
    @Test
    void duplicateMealPlan_copiesItemsToNewDateRange() {
        UserEntity user = user();
        FoodItemEntity yogurt = food(10L, "Greek yogurt");
        MealPlanEntity source = new MealPlanEntity();
        source.setId(99L);
        source.setUser(user);
        source.setName("Current week");
        source.setStartDate(LocalDate.of(2026, 6, 15));
        source.setEndDate(LocalDate.of(2026, 6, 21));
        source.getItems().add(foodItem(source, yogurt, 150.0));

        MealPlanDuplicateRequestDto request = new MealPlanDuplicateRequestDto();
        request.setName("Next week");
        request.setStartDate(LocalDate.of(2026, 6, 22));

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(mealPlanRepository.findByIdAndUser(99L, user)).thenReturn(Optional.of(source));
        when(mealPlanRepository.save(any(MealPlanEntity.class))).thenAnswer(invocation -> {
            MealPlanEntity copy = invocation.getArgument(0);
            copy.setId(100L);
            return copy;
        });

        MealPlanDto result = service.duplicateMealPlan("user@test.com", 99L, request);

        assertEquals(100L, result.getId());
        assertEquals("Next week", result.getName());
        assertEquals(LocalDate.of(2026, 6, 22), result.getStartDate());
        assertEquals(LocalDate.of(2026, 6, 28), result.getEndDate());
        assertEquals(LocalDate.of(2026, 6, 22), result.getItems().get(0).getPlanDate());
    }


    @Test
    void createMealPlan_whenAiSnapshotHasNoCatalogMatch_persistsSnapshot() {
        UserEntity user = user();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(mealPlanRepository.save(any(MealPlanEntity.class))).thenAnswer(invocation -> {
            MealPlanEntity plan = invocation.getArgument(0);
            plan.setId(101L);
            return plan;
        });

        MealPlanDto result = service.createMealPlan("user@test.com", snapshotRequest());

        assertEquals(MealPlanItemType.AI_SNAPSHOT, result.getItems().get(0).getItemType());
        assertEquals("Grilled Chicken with Rice", result.getItems().get(0).getSnapshotName());
        assertEquals("Chicken and Rice", result.getItems().get(0).getGroceryName());
        assertEquals(FoodPreparationState.GRILLED, result.getItems().get(0).getPreparationMethod());
        assertEquals(520.0, result.getItems().get(0).getSnapshotNutrition().getCalories());
        assertEquals(null, result.getItems().get(0).getFoodItemId());
        assertEquals(null, result.getItems().get(0).getRecipeId());
    }

    @Test
    void updateMealPlan_whenAiGeneratedPlanAddsItem_rejects() {
        UserEntity user = user();
        MealPlanEntity plan = new MealPlanEntity();
        plan.setId(101L);
        plan.setUser(user);
        plan.setName("AI nutrition week");
        plan.setStartDate(LocalDate.of(2026, 7, 20));
        plan.setEndDate(LocalDate.of(2026, 7, 26));
        plan.getItems().add(new com.grun.calorietracker.entity.MealPlanItemEntity());
        plan.getItems().get(0).setItemType(MealPlanItemType.AI_SNAPSHOT);

        MealPlanRequestDto update = snapshotRequest();
        update.setItems(java.util.List.of(update.getItems().get(0), update.getItems().get(0)));

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(mealPlanRepository.findByIdAndUser(101L, user)).thenReturn(Optional.of(plan));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.updateMealPlan("user@test.com", 101L, update));

        assertEquals("Items cannot be added to an AI-generated meal plan.", error.getMessage());
    }

    @Test
    void createMealPlan_whenWorkoutAligned_usesOwnedActiveWorkout() {
        UserEntity user = user();
        WorkoutPlanEntity workout = new WorkoutPlanEntity();
        workout.setId(42L);
        workout.setUser(user);
        workout.setStatus(WorkoutPlanStatus.ACTIVE);
        workout.setActive(true);
        MealPlanRequestDto request = snapshotRequest();
        request.setGenerationMode(NutritionPlanGenerationMode.WORKOUT_ALIGNED);
        request.setWorkoutPlanId(42L);

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(workoutPlanRepository.findByIdAndUser(42L, user)).thenReturn(Optional.of(workout));
        when(mealPlanRepository.save(any(MealPlanEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MealPlanDto result = service.createMealPlan("user@test.com", request);

        assertEquals(NutritionPlanGenerationMode.WORKOUT_ALIGNED, result.getGenerationMode());
        assertEquals(42L, result.getWorkoutPlanId());
    }

    @Test
    void createMealPlan_whenWorkoutIsNotOwned_rejects() {
        UserEntity user = user();
        MealPlanRequestDto request = snapshotRequest();
        request.setGenerationMode(NutritionPlanGenerationMode.WORKOUT_ALIGNED);
        request.setWorkoutPlanId(42L);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(workoutPlanRepository.findByIdAndUser(42L, user)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createMealPlan("user@test.com", request));
    }

    @Test
    void createMealPlan_whenSnapshotNutritionIsNegative_rejects() {
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user()));
        MealPlanRequestDto request = snapshotRequest();
        request.getItems().get(0).getSnapshotNutrition().setCalories(-1.0);

        assertThrows(IllegalArgumentException.class, () -> service.createMealPlan("user@test.com", request));
    }

    @Test
    void createMealPlan_whenItemDateOutsideRange_rejects() {
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user()));
        MealPlanRequestDto request = request();
        request.getItems().get(0).setPlanDate(LocalDate.of(2026, 6, 30));

        assertThrows(IllegalArgumentException.class, () -> service.createMealPlan("user@test.com", request));
    }


    @Test
    void getMealPlan_whenAiPlan_exposesStoredDailyNutrition() throws Exception {
        UserEntity user = user();
        com.grun.calorietracker.dto.AiNutritionPlanDayDto day =
                new com.grun.calorietracker.dto.AiNutritionPlanDayDto();
        day.setDate(LocalDate.of(2026, 7, 20));
        day.setDayType(com.grun.calorietracker.enums.NutritionPlanDayType.TRAINING);
        MealPlanNutritionSnapshotDto total = new MealPlanNutritionSnapshotDto();
        total.setCalories(2100.0);
        total.setProtein(150.0);
        total.setCarbs(220.0);
        total.setFat(70.0);
        total.setSodium(1800.0);
        total.setVitaminC(75.0);
        day.setTotalNutrition(total);
        com.grun.calorietracker.dto.AiNutritionPlanDraftResponseDto draft =
                new com.grun.calorietracker.dto.AiNutritionPlanDraftResponseDto();
        draft.setDays(java.util.List.of(day));

        com.grun.calorietracker.entity.AiRequestHistoryEntity history =
                new com.grun.calorietracker.entity.AiRequestHistoryEntity();
        history.setId(77L);
        history.setOutputPayload(new ObjectMapper().findAndRegisterModules()
                .writeValueAsString(draft));

        MealPlanEntity plan = new MealPlanEntity();
        plan.setId(90L);
        plan.setUser(user);
        plan.setStartDate(LocalDate.of(2026, 7, 20));
        plan.setEndDate(LocalDate.of(2026, 7, 20));
        plan.setSourceAiRequest(history);
        plan.setItems(new java.util.ArrayList<>());

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(mealPlanRepository.findByIdAndUser(90L, user)).thenReturn(Optional.of(plan));

        MealPlanDto result = service.getMealPlan("user@test.com", 90L);

        assertEquals(1, result.getAiDayNutrition().size());
        assertEquals(com.grun.calorietracker.enums.NutritionPlanDayType.TRAINING,
                result.getAiDayNutrition().get(0).getDayType());
        assertEquals(1800.0,
                result.getAiDayNutrition().get(0).getTotalNutrition().getSodium());
        assertEquals(75.0,
                result.getAiDayNutrition().get(0).getTotalNutrition().getVitaminC());
    }
    private MealPlanRequestDto snapshotRequest() {
        MealPlanRequestDto request = new MealPlanRequestDto();
        request.setName("AI nutrition week");
        request.setStartDate(LocalDate.of(2026, 7, 20));
        request.setEndDate(LocalDate.of(2026, 7, 26));
        request.setGenerationMode(NutritionPlanGenerationMode.GENERAL);

        MealPlanNutritionSnapshotDto nutrition = new MealPlanNutritionSnapshotDto();
        nutrition.setCalories(520.0);
        nutrition.setProtein(48.0);
        nutrition.setCarbs(55.0);
        nutrition.setFat(11.0);
        nutrition.setFiber(8.0);

        MealPlanItemRequestDto item = new MealPlanItemRequestDto();
        item.setPlanDate(LocalDate.of(2026, 7, 20));
        item.setMealType("LUNCH");
        item.setItemType(MealPlanItemType.AI_SNAPSHOT);
        item.setSnapshotName("grilled chicken with rice");
        item.setGroceryName("chicken and rice");
        item.setPreparationMethod(FoodPreparationState.GRILLED);
        item.setPortionSize(430.0);
        item.setPortionUnit(FoodPortionUnit.GRAM);
        item.setSnapshotNutrition(nutrition);
        item.setWarnings(java.util.List.of("Nutrition values are estimated."));
        request.setItems(java.util.List.of(item));
        return request;
    }

    private MealPlanRequestDto request() {
        MealPlanRequestDto request = new MealPlanRequestDto();
        request.setName("High protein week");
        request.setStartDate(LocalDate.of(2026, 6, 15));
        request.setEndDate(LocalDate.of(2026, 6, 21));

        MealPlanItemRequestDto foodItem = new MealPlanItemRequestDto();
        foodItem.setPlanDate(LocalDate.of(2026, 6, 15));
        foodItem.setMealType("BREAKFAST");
        foodItem.setItemType(MealPlanItemType.FOOD_ITEM);
        foodItem.setFoodItemId(10L);
        foodItem.setPortionSize(150.0);
        foodItem.setPortionUnit(FoodPortionUnit.GRAM);

        MealPlanItemRequestDto recipe = new MealPlanItemRequestDto();
        recipe.setPlanDate(LocalDate.of(2026, 6, 15));
        recipe.setMealType("DINNER");
        recipe.setItemType(MealPlanItemType.RECIPE);
        recipe.setRecipeId(20L);
        recipe.setServingCount(2.0);

        request.setItems(java.util.List.of(foodItem, recipe));
        return request;
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@test.com");
        return user;
    }

    private FoodItemEntity food(Long id, String name) {
        FoodItemEntity food = new FoodItemEntity();
        food.setId(id);
        food.setName(name);
        return food;
    }

    private RecipeEntity recipe(Long id, String name, FoodItemEntity ingredientFood, double grams) {
        RecipeEntity recipe = new RecipeEntity();
        recipe.setId(id);
        recipe.setName(name);
        recipe.setDefaultServingGrams(100.0);
        recipe.setTotalYieldGrams(100.0);
        RecipeIngredientEntity ingredient = new RecipeIngredientEntity();
        ingredient.setFoodItem(ingredientFood);
        ingredient.setNormalizedPortionGrams(grams);
        recipe.getIngredients().add(ingredient);
        return recipe;
    }

    private com.grun.calorietracker.entity.MealPlanItemEntity foodItem(MealPlanEntity plan, FoodItemEntity food, double grams) {
        com.grun.calorietracker.entity.MealPlanItemEntity item = new com.grun.calorietracker.entity.MealPlanItemEntity();
        item.setMealPlan(plan);
        item.setPlanDate(LocalDate.of(2026, 6, 15));
        item.setMealType("BREAKFAST");
        item.setItemType(MealPlanItemType.FOOD_ITEM);
        item.setFoodItem(food);
        item.setPortionSize(grams);
        item.setPortionUnit(FoodPortionUnit.GRAM);
        return item;
    }

    private com.grun.calorietracker.entity.MealPlanItemEntity recipeItem(MealPlanEntity plan, RecipeEntity recipe, double servings) {
        com.grun.calorietracker.entity.MealPlanItemEntity item = new com.grun.calorietracker.entity.MealPlanItemEntity();
        item.setMealPlan(plan);
        item.setPlanDate(LocalDate.of(2026, 6, 15));
        item.setMealType("DINNER");
        item.setItemType(MealPlanItemType.RECIPE);
        item.setRecipe(recipe);
        item.setServingCount(servings);
        return item;
    }
}
