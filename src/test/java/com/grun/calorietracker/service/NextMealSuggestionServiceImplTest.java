package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.DailySummaryDto;
import com.grun.calorietracker.dto.NextMealSuggestionDto;
import com.grun.calorietracker.dto.RecipeDto;
import com.grun.calorietracker.dto.RecipeNutritionDto;
import com.grun.calorietracker.dto.RecipePageDto;
import com.grun.calorietracker.dto.UserNutritionPreferenceDto;
import com.grun.calorietracker.entity.FoodLogsEntity;
import com.grun.calorietracker.entity.RecipeLogEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.NextMealStatus;
import com.grun.calorietracker.enums.NextMealSuggestionReason;
import com.grun.calorietracker.enums.NextMealSuggestionStrategy;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.RecipeAllergen;
import com.grun.calorietracker.enums.RecipeCategory;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.ProductAnalyticsEventRepository;
import com.grun.calorietracker.repository.RecipeLogRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.NextMealSuggestionServiceImpl;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NextMealSuggestionServiceImplTest {

    private static final String EMAIL = "user@example.com";
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 25);

    @Mock
    private UserRepository userRepository;
    @Mock
    private DashboardService dashboardService;
    @Mock
    private RecipeService recipeService;
    @Mock
    private UserNutritionPreferenceService nutritionPreferenceService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private FoodLogsRepository foodLogsRepository;
    @Mock
    private RecipeLogRepository recipeLogRepository;
    @Mock
    private ProductAnalyticsEventRepository productAnalyticsEventRepository;
    @Mock
    private UserTimeZoneSupport userTimeZoneSupport;

    private NextMealSuggestionServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = new NextMealSuggestionServiceImpl(
                userRepository,
                dashboardService,
                recipeService,
                nutritionPreferenceService,
                subscriptionService,
                foodLogsRepository,
                recipeLogRepository,
                productAnalyticsEventRepository,
                userTimeZoneSupport
        );
        user = new UserEntity();
        user.setId(1L);
        user.setEmail(EMAIL);
        user.setMarketRegion(MarketRegion.UK_IE);
        user.setPreferredLanguage(PreferredLanguage.EN);
        user.setTimeZone("Europe/Dublin");

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userTimeZoneSupport.today(user)).thenReturn(TODAY);
        when(userTimeZoneSupport.now(user)).thenReturn(LocalDateTime.of(TODAY, LocalTime.NOON));
        when(subscriptionService.hasFeatureAccess(EMAIL, SubscriptionFeature.AI_RECIPE_GENERATION))
                .thenReturn(true);
        when(subscriptionService.resolveAiCreditCost(EMAIL, SubscriptionFeature.AI_RECIPE_GENERATION))
                .thenReturn(2);
    }

    @Test
    void getNextMeal_afterBreakfast_targetsLunchAndBuildsRecipePrefill() {
        when(dashboardService.getDailySummary(EMAIL, TODAY)).thenReturn(summary(1180.0, 72.0, 130.0, 44.0));
        FoodLogsEntity breakfast = new FoodLogsEntity();
        breakfast.setMealType("BREAKFAST");
        when(foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of(breakfast));
        when(recipeLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of());

        UserNutritionPreferenceDto preferences = new UserNutritionPreferenceDto();
        preferences.setAllergens(Set.of(RecipeAllergen.MILK));
        preferences.setDietaryPreferences(List.of("HIGH_PROTEIN"));
        preferences.setExcludedFoods(List.of("pork"));
        when(nutritionPreferenceService.getForPersonalization(EMAIL)).thenReturn(preferences);
        when(nutritionPreferenceService.isPersonalizationAllowed(EMAIL)).thenReturn(true);
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("LUNCH"), eq(MarketRegion.UK_IE), eq("en"),
                any(), eq(Set.of(RecipeAllergen.MILK)), any(), eq(0), eq(50)))
                .thenReturn(recipePage(macroMismatchRecipe(), recipe()));

        NextMealSuggestionDto result = service.getNextMeal(EMAIL);

        assertEquals(NextMealStatus.READY, result.getStatus());
        assertEquals("LUNCH", result.getMealType());
        assertEquals(544.6, result.getTargetCalories());
        assertEquals(36.0, result.getTargetProtein());
        assertEquals(70.0, result.getTargetCarbs());
        assertEquals(17.6, result.getTargetFat());
        assertEquals(2, result.getRecipeSuggestions().size());
        assertEquals("Chicken bowl", result.getRecipeSuggestions().get(0).getName());
        assertEquals(NextMealSuggestionStrategy.BEST_MATCH,
                result.getRecipeSuggestions().get(0).getStrategy());
        assertTrue(result.getRecipeSuggestions().get(0).getReasonCodes()
                .contains(NextMealSuggestionReason.DIETARY_PREFERENCE_MATCH));
        assertTrue(result.getRecipeSuggestions().get(0).getMatchScore()
                > result.getRecipeSuggestions().get(1).getMatchScore());
        assertEquals(true, result.getAiRecipeAvailable());
        assertEquals(2, result.getAiRecipeCreditCost());
        assertNotNull(result.getAiRecipePrefill());
        assertEquals(544.6, result.getAiRecipePrefill().getTargetCaloriesPerServing());
        assertTrue(result.getAiRecipePrefill().getExcludedIngredients().contains("MILK"));
        assertTrue(result.getAiRecipePrefill().getExcludedIngredients().contains("pork"));
        verify(subscriptionService).assertFeatureAccess(
                EMAIL, SubscriptionFeature.NEXT_MEAL_SUGGESTIONS);
    }

    @Test
    void getNextMeal_whenLunchRecipeWasLogged_targetsDinner() {
        when(dashboardService.getDailySummary(EMAIL, TODAY)).thenReturn(summary(700.0, 40.0, 70.0, 25.0));
        when(foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of());
        RecipeLogEntity lunch = new RecipeLogEntity();
        lunch.setMealType("LUNCH");
        when(recipeLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of(lunch));
        when(nutritionPreferenceService.getForPersonalization(EMAIL)).thenReturn(new UserNutritionPreferenceDto());
        when(nutritionPreferenceService.isPersonalizationAllowed(EMAIL)).thenReturn(true);
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("DINNER"), eq(MarketRegion.UK_IE), eq("en"),
                any(), any(), any(), eq(0), eq(50)))
                .thenReturn(recipePage());
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("DINNER"), eq(MarketRegion.UK_IE), eq(null),
                any(), any(), any(), eq(0), eq(50)))
                .thenReturn(recipePage());
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("DINNER"), eq(null), eq(null),
                any(), any(), any(), eq(0), eq(50)))
                .thenReturn(recipePage());

        NextMealSuggestionDto result = service.getNextMeal(EMAIL);

        assertEquals(NextMealStatus.READY, result.getStatus());
        assertEquals("DINNER", result.getMealType());
        assertEquals(700.0, result.getTargetCalories());
        assertEquals(40.0, result.getTargetProtein());
        assertEquals(70.0, result.getTargetCarbs());
        assertEquals(25.0, result.getTargetFat());
    }

    @Test
    void getNextMeal_lateAtNightWithoutLogs_hasNoUpcomingMeal() {
        when(userTimeZoneSupport.now(user)).thenReturn(LocalDateTime.of(TODAY, LocalTime.of(23, 30)));
        when(dashboardService.getDailySummary(EMAIL, TODAY)).thenReturn(summary(1600.0, 90.0, 180.0, 55.0));
        when(foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of());
        when(recipeLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of());

        NextMealSuggestionDto result = service.getNextMeal(EMAIL);

        assertEquals(NextMealStatus.NO_UPCOMING_MEAL, result.getStatus());
        assertEquals(null, result.getMealType());
        assertEquals(LocalDateTime.of(TODAY, LocalTime.of(23, 30)), result.getGeneratedAt());
        verify(recipeService, never()).getPublicRecipes(
                any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void getNextMeal_atLunchTimeWithoutLogs_doesNotSuggestMissedBreakfast() {
        when(userTimeZoneSupport.now(user)).thenReturn(LocalDateTime.of(TODAY, LocalTime.of(13, 0)));
        when(dashboardService.getDailySummary(EMAIL, TODAY)).thenReturn(summary(1200.0, 70.0, 140.0, 40.0));
        when(foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of());
        when(recipeLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of());
        when(nutritionPreferenceService.getForPersonalization(EMAIL)).thenReturn(new UserNutritionPreferenceDto());
        when(nutritionPreferenceService.isPersonalizationAllowed(EMAIL)).thenReturn(true);
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("LUNCH"), eq(MarketRegion.UK_IE), eq("en"),
                any(), any(), any(), eq(0), eq(50)))
                .thenReturn(recipePage());
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("LUNCH"), eq(MarketRegion.UK_IE), eq(null),
                any(), any(), any(), eq(0), eq(50)))
                .thenReturn(recipePage());
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("LUNCH"), eq(null), eq(null),
                any(), any(), any(), eq(0), eq(50)))
                .thenReturn(recipePage());

        NextMealSuggestionDto result = service.getNextMeal(EMAIL);

        assertEquals(NextMealStatus.READY, result.getStatus());
        assertEquals("LUNCH", result.getMealType());
        assertEquals(553.8, result.getTargetCalories());
        assertEquals(35.0, result.getTargetProtein());
        assertEquals(75.4, result.getTargetCarbs());
        assertEquals(16.0, result.getTargetFat());
    }

    @Test
    void getNextMeal_atDinnerTimeWithoutLogs_doesNotSuggestEarlierMeals() {
        when(userTimeZoneSupport.now(user)).thenReturn(LocalDateTime.of(TODAY, LocalTime.of(18, 0)));
        when(dashboardService.getDailySummary(EMAIL, TODAY)).thenReturn(summary(800.0, 45.0, 90.0, 30.0));
        when(foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of());
        when(recipeLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of());
        when(nutritionPreferenceService.getForPersonalization(EMAIL)).thenReturn(new UserNutritionPreferenceDto());
        when(nutritionPreferenceService.isPersonalizationAllowed(EMAIL)).thenReturn(true);
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("DINNER"), eq(MarketRegion.UK_IE), eq("en"),
                any(), any(), any(), eq(0), eq(50)))
                .thenReturn(recipePage());
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("DINNER"), eq(MarketRegion.UK_IE), eq(null),
                any(), any(), any(), eq(0), eq(50)))
                .thenReturn(recipePage());
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("DINNER"), eq(null), eq(null),
                any(), any(), any(), eq(0), eq(50)))
                .thenReturn(recipePage());

        NextMealSuggestionDto result = service.getNextMeal(EMAIL);

        assertEquals(NextMealStatus.READY, result.getStatus());
        assertEquals("DINNER", result.getMealType());
        assertEquals(800.0, result.getTargetCalories());
    }

    @Test
    void getNextMeal_breakfastEndsAndLunchStartsAt1130() {
        stubEmptyDay(LocalTime.of(11, 29), summary(1000.0, 60.0, 120.0, 40.0));
        assertEquals("BREAKFAST", service.getNextMeal(EMAIL).getMealType());

        when(userTimeZoneSupport.now(user)).thenReturn(LocalDateTime.of(TODAY, LocalTime.of(11, 30)));
        assertEquals("LUNCH", service.getNextMeal(EMAIL).getMealType());
    }

    @Test
    void getNextMeal_between1600And1759_targetsEarlySnackAndReservesDinnerBudget() {
        stubEmptyDay(LocalTime.of(16, 0), summary(1000.0, 60.0, 120.0, 40.0));

        NextMealSuggestionDto result = service.getNextMeal(EMAIL);

        assertEquals(NextMealStatus.READY, result.getStatus());
        assertEquals("SNACK", result.getMealType());
        assertEquals(200.0, result.getTargetCalories());
        assertEquals(12.0, result.getTargetProtein());
    }

    @Test
    void getNextMeal_at1800_targetsDinner() {
        stubEmptyDay(LocalTime.of(18, 0), summary(800.0, 45.0, 90.0, 30.0));

        NextMealSuggestionDto result = service.getNextMeal(EMAIL);

        assertEquals("DINNER", result.getMealType());
        assertEquals(800.0, result.getTargetCalories());
    }

    @Test
    void getNextMeal_between2200And2259_targetsSnackWhenCaloriesRemain() {
        stubEmptyDay(LocalTime.of(22, 0), summary(320.0, 18.0, 35.0, 10.0));

        NextMealSuggestionDto result = service.getNextMeal(EMAIL);

        assertEquals(NextMealStatus.READY, result.getStatus());
        assertEquals("SNACK", result.getMealType());
        assertEquals(320.0, result.getTargetCalories());
    }

    @Test
    void getNextMeal_at2300_hasNoUpcomingMeal() {
        stubEmptyDay(LocalTime.of(23, 0), summary(320.0, 18.0, 35.0, 10.0));

        NextMealSuggestionDto result = service.getNextMeal(EMAIL);

        assertEquals(NextMealStatus.NO_UPCOMING_MEAL, result.getStatus());
        assertEquals(null, result.getMealType());
    }

    @Test
    void getNextMeal_whenMainMealsAreLoggedAndCaloriesRemain_targetsSnack() {
        when(userTimeZoneSupport.now(user)).thenReturn(LocalDateTime.of(TODAY, LocalTime.of(22, 0)));
        when(dashboardService.getDailySummary(EMAIL, TODAY)).thenReturn(summary(420.0, 24.0, 45.0, 14.0));
        FoodLogsEntity breakfast = new FoodLogsEntity();
        breakfast.setMealType("BREAKFAST");
        FoodLogsEntity lunch = new FoodLogsEntity();
        lunch.setMealType("LUNCH");
        FoodLogsEntity dinner = new FoodLogsEntity();
        dinner.setMealType("DINNER");
        when(foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of(breakfast, lunch, dinner));
        when(recipeLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of());
        lenient().when(nutritionPreferenceService.getForPersonalization(EMAIL))
                .thenReturn(new UserNutritionPreferenceDto());
        lenient().when(nutritionPreferenceService.isPersonalizationAllowed(EMAIL)).thenReturn(true);
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("SNACK"), eq(MarketRegion.UK_IE), eq("en"),
                any(), any(), any(), eq(0), eq(50)))
                .thenReturn(recipePage());
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("SNACK"), eq(MarketRegion.UK_IE), eq(null),
                any(), any(), any(), eq(0), eq(50)))
                .thenReturn(recipePage());
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("SNACK"), eq(null), eq(null),
                any(), any(), any(), eq(0), eq(50)))
                .thenReturn(recipePage());

        NextMealSuggestionDto result = service.getNextMeal(EMAIL);

        assertEquals(NextMealStatus.READY, result.getStatus());
        assertEquals("SNACK", result.getMealType());
        assertEquals(420.0, result.getTargetCalories());
    }

    @Test
    void getNextMeal_recentlyLoggedRecipeIsRankedBelowFreshComparableRecipe() {
        when(dashboardService.getDailySummary(EMAIL, TODAY)).thenReturn(summary(1180.0, 72.0, 130.0, 44.0));
        FoodLogsEntity breakfast = new FoodLogsEntity();
        breakfast.setMealType("BREAKFAST");
        when(foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of(breakfast));
        when(recipeLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of());
        when(recipeLogRepository.findRecentRecipeUsage(eq(user), any()))
                .thenReturn(List.<Object[]>of(new Object[]{
                        10L, TODAY.minusDays(1).atTime(12, 0), 2L
                }));
        when(nutritionPreferenceService.getForPersonalization(EMAIL)).thenReturn(new UserNutritionPreferenceDto());
        when(nutritionPreferenceService.isPersonalizationAllowed(EMAIL)).thenReturn(true);
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("LUNCH"), eq(MarketRegion.UK_IE), eq("en"),
                any(), any(), any(), eq(0), eq(50)))
                .thenReturn(recipePage(recipe(), freshComparableRecipe()));

        NextMealSuggestionDto result = service.getNextMeal(EMAIL);

        assertEquals(NextMealStatus.READY, result.getStatus());
        assertEquals("Fresh salmon bowl", result.getRecipeSuggestions().get(0).getName());
        assertTrue(result.getRecipeSuggestions().stream()
                .noneMatch(suggestion -> suggestion.getRecipeId().equals(10L)));
    }

    @Test
    void getNextMeal_assignsBestQuickAndVarietyStrategiesToUniqueRecipes() {
        when(dashboardService.getDailySummary(EMAIL, TODAY)).thenReturn(summary(1180.0, 72.0, 130.0, 44.0));
        FoodLogsEntity breakfast = new FoodLogsEntity();
        breakfast.setMealType("BREAKFAST");
        when(foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of(breakfast));
        when(recipeLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of());
        when(nutritionPreferenceService.getForPersonalization(EMAIL)).thenReturn(new UserNutritionPreferenceDto());
        when(nutritionPreferenceService.isPersonalizationAllowed(EMAIL)).thenReturn(true);
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("LUNCH"), eq(MarketRegion.UK_IE), eq("en"),
                any(), any(), any(), eq(0), eq(50)))
                .thenReturn(recipePage(idealRecipe(), freshComparableRecipe(), quickRecipe()));

        NextMealSuggestionDto result = service.getNextMeal(EMAIL);

        assertEquals(3, result.getRecipeSuggestions().size());
        assertEquals(3, result.getRecipeSuggestions().stream()
                .map(suggestion -> suggestion.getRecipeId())
                .distinct()
                .count());
        assertTrue(result.getRecipeSuggestions().stream()
                .anyMatch(suggestion -> suggestion.getStrategy() == NextMealSuggestionStrategy.BEST_MATCH));
        assertTrue(result.getRecipeSuggestions().stream()
                .anyMatch(suggestion -> suggestion.getStrategy() == NextMealSuggestionStrategy.QUICK_OPTION
                        && suggestion.getReasonCodes().contains(NextMealSuggestionReason.QUICK_OPTION)));
        assertTrue(result.getRecipeSuggestions().stream()
                .anyMatch(suggestion -> suggestion.getStrategy() == NextMealSuggestionStrategy.VARIETY
                        && suggestion.getReasonCodes().contains(NextMealSuggestionReason.VARIETY_PICK)));
    }

    @Test
    void getNextMeal_recentlyDismissedRecipeIsDeprioritized() {
        when(dashboardService.getDailySummary(EMAIL, TODAY)).thenReturn(summary(1180.0, 72.0, 130.0, 44.0));
        FoodLogsEntity breakfast = new FoodLogsEntity();
        breakfast.setMealType("BREAKFAST");
        when(foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of(breakfast));
        when(recipeLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of());
        when(productAnalyticsEventRepository.findRecentlyDismissedNextMealRecipeIds(eq(user), any()))
                .thenReturn(List.of(14L));
        when(nutritionPreferenceService.getForPersonalization(EMAIL)).thenReturn(new UserNutritionPreferenceDto());
        when(nutritionPreferenceService.isPersonalizationAllowed(EMAIL)).thenReturn(true);
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("LUNCH"), eq(MarketRegion.UK_IE), eq("en"),
                any(), any(), any(), eq(0), eq(50)))
                .thenReturn(recipePage(idealRecipe(), freshComparableRecipe()));

        NextMealSuggestionDto result = service.getNextMeal(EMAIL);

        assertEquals("Fresh salmon bowl", result.getRecipeSuggestions().get(0).getName());
        assertTrue(result.getRecipeSuggestions().stream()
                .noneMatch(suggestion -> suggestion.getRecipeId().equals(14L)));
    }

    @Test
    void getNextMeal_whenDailyTargetReached_doesNotQueryRecipes() {
        when(dashboardService.getDailySummary(EMAIL, TODAY)).thenReturn(summary(0.0, 0.0, 0.0, 0.0));

        NextMealSuggestionDto result = service.getNextMeal(EMAIL);

        assertEquals(NextMealStatus.TARGET_REACHED, result.getStatus());
        verify(recipeService, never()).getPublicRecipes(
                any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    private DailySummaryDto summary(double calories, double protein, double carbs, double fat) {
        DailySummaryDto summary = new DailySummaryDto();
        summary.setHasActiveGoal(true);
        summary.setRemainingCalories(calories);
        summary.setRemainingProtein(protein);
        summary.setRemainingCarbs(carbs);
        summary.setRemainingFat(fat);
        return summary;
    }

    private void stubEmptyDay(LocalTime time, DailySummaryDto summary) {
        when(userTimeZoneSupport.now(user)).thenReturn(LocalDateTime.of(TODAY, time));
        when(dashboardService.getDailySummary(EMAIL, TODAY)).thenReturn(summary);
        when(foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of());
        when(recipeLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of());
        lenient().when(nutritionPreferenceService.getForPersonalization(EMAIL))
                .thenReturn(new UserNutritionPreferenceDto());
        lenient().when(nutritionPreferenceService.isPersonalizationAllowed(EMAIL)).thenReturn(true);
    }

    private RecipePageDto recipePage(RecipeDto... recipes) {
        RecipePageDto page = new RecipePageDto();
        page.setContent(List.of(recipes));
        return page;
    }

    private RecipeDto recipe() {
        RecipeDto recipe = new RecipeDto();
        recipe.setId(10L);
        recipe.setName("Chicken bowl");
        recipe.setImageUrl("https://example.test/chicken.jpg");
        recipe.setCategories(Set.of(RecipeCategory.HIGH_PROTEIN, RecipeCategory.CHICKEN));
        recipe.setPerServingNutrition(new RecipeNutritionDto(
                530.0, 36.0, 58.0, 17.0,
                8.0, 5.0, 3.0, 600.0,
                700.0, 80.0, 120.0, 4.0,
                60.0, 3.0, 200.0, 30.0,
                2.0, 3.0, 1.0
        ));
        return recipe;
    }

    private RecipeDto macroMismatchRecipe() {
        RecipeDto recipe = new RecipeDto();
        recipe.setId(11L);
        recipe.setName("Macro mismatch bowl");
        recipe.setPerServingNutrition(new RecipeNutritionDto(
                530.0, 36.0, 50.0, 25.0,
                2.0, 1.0, 10.0, 800.0,
                300.0, 50.0, 40.0, 1.0,
                20.0, 1.0, 50.0, 5.0,
                1.0, 1.0, 0.5
        ));
        return recipe;
    }

    private RecipeDto freshComparableRecipe() {
        RecipeDto recipe = new RecipeDto();
        recipe.setId(12L);
        recipe.setName("Fresh salmon bowl");
        recipe.setCategories(Set.of(RecipeCategory.FISH));
        recipe.setPerServingNutrition(new RecipeNutritionDto(
                550.0, 34.0, 65.0, 20.0,
                7.0, 4.0, 4.0, 500.0,
                650.0, 70.0, 100.0, 3.0,
                50.0, 2.0, 180.0, 20.0,
                2.0, 2.0, 1.0
        ));
        return recipe;
    }

    private RecipeDto quickRecipe() {
        RecipeDto recipe = new RecipeDto();
        recipe.setId(13L);
        recipe.setName("Quick turkey wrap");
        recipe.setCategories(Set.of(RecipeCategory.QUICK_MEAL, RecipeCategory.MEAT));
        recipe.setPerServingNutrition(new RecipeNutritionDto(
                540.0, 35.0, 66.0, 19.0,
                6.0, 4.0, 3.0, 550.0,
                620.0, 60.0, 90.0, 3.0,
                45.0, 2.0, 160.0, 18.0,
                2.0, 2.0, 1.0
        ));
        return recipe;
    }

    private RecipeDto idealRecipe() {
        RecipeDto recipe = new RecipeDto();
        recipe.setId(14L);
        recipe.setName("Target chicken bowl");
        recipe.setCategories(Set.of(RecipeCategory.CHICKEN));
        recipe.setPerServingNutrition(new RecipeNutritionDto(
                544.6, 36.0, 70.0, 17.6,
                7.0, 4.0, 4.0, 500.0,
                650.0, 70.0, 100.0, 3.0,
                50.0, 2.0, 180.0, 20.0,
                2.0, 2.0, 1.0
        ));
        return recipe;
    }
}
