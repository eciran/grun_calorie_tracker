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
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.RecipeAllergen;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.repository.FoodLogsRepository;
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
        when(nutritionPreferenceService.get(EMAIL)).thenReturn(preferences);
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("LUNCH"), eq(MarketRegion.UK_IE), eq("en"),
                any(), eq(Set.of(RecipeAllergen.MILK)), any(), eq(0), eq(50)))
                .thenReturn(recipePage(recipe()));

        NextMealSuggestionDto result = service.getNextMeal(EMAIL);

        assertEquals(NextMealStatus.READY, result.getStatus());
        assertEquals("LUNCH", result.getMealType());
        assertEquals(544.6, result.getTargetCalories());
        assertEquals(33.2, result.getTargetProtein());
        assertEquals(1, result.getRecipeSuggestions().size());
        assertEquals("Chicken bowl", result.getRecipeSuggestions().get(0).getName());
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
        when(nutritionPreferenceService.get(EMAIL)).thenReturn(new UserNutritionPreferenceDto());
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
    }

    @Test
    void getNextMeal_lateAtNightWithoutLogs_stillTargetsBreakfast() {
        when(userTimeZoneSupport.now(user)).thenReturn(LocalDateTime.of(TODAY, LocalTime.of(23, 30)));
        when(dashboardService.getDailySummary(EMAIL, TODAY)).thenReturn(summary(1600.0, 90.0, 180.0, 55.0));
        when(foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of());
        when(recipeLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                eq(user), any(), any())).thenReturn(List.of());
        when(nutritionPreferenceService.get(EMAIL)).thenReturn(new UserNutritionPreferenceDto());
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("BREAKFAST"), eq(MarketRegion.UK_IE), eq("en"),
                any(), any(), any(), eq(0), eq(50)))
                .thenReturn(recipePage());
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("BREAKFAST"), eq(MarketRegion.UK_IE), eq(null),
                any(), any(), any(), eq(0), eq(50)))
                .thenReturn(recipePage());
        when(recipeService.getPublicRecipes(
                eq(EMAIL), eq(null), eq("BREAKFAST"), eq(null), eq(null),
                any(), any(), any(), eq(0), eq(50)))
                .thenReturn(recipePage());

        NextMealSuggestionDto result = service.getNextMeal(EMAIL);

        assertEquals(NextMealStatus.READY, result.getStatus());
        assertEquals("BREAKFAST", result.getMealType());
        assertEquals(LocalDateTime.of(TODAY, LocalTime.of(23, 30)), result.getGeneratedAt());
    }

    @Test
    void getNextMeal_whenMainMealsAreLoggedAndCaloriesRemain_targetsSnack() {
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
        when(nutritionPreferenceService.get(EMAIL)).thenReturn(new UserNutritionPreferenceDto());
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
        recipe.setPerServingNutrition(new RecipeNutritionDto(
                530.0, 36.0, 58.0, 17.0,
                8.0, 5.0, 3.0, 600.0,
                700.0, 80.0, 120.0, 4.0,
                60.0, 3.0, 200.0, 30.0,
                2.0, 3.0, 1.0
        ));
        return recipe;
    }
}
