package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.service.impl.OpenAiAiMealDraftProviderClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "grun.live-ai-smoke", matches = "true")
class OpenAiNutritionPlanLiveSmokeTest {

    private static OpenAiAiMealDraftProviderClient client;

    @BeforeAll
    static void setUpClient() {
        String apiKey = requiredEnv("GRUN_AI_OPENAI_API_KEY");
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider(AiProvider.OPENAI);
        properties.setModel(environment("GRUN_AI_MODEL", "gpt-5.4-mini"));
        properties.setPromptVersion(environment(
                "GRUN_AI_PROMPT_VERSION", "ai-nutrition-live-smoke-v1"));
        properties.getOpenai().setApiKey(apiKey);
        properties.getOpenai().setBaseUrl(environment(
                "GRUN_AI_OPENAI_BASE_URL", "https://api.openai.com/v1/responses"));
        properties.getOpenai().setMaxOutputTokens(6000);
        properties.getOpenai().setRepairEnabled(true);
        properties.getOpenai().setMaxRepairAttempts(1);

        client = new OpenAiAiMealDraftProviderClient(
                properties, new RestTemplateBuilder(),
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void liveProviderReturnsUsableGeneralWorkoutAndPreparationContracts() {
        AiNutritionPlanDraftResponseDto general =
                client.createNutritionPlanDraft(nutritionRequest(NutritionPlanGenerationMode.GENERAL));
        assertNutritionContract(general, NutritionPlanGenerationMode.GENERAL);

        AiNutritionPlanDraftResponseDto workout =
                client.createNutritionPlanDraft(nutritionRequest(NutritionPlanGenerationMode.WORKOUT_ALIGNED));
        assertNutritionContract(workout, NutritionPlanGenerationMode.WORKOUT_ALIGNED);

        AiPreparationGuideResponseDto guide =
                client.createPreparationGuide(preparationRequest());
        assertNotNull(guide);
        assertFalse(guide.getIngredients().isEmpty());
        assertFalse(guide.getSteps().isEmpty());
        assertFalse(guide.getFoodSafetyNotes().isEmpty());
        assertNotNull(guide.getQualityScore());
        assertNotNull(guide.getConfidence());
        assertTrue(guide.getTotalTokens() != null && guide.getTotalTokens() > 0);
    }

    private static AiNutritionPlanDraftRequestDto nutritionRequest(
            NutritionPlanGenerationMode mode) {
        LocalDate date = LocalDate.now().plusDays(1);
        AiNutritionPlanDraftRequestDto request = new AiNutritionPlanDraftRequestDto();
        request.setGenerationMode(mode);
        request.setWorkoutPlanId(mode == NutritionPlanGenerationMode.WORKOUT_ALIGNED ? 101L : null);
        request.setStartDate(date);
        request.setDayCount(1);
        request.setMealsPerDay(2);
        request.setPreferredMealTimes(List.of(LocalTime.of(8, 0), LocalTime.of(19, 30)));
        request.setExcludedFoods(List.of("peanuts"));
        request.setDietaryPreferences(List.of("high protein"));
        request.setBudgetPreference("MODERATE");
        request.setPreparationTimePreference("QUICK");
        request.setIncludeRecipeSuggestions(false);
        request.setLanguage("en");
        request.setTrustedDailyTarget(nutrition(2000.0, 125.0, 225.0, 65.0));
        request.setTrustedUserContext(Map.of(
                "age", 32,
                "gender", "MALE",
                "heightCm", 180,
                "weightKg", 82,
                "marketRegion", "UK_IE",
                "preferredLanguage", "en",
                "goalType", "MAINTAIN_WEIGHT",
                "activityLevel", "MODERATE"));

        if (mode == NutritionPlanGenerationMode.WORKOUT_ALIGNED) {
            WorkoutNutritionContextDto workout = new WorkoutNutritionContextDto();
            workout.setWorkoutPlanId(101L);
            workout.setWorkoutPlanName("Synthetic full-body session");
            workout.setScheduleVersion("workout_schedule_v1");
            workout.setScheduleUpdatedAt(LocalDateTime.now().minusHours(1));
            workout.setSessions(List.of(new WorkoutNutritionSessionContextDto(
                    date, LocalTime.of(18, 0), 50,
                    WorkoutSessionIntensity.MODERATE, "Full-body strength",
                    8, 24, 0)));
            request.setTrustedWorkoutContext(workout);
        }
        return request;
    }

    private static AiPreparationGuideProviderRequestDto preparationRequest() {
        AiPreparationGuideProviderRequestDto request =
                new AiPreparationGuideProviderRequestDto();
        request.setMealPlanId(201L);
        request.setMealPlanItemId(301L);
        request.setPlanDate(LocalDate.now().plusDays(1));
        request.setMealType("DINNER");
        request.setItemName("Chicken breast with rice and broccoli");
        request.setItemDescription("A simple high-protein dinner.");
        request.setPlannedQuantity(1.0);
        request.setPlannedUnit(FoodPortionUnit.SERVING);
        request.setPlannedNutrition(nutrition(620.0, 52.0, 68.0, 15.0));
        request.setShortPreparationState("Grilled chicken, cooked rice, steamed broccoli");
        request.setWorkoutRelation(MealPlanWorkoutRelation.POST_WORKOUT);
        request.setAllergens(List.of());
        request.setWarnings(List.of());
        request.setLanguage("en");
        return request;
    }

    private static void assertNutritionContract(
            AiNutritionPlanDraftResponseDto response,
            NutritionPlanGenerationMode mode) {
        assertNotNull(response);
        assertEquals(mode, response.getGenerationMode());
        assertEquals(1, response.getDays().size());
        assertEquals(2, response.getDays().get(0).getMeals().size());
        assertNotNull(response.getDays().get(0).getTotalNutrition());
        assertTrue(response.getDays().get(0).getMeals().stream()
                .allMatch(meal -> meal.getItems() != null && !meal.getItems().isEmpty()));
        assertNotNull(response.getQualityScore());
        assertNotNull(response.getConfidence());
        assertTrue(response.getTotalTokens() != null && response.getTotalTokens() > 0);
    }

    private static MealPlanNutritionSnapshotDto nutrition(
            double calories, double protein, double carbs, double fat) {
        MealPlanNutritionSnapshotDto value = new MealPlanNutritionSnapshotDto();
        value.setCalories(calories);
        value.setProtein(protein);
        value.setCarbs(carbs);
        value.setFat(fat);
        value.setFiber(25.0);
        value.setSugar(45.0);
        value.setSaturatedFat(18.0);
        value.setSodium(2.0);
        value.setPotassium(3500.0);
        value.setCholesterol(250.0);
        value.setCalcium(900.0);
        value.setIron(12.0);
        value.setMagnesium(350.0);
        value.setZinc(10.0);
        value.setVitaminA(800.0);
        value.setVitaminC(75.0);
        value.setVitaminD(10.0);
        value.setVitaminE(12.0);
        value.setVitaminB12(2.4);
        return value;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the live smoke test.");
        }
        return value;
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
