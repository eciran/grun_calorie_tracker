package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.GroceryListDto;
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
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.MealPlanRepository;
import com.grun.calorietracker.repository.RecipeRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.MealPlanService;
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
            copy.getItems().add(item);
        }

        return toDto(mealPlanRepository.save(copy));
    }

    @Override
    @Transactional(readOnly = true)
    public GroceryListDto getGroceryList(String email, Long planId) {
        MealPlanEntity plan = getOwnedPlan(planId, getUser(email));
        Map<Long, GroceryAccumulator> accumulator = new LinkedHashMap<>();
        for (MealPlanItemEntity item : plan.getItems()) {
            if (item.getItemType() == MealPlanItemType.FOOD_ITEM && item.getFoodItem() != null) {
                addFood(accumulator, item.getFoodItem(), toGrams(item.getPortionSize(), item.getPortionUnit(), item.getFoodItem()), 1);
            } else if (item.getItemType() == MealPlanItemType.RECIPE && item.getRecipe() != null) {
                double servings = item.getServingCount() == null ? 1.0 : item.getServingCount();
                double recipeServingGrams = item.getRecipe().getDefaultServingGrams() == null
                        ? 0.0
                        : item.getRecipe().getDefaultServingGrams();
                double factor = recipeServingGrams <= 0 || item.getRecipe().getTotalYieldGrams() == null || item.getRecipe().getTotalYieldGrams() <= 0
                        ? servings
                        : (recipeServingGrams * servings) / item.getRecipe().getTotalYieldGrams();
                for (RecipeIngredientEntity ingredient : item.getRecipe().getIngredients()) {
                    addFood(accumulator, ingredient.getFoodItem(), safe(ingredient.getNormalizedPortionGrams()) * factor, 1);
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
            item.setMealType(itemRequest.getMealType().trim().toUpperCase());
            item.setItemType(itemRequest.getItemType());
            item.setItemOrder(order++);
            if (itemRequest.getItemType() == MealPlanItemType.FOOD_ITEM) {
                FoodItemEntity foodItem = foodItemRepository.findById(itemRequest.getFoodItemId())
                        .orElseThrow(() -> new ResourceNotFoundException("Food item not found"));
                item.setFoodItem(foodItem);
                item.setPortionSize(itemRequest.getPortionSize());
                item.setPortionUnit(itemRequest.getPortionUnit());
            } else {
                RecipeEntity recipe = recipeRepository.findAccessibleRecipe(itemRequest.getRecipeId(), user)
                        .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
                item.setRecipe(recipe);
                item.setServingCount(itemRequest.getServingCount() == null ? 1.0 : itemRequest.getServingCount());
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
        }
    }

    private MealPlanDto toDto(MealPlanEntity plan) {
        MealPlanDto dto = new MealPlanDto();
        dto.setId(plan.getId());
        dto.setName(plan.getName());
        dto.setStartDate(plan.getStartDate());
        dto.setEndDate(plan.getEndDate());
        dto.setStatus(plan.getStatus());
        dto.setCreatedAt(plan.getCreatedAt());
        dto.setUpdatedAt(plan.getUpdatedAt());
        dto.setItems(plan.getItems().stream().map(this::toItemDto).toList());
        return dto;
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
        if (item.getFoodItem() != null) {
            dto.setFoodItemId(item.getFoodItem().getId());
            dto.setFoodItemName(item.getFoodItem().getName());
        }
        if (item.getRecipe() != null) {
            dto.setRecipeId(item.getRecipe().getId());
            dto.setRecipeName(item.getRecipe().getName());
        }
        return dto;
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

    private void addFood(Map<Long, GroceryAccumulator> accumulator, FoodItemEntity foodItem, double grams, int uses) {
        if (foodItem == null || foodItem.getId() == null || grams <= 0) {
            return;
        }
        accumulator.computeIfAbsent(foodItem.getId(), ignored -> new GroceryAccumulator(foodItem.getId(), foodItem.getName()))
                .add(grams, uses);
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
        private int uses;

        private GroceryAccumulator(Long foodItemId, String name) {
            this.foodItemId = foodItemId;
            this.name = name;
        }

        private GroceryAccumulator add(double grams, int uses) {
            this.grams += grams;
            this.uses += uses;
            return this;
        }

        private GroceryListItemDto toDto() {
            return new GroceryListItemDto(foodItemId, name, Math.round(grams * 10.0) / 10.0, uses);
        }
    }
}
