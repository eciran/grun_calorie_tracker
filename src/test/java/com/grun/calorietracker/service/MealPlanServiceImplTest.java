package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.GroceryListDto;
import com.grun.calorietracker.dto.MealPlanDto;
import com.grun.calorietracker.dto.MealPlanDuplicateRequestDto;
import com.grun.calorietracker.dto.MealPlanItemRequestDto;
import com.grun.calorietracker.dto.MealPlanRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.MealPlanEntity;
import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.RecipeIngredientEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.MealPlanItemType;
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
import static org.mockito.Mockito.when;

class MealPlanServiceImplTest {

    private final MealPlanRepository mealPlanRepository = mock(MealPlanRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FoodItemRepository foodItemRepository = mock(FoodItemRepository.class);
    private final RecipeRepository recipeRepository = mock(RecipeRepository.class);
    private final MealPlanServiceImpl service = new MealPlanServiceImpl(
            mealPlanRepository,
            userRepository,
            foodItemRepository,
            recipeRepository
    );

    @Test
    void createMealPlan_whenRecipeAndFoodItemsProvided_createsPlan() {
        UserEntity user = user();
        FoodItemEntity yogurt = food(10L, "Greek yogurt");
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

        assertEquals(99L, result.getMealPlanId());
        assertEquals(2, result.getItems().size());
        assertEquals("Chicken", result.getItems().get(0).getName());
        assertEquals(360.0, result.getItems().get(0).getTotalGrams());
        assertEquals("Greek yogurt", result.getItems().get(1).getName());
        assertEquals(150.0, result.getItems().get(1).getTotalGrams());
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
    void createMealPlan_whenItemDateOutsideRange_rejects() {
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user()));
        MealPlanRequestDto request = request();
        request.getItems().get(0).setPlanDate(LocalDate.of(2026, 6, 30));

        assertThrows(IllegalArgumentException.class, () -> service.createMealPlan("user@test.com", request));
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
