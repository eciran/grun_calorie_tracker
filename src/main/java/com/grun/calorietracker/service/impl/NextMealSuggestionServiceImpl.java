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
import com.grun.calorietracker.enums.NextMealSuggestionReason;
import com.grun.calorietracker.enums.NextMealSuggestionStrategy;
import com.grun.calorietracker.enums.RecipeAllergen;
import com.grun.calorietracker.enums.RecipeCategory;
import com.grun.calorietracker.enums.RecipePublicSort;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.ProductAnalyticsEventRepository;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NextMealSuggestionServiceImpl implements NextMealSuggestionService {

    private static final int RECIPE_CANDIDATE_LIMIT = 50;
    private static final int RECIPE_SUGGESTION_LIMIT = 3;
    private static final double CALORIE_SCORE_WEIGHT = 0.35;
    private static final double PROTEIN_SCORE_WEIGHT = 0.30;
    private static final double CARB_SCORE_WEIGHT = 0.20;
    private static final double FAT_SCORE_WEIGHT = 0.15;
    private static final int RECENT_RECIPE_LOOKBACK_DAYS = 14;
    private static final int DISMISSED_RECIPE_LOOKBACK_DAYS = 14;
    private static final int DISMISSED_RECIPE_PENALTY = 24;
    private static final int SCORE_ROTATION_BAND = 5;
    private static final int SPECIALIZED_OPTION_MAX_SCORE_GAP = 15;
    private static final Set<RecipeCategory> VARIETY_CATEGORIES = Set.of(
            RecipeCategory.VEGETABLES,
            RecipeCategory.MEAT,
            RecipeCategory.CHICKEN,
            RecipeCategory.FISH,
            RecipeCategory.SOUP,
            RecipeCategory.SALAD,
            RecipeCategory.DESSERT,
            RecipeCategory.TURKISH,
            RecipeCategory.MEDITERRANEAN,
            RecipeCategory.UK_IE
    );
    private static final LocalTime BREAKFAST_START = LocalTime.of(5, 0);
    private static final LocalTime LUNCH_START = LocalTime.of(11, 30);
    private static final LocalTime EARLY_SNACK_START = LocalTime.of(16, 0);
    private static final LocalTime DINNER_START = LocalTime.of(18, 0);
    private static final LocalTime LATE_SNACK_START = LocalTime.of(22, 0);
    private static final LocalTime DAY_END = LocalTime.of(23, 0);
    private static final double EARLY_SNACK_RATIO = 0.20;
    private static final List<MealSlot> CORE_MEALS = List.of(
            new MealSlot("BREAKFAST", 0.25, 0.30, 0.35, 0.25),
            new MealSlot("LUNCH", 0.30, 0.35, 0.35, 0.30),
            new MealSlot("DINNER", 0.35, 0.35, 0.30, 0.45)
    );

    private final UserRepository userRepository;
    private final DashboardService dashboardService;
    private final RecipeService recipeService;
    private final UserNutritionPreferenceService nutritionPreferenceService;
    private final SubscriptionService subscriptionService;
    private final FoodLogsRepository foodLogsRepository;
    private final RecipeLogRepository recipeLogRepository;
    private final ProductAnalyticsEventRepository productAnalyticsEventRepository;
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
        MealAllocation allocation = resolveAllocation(loggedMealTypes, result.getGeneratedAt().toLocalTime());
        if (allocation == null) {
            result.setStatus(NextMealStatus.NO_UPCOMING_MEAL);
            return result;
        }

        result.setStatus(NextMealStatus.READY);
        result.setMealType(allocation.mealType());
        result.setTargetCalories(portion(summary.getRemainingCalories(), allocation.calorieRatio()));
        result.setTargetProtein(portion(summary.getRemainingProtein(), allocation.proteinRatio()));
        result.setTargetCarbs(portion(summary.getRemainingCarbs(), allocation.carbRatio()));
        result.setTargetFat(portion(summary.getRemainingFat(), allocation.fatRatio()));

        UserNutritionPreferenceDto preferences = nutritionPreferenceService.getForPersonalization(email);
        boolean personalizationAllowed = nutritionPreferenceService.isPersonalizationAllowed(email);
        result.setRecipeSuggestions(findRecipeSuggestions(
                email,
                user,
                preferences,
                personalizationAllowed,
                today,
                result.getMealType(),
                result.getTargetCalories(),
                result.getTargetProtein(),
                result.getTargetCarbs(),
                result.getTargetFat()
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

    private MealAllocation resolveAllocation(Set<String> loggedMealTypes, LocalTime localTime) {
        MealWindow window = currentMealWindow(localTime);
        if (window == MealWindow.NONE) {
            return null;
        }
        if (window == MealWindow.EARLY_SNACK) {
            return loggedMealTypes.contains("SNACK")
                    ? null
                    : new MealAllocation("SNACK", EARLY_SNACK_RATIO, EARLY_SNACK_RATIO,
                    EARLY_SNACK_RATIO, EARLY_SNACK_RATIO);
        }
        if (window == MealWindow.LATE_SNACK) {
            return new MealAllocation("SNACK", 1.0, 1.0, 1.0, 1.0);
        }

        int currentMealIndex = switch (window) {
            case BREAKFAST -> 0;
            case LUNCH -> 1;
            case DINNER -> 2;
            default -> throw new IllegalStateException("Unexpected meal window: " + window);
        };

        int latestLoggedIndex = -1;
        for (int index = 0; index < CORE_MEALS.size(); index++) {
            if (loggedMealTypes.contains(CORE_MEALS.get(index).mealType())) {
                latestLoggedIndex = index;
            }
        }

        int firstEligibleIndex = Math.max(currentMealIndex, latestLoggedIndex + 1);
        List<MealSlot> remaining = CORE_MEALS.subList(firstEligibleIndex, CORE_MEALS.size()).stream()
                .filter(slot -> !loggedMealTypes.contains(slot.mealType()))
                .toList();
        if (remaining.isEmpty()) {
            return null;
        }
        MealSlot selected = remaining.get(0);
        return new MealAllocation(
                selected.mealType(),
                ratio(selected.calorieWeight(), remaining.stream().mapToDouble(MealSlot::calorieWeight).sum()),
                ratio(selected.proteinWeight(), remaining.stream().mapToDouble(MealSlot::proteinWeight).sum()),
                ratio(selected.carbWeight(), remaining.stream().mapToDouble(MealSlot::carbWeight).sum()),
                ratio(selected.fatWeight(), remaining.stream().mapToDouble(MealSlot::fatWeight).sum())
        );
    }

    private MealWindow currentMealWindow(LocalTime localTime) {
        if (localTime == null || localTime.isBefore(BREAKFAST_START) || !localTime.isBefore(DAY_END)) {
            return MealWindow.NONE;
        }
        if (localTime.isBefore(LUNCH_START)) {
            return MealWindow.BREAKFAST;
        }
        if (localTime.isBefore(EARLY_SNACK_START)) {
            return MealWindow.LUNCH;
        }
        if (localTime.isBefore(DINNER_START)) {
            return MealWindow.EARLY_SNACK;
        }
        if (localTime.isBefore(LATE_SNACK_START)) {
            return MealWindow.DINNER;
        }
        return MealWindow.LATE_SNACK;
    }

    private List<NextMealRecipeSuggestionDto> findRecipeSuggestions(
            String email,
            UserEntity user,
            UserNutritionPreferenceDto preferences,
            boolean personalizationAllowed,
            LocalDate targetDate,
            String mealType,
            Double targetCalories,
            Double targetProtein,
            Double targetCarbs,
            Double targetFat) {
        Set<RecipeCategory> categories = dietaryCategories(preferences.getDietaryPreferences());
        Set<RecipeAllergen> allergens = preferences.getAllergens() == null
                ? Set.of()
                : new LinkedHashSet<>(preferences.getAllergens());
        List<RecipeDto> recipes = loadCandidates(
                email, mealType, user, categories, allergens, personalizationAllowed);
        Map<Long, RecentRecipeUsage> recentUsage = recentRecipeUsage(user, targetDate);
        Set<Long> recentlyDismissedRecipeIds = new LinkedHashSet<>(
                productAnalyticsEventRepository.findRecentlyDismissedNextMealRecipeIds(
                        user, targetDate.minusDays(DISMISSED_RECIPE_LOOKBACK_DAYS).atStartOfDay()));

        List<ScoredSuggestion> ranked = recipes.stream()
                .filter(recipe -> !containsExcludedFood(recipe, preferences.getExcludedFoods()))
                .map(recipe -> toSuggestion(
                        recipe, targetCalories, targetProtein, targetCarbs, targetFat,
                        recentUsage.get(recipe.getId()), targetDate,
                        categories, personalizationAllowed,
                        recentlyDismissedRecipeIds.contains(recipe.getId())))
                .filter(suggestion -> suggestion.dto().getCaloriesPerServing() != null)
                .sorted(suggestionComparator(targetDate))
                .toList();
        return assignStrategies(ranked);
    }

    private Comparator<ScoredSuggestion> suggestionComparator(LocalDate targetDate) {
        return Comparator
                .comparingInt((ScoredSuggestion suggestion) -> scoreBand(suggestion.dto().getMatchScore()))
                .reversed()
                .thenComparingLong(suggestion -> dailyRotationKey(suggestion.dto().getRecipeId(), targetDate))
                .thenComparing(suggestion -> suggestion.dto().getMatchScore(), Comparator.reverseOrder())
                .thenComparing(suggestion -> suggestion.dto().getRecipeId());
    }

    private List<NextMealRecipeSuggestionDto> assignStrategies(List<ScoredSuggestion> ranked) {
        if (ranked.isEmpty()) {
            return List.of();
        }
        List<ScoredSuggestion> selected = new ArrayList<>();
        ScoredSuggestion best = ranked.get(0);
        applyStrategy(best.dto(), NextMealSuggestionStrategy.BEST_MATCH, null);
        selected.add(best);

        ScoredSuggestion quick = ranked.stream()
                .skip(1)
                .filter(ScoredSuggestion::quick)
                .filter(candidate -> withinScoreGap(best, candidate, SPECIALIZED_OPTION_MAX_SCORE_GAP))
                .findFirst()
                .orElse(null);
        if (quick != null) {
            applyStrategy(quick.dto(), NextMealSuggestionStrategy.QUICK_OPTION,
                    NextMealSuggestionReason.QUICK_OPTION);
            selected.add(quick);
        }

        ScoredSuggestion variety = ranked.stream()
                .skip(1)
                .filter(candidate -> !selected.contains(candidate))
                .filter(candidate -> isVariety(best, candidate))
                .filter(candidate -> withinScoreGap(best, candidate, SPECIALIZED_OPTION_MAX_SCORE_GAP))
                .findFirst()
                .orElseGet(() -> ranked.stream()
                        .skip(1)
                        .filter(candidate -> !selected.contains(candidate))
                        .filter(candidate -> withinScoreGap(best, candidate, SPECIALIZED_OPTION_MAX_SCORE_GAP))
                        .findFirst()
                        .orElse(null));
        if (variety != null && selected.size() < RECIPE_SUGGESTION_LIMIT) {
            applyStrategy(variety.dto(), NextMealSuggestionStrategy.VARIETY,
                    NextMealSuggestionReason.VARIETY_PICK);
            selected.add(variety);
        }

        return selected.stream()
                .limit(RECIPE_SUGGESTION_LIMIT)
                .map(ScoredSuggestion::dto)
                .toList();
    }

    private void applyStrategy(
            NextMealRecipeSuggestionDto suggestion,
            NextMealSuggestionStrategy strategy,
            NextMealSuggestionReason reason) {
        suggestion.setStrategy(strategy);
        if (reason != null && !suggestion.getReasonCodes().contains(reason)) {
            suggestion.getReasonCodes().add(reason);
        }
    }

    private boolean withinScoreGap(ScoredSuggestion best, ScoredSuggestion candidate, int maxGap) {
        return best.dto().getMatchScore() - candidate.dto().getMatchScore() <= maxGap;
    }

    private boolean isVariety(ScoredSuggestion best, ScoredSuggestion candidate) {
        Set<RecipeCategory> bestCategories = new LinkedHashSet<>(best.categories());
        bestCategories.retainAll(VARIETY_CATEGORIES);
        Set<RecipeCategory> candidateCategories = new LinkedHashSet<>(candidate.categories());
        candidateCategories.retainAll(VARIETY_CATEGORIES);
        return candidateCategories.isEmpty() || !candidateCategories.equals(bestCategories);
    }

    private int scoreBand(Integer score) {
        return score == null ? -1 : score / SCORE_ROTATION_BAND;
    }

    private long dailyRotationKey(Long recipeId, LocalDate targetDate) {
        long id = recipeId == null ? 0 : recipeId;
        long day = targetDate == null ? 0 : targetDate.toEpochDay();
        long mixed = id * 0x9E3779B97F4A7C15L + day * 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        return mixed ^ (mixed >>> 31);
    }

    private Map<Long, RecentRecipeUsage> recentRecipeUsage(UserEntity user, LocalDate targetDate) {
        LocalDateTime since = targetDate.minusDays(RECENT_RECIPE_LOOKBACK_DAYS).atStartOfDay();
        Map<Long, RecentRecipeUsage> usage = new HashMap<>();
        for (Object[] row : recipeLogRepository.findRecentRecipeUsage(user, since)) {
            if (row == null || row.length < 3 || !(row[0] instanceof Number recipeId)
                    || !(row[1] instanceof LocalDateTime lastLoggedAt)
                    || !(row[2] instanceof Number count)) {
                continue;
            }
            usage.put(recipeId.longValue(), new RecentRecipeUsage(
                    lastLoggedAt.toLocalDate(), count.longValue()));
        }
        return usage;
    }

    private List<RecipeDto> loadCandidates(
            String email,
            String mealType,
            UserEntity user,
            Set<RecipeCategory> categories,
            Set<RecipeAllergen> allergens,
            boolean personalizationAllowed) {
        String language = user.getPreferredLanguage() == null
                ? null
                : user.getPreferredLanguage().name().toLowerCase(Locale.ROOT);
        RecipePageDto exact = recipeService.getPublicRecipes(
                email, null, mealType, personalizationAllowed ? user.getMarketRegion() : null, language,
                categories, allergens, RecipePublicSort.NEWEST, 0, RECIPE_CANDIDATE_LIMIT);
        if (hasContent(exact)) {
            return exact.getContent();
        }
        RecipePageDto regional = recipeService.getPublicRecipes(
                email, null, mealType, personalizationAllowed ? user.getMarketRegion() : null, null,
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

    private ScoredSuggestion toSuggestion(
            RecipeDto recipe,
            Double targetCalories,
            Double targetProtein,
            Double targetCarbs,
            Double targetFat,
            RecentRecipeUsage recentUsage,
            LocalDate targetDate,
            Set<RecipeCategory> dietaryCategories,
            boolean personalizationAllowed,
            boolean recentlyDismissed) {
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
        int nutritionScore = matchScore(
                calories, protein, carbs, fat,
                targetCalories, targetProtein, targetCarbs, targetFat);
        int personalizationBonus = personalizationBonus(
                recipe, dietaryCategories, personalizationAllowed);
        suggestion.setMatchScore(Math.max(0, Math.min(100,
                nutritionScore + personalizationBonus
                        - repetitionPenalty(recentUsage, targetDate)
                        - (recentlyDismissed ? DISMISSED_RECIPE_PENALTY : 0))));
        suggestion.setReasonCodes(reasonCodes(
                recipe, calories, protein, carbs, fat,
                targetCalories, targetProtein, targetCarbs, targetFat,
                dietaryCategories, personalizationAllowed));
        Set<RecipeCategory> recipeCategories = recipe.getCategories() == null
                ? Set.of()
                : new LinkedHashSet<>(recipe.getCategories());
        return new ScoredSuggestion(
                suggestion,
                recipeCategories,
                recipeCategories.contains(RecipeCategory.QUICK_MEAL));
    }

    private int personalizationBonus(
            RecipeDto recipe,
            Set<RecipeCategory> dietaryCategories,
            boolean personalizationAllowed) {
        if (!personalizationAllowed) {
            return 0;
        }
        int bonus = 0;
        if (Boolean.TRUE.equals(recipe.getFavoriteByMe())) {
            bonus += 8;
        } else if (Boolean.TRUE.equals(recipe.getSavedByMe())) {
            bonus += 5;
        }
        if (recipe.getMyRating() != null && recipe.getMyRating() >= 4) {
            bonus += 4;
        }
        if (matchesAnyCategory(recipe, dietaryCategories)) {
            bonus += 3;
        }
        return bonus;
    }

    private List<NextMealSuggestionReason> reasonCodes(
            RecipeDto recipe,
            Double calories,
            Double protein,
            Double carbs,
            Double fat,
            Double targetCalories,
            Double targetProtein,
            Double targetCarbs,
            Double targetFat,
            Set<RecipeCategory> dietaryCategories,
            boolean personalizationAllowed) {
        List<NextMealSuggestionReason> reasons = new ArrayList<>();
        if (closeness(calories, targetCalories) >= 0.85) {
            reasons.add(NextMealSuggestionReason.CALORIE_TARGET_MATCH);
        }
        if (closeness(protein, targetProtein) >= 0.85) {
            reasons.add(NextMealSuggestionReason.PROTEIN_TARGET_MATCH);
        }
        if (closeness(protein, targetProtein) >= 0.75
                && closeness(carbs, targetCarbs) >= 0.75
                && closeness(fat, targetFat) >= 0.75) {
            reasons.add(NextMealSuggestionReason.MACRO_BALANCED);
        }
        if (personalizationAllowed && matchesAnyCategory(recipe, dietaryCategories)) {
            reasons.add(NextMealSuggestionReason.DIETARY_PREFERENCE_MATCH);
        }
        if (personalizationAllowed && Boolean.TRUE.equals(recipe.getFavoriteByMe())) {
            reasons.add(NextMealSuggestionReason.FAVORITE_BY_YOU);
        } else if (personalizationAllowed && Boolean.TRUE.equals(recipe.getSavedByMe())) {
            reasons.add(NextMealSuggestionReason.SAVED_BY_YOU);
        }
        return reasons;
    }

    private boolean matchesAnyCategory(RecipeDto recipe, Set<RecipeCategory> categories) {
        return categories != null && !categories.isEmpty()
                && recipe.getCategories() != null
                && recipe.getCategories().stream().anyMatch(categories::contains);
    }

    private int repetitionPenalty(RecentRecipeUsage usage, LocalDate targetDate) {
        if (usage == null || usage.lastLoggedDate() == null || targetDate == null) {
            return 0;
        }
        long daysSince = java.time.temporal.ChronoUnit.DAYS.between(usage.lastLoggedDate(), targetDate);
        int recencyPenalty;
        if (daysSince <= 2) {
            recencyPenalty = 30;
        } else if (daysSince <= 7) {
            recencyPenalty = 18;
        } else if (daysSince <= RECENT_RECIPE_LOOKBACK_DAYS) {
            recencyPenalty = 8;
        } else {
            recencyPenalty = 0;
        }
        int frequencyPenalty = (int) Math.min(9, Math.max(0, usage.count() - 1) * 3);
        return recencyPenalty + frequencyPenalty;
    }

    private int matchScore(
            Double calories,
            Double protein,
            Double carbs,
            Double fat,
            Double targetCalories,
            Double targetProtein,
            Double targetCarbs,
            Double targetFat) {
        double score = closeness(calories, targetCalories) * CALORIE_SCORE_WEIGHT
                + closeness(protein, targetProtein) * PROTEIN_SCORE_WEIGHT
                + closeness(carbs, targetCarbs) * CARB_SCORE_WEIGHT
                + closeness(fat, targetFat) * FAT_SCORE_WEIGHT;
        return (int) Math.round(score * 100);
    }

    private double closeness(Double actual, Double target) {
        if (actual == null || target == null) {
            return 0;
        }
        if (target <= 0) {
            return actual <= 0 ? 1 : 0;
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

    private double ratio(double selectedWeight, double remainingWeight) {
        return remainingWeight <= 0 ? 0 : selectedWeight / remainingWeight;
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

    private record MealSlot(
            String mealType,
            double calorieWeight,
            double proteinWeight,
            double carbWeight,
            double fatWeight) {
    }

    private record MealAllocation(
            String mealType,
            double calorieRatio,
            double proteinRatio,
            double carbRatio,
            double fatRatio) {
    }

    private record RecentRecipeUsage(LocalDate lastLoggedDate, long count) {
    }

    private enum MealWindow {
        BREAKFAST,
        LUNCH,
        EARLY_SNACK,
        DINNER,
        LATE_SNACK,
        NONE
    }

    private record ScoredSuggestion(
            NextMealRecipeSuggestionDto dto,
            Set<RecipeCategory> categories,
            boolean quick) {
    }
}
