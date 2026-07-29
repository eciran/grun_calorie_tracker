package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.DailySummaryDto;
import com.grun.calorietracker.dto.NextMealAiRecipePrefillDto;
import com.grun.calorietracker.dto.NextMealRecipeSuggestionDto;
import com.grun.calorietracker.dto.NextMealSuggestionDto;
import com.grun.calorietracker.dto.RecipeDto;
import com.grun.calorietracker.dto.RecipeIngredientDto;
import com.grun.calorietracker.dto.RecipeNutritionDto;
import com.grun.calorietracker.dto.RecipePageDto;
import com.grun.calorietracker.dto.UserNutritionPreferenceDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.NextMealStatus;
import com.grun.calorietracker.enums.RecipeAllergen;
import com.grun.calorietracker.enums.RecipeCategory;
import com.grun.calorietracker.enums.RecipePublicSort;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.RecipeLogRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.DashboardService;
import com.grun.calorietracker.service.NextMealSuggestionService;
import com.grun.calorietracker.service.RecipeService;
import com.grun.calorietracker.service.SubscriptionService;
import com.grun.calorietracker.service.UserNutritionPreferenceService;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NextMealSuggestionServiceImpl implements NextMealSuggestionService {

    private static final int RECIPE_CANDIDATE_LIMIT = 50;
    private static final int RECIPE_SUGGESTION_LIMIT = 3;
    private static final List<MealSlot> CORE_MEALS = List.of(
            new MealSlot("BREAKFAST", 0.25),
            new MealSlot("LUNCH", 0.30),
            new MealSlot("DINNER", 0.35)
    );

    private final UserRepository userRepository;
    private final DashboardService dashboardService;
    private final RecipeService recipeService;
    private final UserNutritionPreferenceService nutritionPreferenceService;
    private final SubscriptionService subscriptionService;
    private final FoodLogsRepository foodLogsRepository;
    private final RecipeLogRepository recipeLogRepository;
    private final UserTimeZoneSupport userTimeZoneSupport;

    @Override
    @Transactional(readOnly = true)
    public NextMealSuggestionDto getNextMeal(String email) {
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.NEXT_MEAL_SUGGESTIONS);
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        LocalDate today = userTimeZoneSupport.today(user);
        DailySummaryDto summary = dashboardService.getDailySummary(email, today);

        NextMealSuggestionDto result = baseResult(user, summary, today);
        if (!Boolean.TRUE.equals(summary.getHasActiveGoal())) {
            result.setStatus(NextMealStatus.NO_ACTIVE_GOAL);
            return result;
        }
        if (safe(summary.getRemainingCalories()) <= 0.0) {
            result.setStatus(NextMealStatus.TARGET_REACHED);
            return result;
        }

        Set<String> loggedMealTypes = loggedMealTypes(user, today);
        MealAllocation allocation = resolveAllocation(loggedMealTypes);
        if (allocation == null) {
            result.setStatus(NextMealStatus.NO_UPCOMING_MEAL);
            return result;
        }

        result.setStatus(NextMealStatus.READY);
        result.setMealType(allocation.mealType());
        result.setTargetCalories(portion(summary.getRemainingCalories(), allocation.ratio()));
        result.setTargetProtein(portion(summary.getRemainingProtein(), allocation.ratio()));
        result.setTargetCarbs(portion(summary.getRemainingCarbs(), allocation.ratio()));
        result.setTargetFat(portion(summary.getRemainingFat(), allocation.ratio()));

        UserNutritionPreferenceDto preferences = nutritionPreferenceService.get(email);
        result.setRecipeSuggestions(findRecipeSuggestions(
                email,
                user,
                preferences,
                result.getMealType(),
                result.getTargetCalories(),
                result.getTargetProtein()
        ));
        result.setAiRecipePrefill(toAiPrefill(user, preferences, result));
        return result;
    }

    private NextMealSuggestionDto baseResult(UserEntity user, DailySummaryDto summary, LocalDate today) {
        NextMealSuggestionDto result = new NextMealSuggestionDto();
        result.setTargetDate(today);
        result.setGeneratedAt(userTimeZoneSupport.now(user));
        result.setDailyRemainingCalories(nonNegative(summary.getRemainingCalories()));
        result.setDailyRemainingProtein(nonNegative(summary.getRemainingProtein()));
        result.setDailyRemainingCarbs(nonNegative(summary.getRemainingCarbs()));
        result.setDailyRemainingFat(nonNegative(summary.getRemainingFat()));
        boolean aiRecipeAvailable = subscriptionService.hasFeatureAccess(
                user.getEmail(), SubscriptionFeature.AI_RECIPE_GENERATION);
        result.setAiRecipeAvailable(aiRecipeAvailable);
        result.setAiRecipeCreditCost(aiRecipeAvailable
                ? subscriptionService.resolveAiCreditCost(user.getEmail(), SubscriptionFeature.AI_RECIPE_GENERATION)
                : null);
        return result;
    }

    private Set<String> loggedMealTypes(UserEntity user, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        Set<String> mealTypes = new LinkedHashSet<>();
        foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                        user, start, end)
                .forEach(log -> addMealType(mealTypes, log.getMealType()));
        recipeLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                        user, start, end)
                .forEach(log -> addMealType(mealTypes, log.getMealType()));
        return mealTypes;
    }

    private MealAllocation resolveAllocation(Set<String> loggedMealTypes) {
        int latestLoggedIndex = -1;
        for (int index = 0; index < CORE_MEALS.size(); index++) {
            if (loggedMealTypes.contains(CORE_MEALS.get(index).mealType())) {
                latestLoggedIndex = index;
            }
        }

        List<MealSlot> remaining = CORE_MEALS.subList(latestLoggedIndex + 1, CORE_MEALS.size()).stream()
                .filter(slot -> !loggedMealTypes.contains(slot.mealType()))
                .toList();
        if (remaining.isEmpty()) {
            return new MealAllocation("SNACK", 1.0);
        }
        MealSlot selected = remaining.get(0);
        double remainingWeight = remaining.stream().mapToDouble(MealSlot::weight).sum();
        return new MealAllocation(selected.mealType(), selected.weight() / remainingWeight);
    }

    private List<NextMealRecipeSuggestionDto> findRecipeSuggestions(
            String email,
            UserEntity user,
            UserNutritionPreferenceDto preferences,
            String mealType,
            Double targetCalories,
            Double targetProtein) {
        Set<RecipeCategory> categories = dietaryCategories(preferences.getDietaryPreferences());
        Set<RecipeAllergen> allergens = preferences.getAllergens() == null
                ? Set.of()
                : new LinkedHashSet<>(preferences.getAllergens());
        List<RecipeDto> recipes = loadCandidates(
                email, mealType, user, categories, allergens);

        return recipes.stream()
                .filter(recipe -> !containsExcludedFood(recipe, preferences.getExcludedFoods()))
                .map(recipe -> toSuggestion(recipe, targetCalories, targetProtein))
                .filter(suggestion -> suggestion.getCaloriesPerServing() != null)
                .sorted(Comparator.comparing(
                        NextMealRecipeSuggestionDto::getMatchScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(RECIPE_SUGGESTION_LIMIT)
                .toList();
    }

    private List<RecipeDto> loadCandidates(
            String email,
            String mealType,
            UserEntity user,
            Set<RecipeCategory> categories,
            Set<RecipeAllergen> allergens) {
        String language = user.getPreferredLanguage() == null
                ? null
                : user.getPreferredLanguage().name().toLowerCase(Locale.ROOT);
        RecipePageDto exact = recipeService.getPublicRecipes(
                email, null, mealType, user.getMarketRegion(), language,
                categories, allergens, RecipePublicSort.NEWEST, 0, RECIPE_CANDIDATE_LIMIT);
        if (hasContent(exact)) {
            return exact.getContent();
        }
        RecipePageDto regional = recipeService.getPublicRecipes(
                email, null, mealType, user.getMarketRegion(), null,
                categories, allergens, RecipePublicSort.NEWEST, 0, RECIPE_CANDIDATE_LIMIT);
        if (hasContent(regional)) {
            return regional.getContent();
        }
        RecipePageDto fallback = recipeService.getPublicRecipes(
                email, null, mealType, null, null,
                categories, allergens, RecipePublicSort.NEWEST, 0, RECIPE_CANDIDATE_LIMIT);
        return hasContent(fallback) ? fallback.getContent() : List.of();
    }

    private boolean hasContent(RecipePageDto page) {
        return page != null && page.getContent() != null && !page.getContent().isEmpty();
    }

    private NextMealRecipeSuggestionDto toSuggestion(
            RecipeDto recipe, Double targetCalories, Double targetProtein) {
        RecipeNutritionDto nutrition = recipe.getPerServingNutrition();
        Double calories = nutrition == null ? perServing(recipe.getCalories(), recipe.getServingCount()) : nutrition.getCalories();
        Double protein = nutrition == null ? perServing(recipe.getProtein(), recipe.getServingCount()) : nutrition.getProtein();
        Double carbs = nutrition == null ? perServing(recipe.getCarbs(), recipe.getServingCount()) : nutrition.getCarbs();
        Double fat = nutrition == null ? perServing(recipe.getFat(), recipe.getServingCount()) : nutrition.getFat();

        NextMealRecipeSuggestionDto suggestion = new NextMealRecipeSuggestionDto();
        suggestion.setRecipeId(recipe.getId());
        suggestion.setName(recipe.getName());
        suggestion.setImageUrl(recipe.getImageUrl());
        suggestion.setCaloriesPerServing(round(calories));
        suggestion.setProteinPerServing(round(protein));
        suggestion.setCarbsPerServing(round(carbs));
        suggestion.setFatPerServing(round(fat));
        suggestion.setMatchScore(matchScore(calories, protein, targetCalories, targetProtein));
        return suggestion;
    }

    private int matchScore(
            Double calories, Double protein, Double targetCalories, Double targetProtein) {
        double calorieScore = closeness(calories, targetCalories);
        double proteinScore = targetProtein == null || targetProtein <= 0
                ? calorieScore
                : closeness(protein, targetProtein);
        return (int) Math.round((calorieScore * 0.75 + proteinScore * 0.25) * 100);
    }

    private double closeness(Double actual, Double target) {
        if (actual == null || target == null || target <= 0) {
            return 0;
        }
        return Math.max(0, 1 - Math.min(Math.abs(actual - target) / target, 1));
    }

    private boolean containsExcludedFood(RecipeDto recipe, List<String> excludedFoods) {
        if (excludedFoods == null || excludedFoods.isEmpty()
                || recipe.getIngredients() == null || recipe.getIngredients().isEmpty()) {
            return false;
        }
        List<String> exclusions = excludedFoods.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .toList();
        return recipe.getIngredients().stream()
                .map(RecipeIngredientDto::getFoodName)
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .anyMatch(name -> exclusions.stream().anyMatch(name::contains));
    }

    private Set<RecipeCategory> dietaryCategories(List<String> preferences) {
        Set<RecipeCategory> categories = new LinkedHashSet<>();
        if (preferences == null) {
            return categories;
        }
        for (String preference : preferences) {
            if (preference == null) {
                continue;
            }
            try {
                RecipeCategory category = RecipeCategory.valueOf(
                        preference.trim().toUpperCase(Locale.ROOT));
                if (Set.of(
                        RecipeCategory.VEGAN,
                        RecipeCategory.VEGETARIAN,
                        RecipeCategory.HIGH_PROTEIN,
                        RecipeCategory.LOW_CARB,
                        RecipeCategory.LOW_FAT,
                        RecipeCategory.MEDITERRANEAN
                ).contains(category)) {
                    categories.add(category);
                }
            } catch (IllegalArgumentException ignored) {
                // Non-category preferences such as BALANCED do not restrict catalog discovery.
            }
        }
        return categories;
    }

    private NextMealAiRecipePrefillDto toAiPrefill(
            UserEntity user,
            UserNutritionPreferenceDto preferences,
            NextMealSuggestionDto suggestion) {
        NextMealAiRecipePrefillDto prefill = new NextMealAiRecipePrefillDto();
        prefill.setMealType(suggestion.getMealType());
        prefill.setMarketRegion(user.getMarketRegion());
        prefill.setLanguage(user.getPreferredLanguage() == null
                ? null
                : user.getPreferredLanguage().name().toLowerCase(Locale.ROOT));
        prefill.setServingCount(1);
        prefill.setTargetCaloriesPerServing(suggestion.getTargetCalories());
        prefill.setDietaryPreferences(preferences.getDietaryPreferences() == null
                ? new ArrayList<>()
                : new ArrayList<>(preferences.getDietaryPreferences()));
        List<String> excluded = new ArrayList<>();
        if (preferences.getAllergens() != null) {
            excluded.addAll(preferences.getAllergens().stream().map(Enum::name).toList());
        }
        if (preferences.getExcludedFoods() != null) {
            excluded.addAll(preferences.getExcludedFoods());
        }
        prefill.setExcludedIngredients(new ArrayList<>(new LinkedHashSet<>(excluded)));
        return prefill;
    }

    private void addMealType(Set<String> target, String mealType) {
        if (mealType != null && !mealType.isBlank()) {
            target.add(mealType.trim().toUpperCase(Locale.ROOT));
        }
    }

    private Double portion(Double remaining, double ratio) {
        return round(nonNegative(remaining) * ratio);
    }

    private Double nonNegative(Double value) {
        return Math.max(0, safe(value));
    }

    private double safe(Double value) {
        return value == null ? 0 : value;
    }

    private Double perServing(Double total, Integer servingCount) {
        if (total == null) {
            return null;
        }
        int servings = servingCount == null || servingCount <= 0 ? 1 : servingCount;
        return total / servings;
    }

    private Double round(Double value) {
        return value == null ? null : Math.round(value * 10.0) / 10.0;
    }

    private record MealSlot(String mealType, double weight) {
    }

    private record MealAllocation(String mealType, double ratio) {
    }
}
