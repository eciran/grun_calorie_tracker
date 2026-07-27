package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.MealPlanEntity;
import com.grun.calorietracker.entity.MealPlanItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import com.grun.calorietracker.entity.WorkoutPlanEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.AiProviderException;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.impl.AiNutritionPlanServiceImpl;
import com.grun.calorietracker.service.impl.AiProviderConfigurationValidatorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiNutritionPlanServiceImplTest {

    private AiProperties properties;
    private AiMealDraftProviderClient provider;
    private AiRequestHistoryRepository historyRepository;
    private UserRepository userRepository;
    private GoalRepository goalRepository;
    private MealPlanRepository mealPlanRepository;
    private WorkoutPlanRepository workoutPlanRepository;
    private MealPlanService mealPlanService;
    private SubscriptionService subscriptionService;
    private AiCreditPricingService aiCreditPricingService;
    private UserNutritionPreferenceService nutritionPreferenceService;
    private ObjectMapper objectMapper;
    private AiNutritionPlanServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider(AiProvider.LOG);
        properties.setModel("log-nutrition-v1");
        properties.setPromptVersion("nutrition-v1");
        provider = mock(AiMealDraftProviderClient.class);
        historyRepository = mock(AiRequestHistoryRepository.class);
        userRepository = mock(UserRepository.class);
        goalRepository = mock(GoalRepository.class);
        mealPlanRepository = mock(MealPlanRepository.class);
        workoutPlanRepository = mock(WorkoutPlanRepository.class);
        mealPlanService = mock(MealPlanService.class);
        subscriptionService = mock(SubscriptionService.class);
        aiCreditPricingService = mock(AiCreditPricingService.class);
        nutritionPreferenceService = mock(UserNutritionPreferenceService.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new AiNutritionPlanServiceImpl(
                properties, List.of(provider), historyRepository, userRepository,
                goalRepository, mealPlanRepository, workoutPlanRepository, mealPlanService,
                subscriptionService, aiCreditPricingService, nutritionPreferenceService, objectMapper,
                new AiProviderConfigurationValidatorImpl(
                        properties,
                        mock(AiOperationsPolicyService.class)
                ));
        user = new UserEntity();
        user.setId(7L);
        user.setEmail("user@example.com");
        when(subscriptionService.resolveAiCreditCost(
                anyString(), eq(SubscriptionFeature.AI_NUTRITION_PLAN))).thenReturn(1);
        when(aiCreditPricingService.estimateNutrition(anyInt(), anyInt(), anyBoolean()))
                .thenAnswer(invocation -> {
                    boolean workoutAligned = invocation.getArgument(2);
                    return new AiCreditCostEstimateDto(SubscriptionFeature.AI_NUTRITION_PLAN,
                            1, 2, 4, 8, 0, workoutAligned, workoutAligned ? 1 : 0,
                            workoutAligned ? 2 : 1);
                });
    }

    @Test
    void estimateCreditCost_usesMealSlotPricingWithoutConsumingQuota() {
        when(aiCreditPricingService.estimateNutrition(7, 4, false))
                .thenReturn(new AiCreditCostEstimateDto(SubscriptionFeature.AI_NUTRITION_PLAN,
                        3, 28, 4, 8, 3, false, 0, 6));

        AiNutritionPlanCreditEstimateDto result =
                service.estimateCreditCost("user@example.com", 7, 4, NutritionPlanGenerationMode.GENERAL);

        assertEquals(7, result.getDayCount());
        assertEquals(NutritionPlanGenerationMode.GENERAL, result.getGenerationMode());
        assertEquals(3, result.getBaseCreditCost());
        assertEquals(4, result.getMealsPerDay());
        assertEquals(28, result.getTotalMealSlots());
        assertEquals(4, result.getIncludedMealSlots());
        assertEquals(8, result.getMealSlotsPerAdditionalCredit());
        assertEquals(3, result.getAdditionalCredits());
        assertFalse(result.getWorkoutContextIncluded());
        assertEquals(0, result.getWorkoutContextCreditCost());
        assertEquals(6, result.getTotalCreditCost());
        verify(subscriptionService, never()).consumeAiQuota(anyString(), anyInt());
        verifyNoInteractions(provider);
    }

    @Test
    void estimateCreditCost_workoutAlignedIncludesContextSurcharge() {
        when(aiCreditPricingService.estimateNutrition(7, 4, true))
                .thenReturn(new AiCreditCostEstimateDto(SubscriptionFeature.AI_NUTRITION_PLAN,
                        3, 28, 4, 8, 3, true, 3, 9));

        AiNutritionPlanCreditEstimateDto result = service.estimateCreditCost(
                "user@example.com", 7, 4, NutritionPlanGenerationMode.WORKOUT_ALIGNED);

        assertEquals(4, result.getMealsPerDay());
        assertEquals(28, result.getTotalMealSlots());
        assertEquals(4, result.getIncludedMealSlots());
        assertEquals(8, result.getMealSlotsPerAdditionalCredit());
        assertEquals(3, result.getAdditionalCredits());
        assertTrue(result.getWorkoutContextIncluded());
        assertEquals(3, result.getWorkoutContextCreditCost());
        assertEquals(9, result.getTotalCreditCost());
        verify(subscriptionService, never()).consumeAiQuota(anyString(), anyInt());
    }

    @Test
    void estimateCreditCost_rejectsUnsupportedPricingPolicy() {
        when(aiCreditPricingService.estimateNutrition(8, 4, false))
                .thenThrow(new IllegalArgumentException(
                        "Nutrition-plan day count must be between 1 and 7."));

        assertThrows(IllegalArgumentException.class,
                () -> service.estimateCreditCost("user@example.com", 8, 4, NutritionPlanGenerationMode.GENERAL));

        verify(subscriptionService).assertFeatureAccess("user@example.com", SubscriptionFeature.AI_NUTRITION_PLAN);
        verify(aiCreditPricingService).estimateNutrition(8, 4, false);
    }

    @Test
    void createDraft_consumesQuotaOnlyAfterUsableProviderResponse() {
        prepareUserAndGoal();
        when(provider.provider()).thenReturn(AiProvider.LOG);
        when(provider.createNutritionPlanDraft(any())).thenReturn(validResponse());
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(
                any(), any(), any())).thenReturn(Optional.empty());
        when(historyRepository.save(any())).thenAnswer(invocation -> {
            AiRequestHistoryEntity history = invocation.getArgument(0);
            history.setId(41L);
            return history;
        });
        SubscriptionDto quota = new SubscriptionDto();
        quota.setAiRemainingThisPeriod(9);
        when(subscriptionService.consumeAiQuota("user@example.com", 1)).thenReturn(quota);

        AiNutritionPlanDraftResponseDto result = service.createDraft(
                "user@example.com", "nutrition-key-001", request());

        assertEquals(41L, result.getRequestId());
        assertEquals(9, result.getAiRemainingThisPeriod());
        assertEquals(AiRequestType.AI_NUTRITION_PLAN, result.getRequestType());
        verify(subscriptionService).assertFeatureAccess(
                "user@example.com", SubscriptionFeature.AI_NUTRITION_PLAN);
        var order = inOrder(provider, subscriptionService);
        order.verify(provider).createNutritionPlanDraft(any());
        order.verify(subscriptionService).consumeAiQuota("user@example.com", 1);

        ArgumentCaptor<AiRequestHistoryEntity> captor =
                ArgumentCaptor.forClass(AiRequestHistoryEntity.class);
        verify(historyRepository, times(2)).save(captor.capture());
        AiRequestHistoryEntity saved = captor.getAllValues().get(1);
        assertEquals(AiRequestStatus.DRAFT_CREATED, saved.getStatus());
        assertTrue(saved.getQuotaConsumed());
        assertEquals(1, saved.getQuotaConsumedAmount());
        assertEquals("nutrition-key-001", saved.getIdempotencyKey());
        assertFalse(saved.getInputPayload().contains("peanuts"));
    }
    @Test
    void createDraft_usesAdminBaseCreditCostForOneDayPlan() {
        prepareUserAndGoal();
        when(aiCreditPricingService.estimateNutrition(1, 2, false))
                .thenReturn(new AiCreditCostEstimateDto(SubscriptionFeature.AI_NUTRITION_PLAN,
                        3, 2, 4, 8, 0, false, 0, 3));
        when(provider.provider()).thenReturn(AiProvider.LOG);
        when(provider.createNutritionPlanDraft(any())).thenReturn(validResponse());
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(
                any(), any(), any())).thenReturn(Optional.empty());
        when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SubscriptionDto quota = new SubscriptionDto();
        quota.setAiBaseRemainingThisPeriod(5);
        quota.setAiAddonRemainingThisPeriod(2);
        quota.setAiRemainingThisPeriod(7);
        when(subscriptionService.consumeAiQuota("user@example.com", 3)).thenReturn(quota);

        AiNutritionPlanDraftResponseDto result = service.createDraft(
                "user@example.com", "nutrition-key-weighted-cost", request());

        assertEquals(3, result.getQuotaConsumedAmount());
        assertEquals(5, result.getAiBaseRemainingThisPeriod());
        assertEquals(2, result.getAiAddonRemainingThisPeriod());
        assertEquals(7, result.getAiRemainingThisPeriod());
        verify(subscriptionService).consumeAiQuota("user@example.com", 3);
        ArgumentCaptor<AiRequestHistoryEntity> captor =
                ArgumentCaptor.forClass(AiRequestHistoryEntity.class);
        verify(historyRepository, times(2)).save(captor.capture());
        assertEquals(3, captor.getAllValues().get(1).getQuotaConsumedAmount());
    }

    @Test
    void createDraft_computesMealAndDayTotalsWhenCompactProviderOmitsThem() throws Exception {
        prepareUserAndGoal();
        when(provider.provider()).thenReturn(AiProvider.LOG);
        AiNutritionPlanDraftResponseDto compact = validResponse();
        compact.getDays().get(0).setTotalNutrition(null);
        compact.getDays().get(0).getMeals().forEach(meal -> meal.setTotalNutrition(null));
        MealPlanNutritionSnapshotDto dailyMicronutrients = new MealPlanNutritionSnapshotDto();
        dailyMicronutrients.setSodium(1800.0);
        dailyMicronutrients.setPotassium(3500.0);
        compact.getDays().get(0).setDailyMicronutrients(dailyMicronutrients);
        when(provider.createNutritionPlanDraft(any())).thenReturn(compact);
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(
                any(), any(), any())).thenReturn(Optional.empty());
        when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SubscriptionDto quota = new SubscriptionDto();
        quota.setAiRemainingThisPeriod(9);
        when(subscriptionService.consumeAiQuota("user@example.com", 1)).thenReturn(quota);

        AiNutritionPlanDraftResponseDto result = service.createDraft(
                "user@example.com", "nutrition-key-compact-totals", request());

        AiNutritionPlanDayDto day = result.getDays().get(0);
        assertEquals(1000.0, day.getMeals().get(0).getTotalNutrition().getCalories());
        assertEquals(2000.0, day.getTotalNutrition().getCalories());
        assertEquals(120.0, day.getTotalNutrition().getProtein());
        assertEquals(1800.0, day.getTotalNutrition().getSodium());
        assertEquals(3500.0, day.getTotalNutrition().getPotassium());
        assertFalse(objectMapper.writeValueAsString(result).contains("dailyMicronutrients"));
        verify(subscriptionService).consumeAiQuota("user@example.com", 1);
    }
    @Test
    void createDraft_withOptionalProfileFieldsMissing_stillUsesTrustedTargets() {
        prepareUserAndGoal();
        when(provider.provider()).thenReturn(AiProvider.LOG);
        when(provider.createNutritionPlanDraft(any())).thenReturn(validResponse());
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(
                any(), any(), any())).thenReturn(Optional.empty());
        when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SubscriptionDto quota = new SubscriptionDto();
        quota.setAiRemainingThisPeriod(9);
        when(subscriptionService.consumeAiQuota("user@example.com", 1)).thenReturn(quota);

        service.createDraft("user@example.com", "nutrition-key-optional-profile", request());

        ArgumentCaptor<AiNutritionPlanDraftRequestDto> captor =
                ArgumentCaptor.forClass(AiNutritionPlanDraftRequestDto.class);
        verify(provider).createNutritionPlanDraft(captor.capture());
        @SuppressWarnings("unchecked")
        var context = (java.util.Map<String, Object>) captor.getValue().getTrustedUserContext();
        assertNull(context.get("age"));
        assertNull(context.get("gender"));
        assertEquals(2000.0, captor.getValue().getTrustedDailyTarget().getCalories());
    }

    @Test
    void createDraft_persistsProviderUsageAndCostTelemetry() {
        prepareUserAndGoal();
        when(provider.provider()).thenReturn(AiProvider.LOG);
        AiNutritionPlanDraftResponseDto response = validResponse();
        response.setPromptTokens(120);
        response.setCompletionTokens(80);
        response.setTotalTokens(200);
        response.setEstimatedCost(0.0042);
        response.setCostCurrency("USD");
        when(provider.createNutritionPlanDraft(any())).thenReturn(response);
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(
                any(), any(), any())).thenReturn(Optional.empty());
        when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SubscriptionDto quota = new SubscriptionDto();
        quota.setAiRemainingThisPeriod(9);
        when(subscriptionService.consumeAiQuota("user@example.com", 1)).thenReturn(quota);

        service.createDraft("user@example.com", "nutrition-key-telemetry", request());

        ArgumentCaptor<AiRequestHistoryEntity> captor =
                ArgumentCaptor.forClass(AiRequestHistoryEntity.class);
        verify(historyRepository, times(2)).save(captor.capture());
        AiRequestHistoryEntity saved = captor.getAllValues().get(1);
        assertEquals(120, saved.getPromptTokens());
        assertEquals(80, saved.getCompletionTokens());
        assertEquals(200, saved.getTotalTokens());
        assertEquals(0.0042, saved.getEstimatedCost());
        assertEquals("USD", saved.getCostCurrency());
        assertEquals(1, saved.getQuotaConsumedAmount());
    }

    @Test
    void createDraft_whenProviderIncludesExcludedFood_rejectsWithoutQuotaCharge() {
        prepareUserAndGoal();
        when(provider.provider()).thenReturn(AiProvider.LOG);
        AiNutritionPlanDraftResponseDto response = validResponse();
        response.getDays().get(0).getMeals().get(0).getItems().get(0)
                .setDisplayName("Peanuts with oats");
        when(provider.createNutritionPlanDraft(any())).thenReturn(response);
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(
                any(), any(), any())).thenReturn(Optional.empty());
        when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(AiProviderException.class, () -> service.createDraft(
                "user@example.com", "nutrition-key-excluded-food", request()));

        verify(subscriptionService, never()).consumeAiQuota(anyString(), anyInt());
    }

    @Test
    void createDraft_whenPersistentAllergenConflicts_rejectsWithoutQuotaCharge() {
        prepareUserAndGoal();
        UserNutritionPreferenceDto preference = new UserNutritionPreferenceDto();
        preference.setAllergens(Set.of(RecipeAllergen.MILK));
        preference.setExcludedFoods(List.of("pork"));
        preference.setDietaryPreferences(List.of("high fiber"));
        when(nutritionPreferenceService.get("user@example.com")).thenReturn(preference);
        when(provider.provider()).thenReturn(AiProvider.LOG);
        when(provider.createNutritionPlanDraft(any())).thenReturn(validResponse());
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(
                any(), any(), any())).thenReturn(Optional.empty());
        when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(AiProviderException.class, () -> service.createDraft(
                "user@example.com", "nutrition-key-allergen-profile", request()));

        verify(subscriptionService, never()).consumeAiQuota(anyString(), anyInt());
        ArgumentCaptor<AiNutritionPlanDraftRequestDto> requestCaptor =
                ArgumentCaptor.forClass(AiNutritionPlanDraftRequestDto.class);
        verify(provider).createNutritionPlanDraft(requestCaptor.capture());
        assertTrue(requestCaptor.getValue().getTrustedAllergens().contains("MILK"));
        assertTrue(requestCaptor.getValue().getExcludedFoods().contains("pork"));
        assertTrue(requestCaptor.getValue().getDietaryPreferences().contains("high fiber"));
    }

    @Test
    void createDraft_whenProviderOutputIsInconsistent_doesNotConsumeQuota() {
        prepareUserAndGoal();
        when(provider.provider()).thenReturn(AiProvider.LOG);
        AiNutritionPlanDraftResponseDto invalid = validResponse();
        invalid.getDays().get(0).getTotalNutrition().setCalories(900.0);
        when(provider.createNutritionPlanDraft(any())).thenReturn(invalid);
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(
                any(), any(), any())).thenReturn(Optional.empty());
        when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(AiProviderException.class, () -> service.createDraft(
                "user@example.com", "nutrition-key-002", request()));

        verify(subscriptionService, never()).consumeAiQuota(anyString(), anyInt());
        verify(subscriptionService, never()).refundConsumedAiQuota(any(), anyInt());
        ArgumentCaptor<AiRequestHistoryEntity> captor =
                ArgumentCaptor.forClass(AiRequestHistoryEntity.class);
        verify(historyRepository, times(2)).save(captor.capture());
        assertEquals(AiRequestStatus.FAILED, captor.getAllValues().get(1).getStatus());
        assertFalse(captor.getAllValues().get(1).getQuotaConsumed());
    }

    @Test
    void createDraft_whenMacrosMissTrustedTargets_returnsDraftWithReviewWarning() {
        prepareUserAndGoal();
        when(provider.provider()).thenReturn(AiProvider.LOG);
        AiNutritionPlanDraftResponseDto macroMiss = validResponse();
        macroMiss.getDays().get(0).getMeals().forEach(meal -> {
            meal.getItems().get(0).getNutrition().setProtein(25.0);
            meal.getTotalNutrition().setProtein(25.0);
        });
        macroMiss.getDays().get(0).getTotalNutrition().setProtein(50.0);
        macroMiss.setPromptTokens(100);
        macroMiss.setCompletionTokens(200);
        macroMiss.setTotalTokens(300);
        macroMiss.setEstimatedCost(0.01);
        macroMiss.setCostCurrency("USD");
        when(provider.createNutritionPlanDraft(any())).thenReturn(macroMiss);
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(
                any(), any(), any())).thenReturn(Optional.empty());
        when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SubscriptionDto quota = new SubscriptionDto();
        quota.setAiRemainingThisPeriod(8);
        when(subscriptionService.consumeAiQuota("user@example.com", 1)).thenReturn(quota);

        AiNutritionPlanDraftResponseDto result = service.createDraft(
                "user@example.com", "nutrition-key-macro", request());

        assertTrue(result.getWarnings().stream().anyMatch(value ->
                value.contains("protein is slightly outside")));
        verify(provider, times(1)).createNutritionPlanDraft(any());
        verify(subscriptionService, times(1)).consumeAiQuota("user@example.com", 1);
        ArgumentCaptor<AiRequestHistoryEntity> historyCaptor =
                ArgumentCaptor.forClass(AiRequestHistoryEntity.class);
        verify(historyRepository, times(2)).save(historyCaptor.capture());
        AiRequestHistoryEntity saved = historyCaptor.getAllValues().get(1);
        assertEquals(AiRequestStatus.DRAFT_CREATED, saved.getStatus());
        assertEquals(100, saved.getPromptTokens());
        assertEquals(200, saved.getCompletionTokens());
        assertEquals(300, saved.getTotalTokens());
        assertEquals(0.01, saved.getEstimatedCost(), 0.000001);
    }

    @Test
    void createDraft_whenMacroDeviationIsSmall_returnsDraftWithReviewWarning() {
        prepareUserAndGoal();
        when(provider.provider()).thenReturn(AiProvider.LOG);
        AiNutritionPlanDraftResponseDto nearTarget = validResponse();
        nearTarget.getDays().get(0).getMeals().forEach(meal -> {
            meal.getItems().get(0).getNutrition().setProtein(75.0);
            meal.getTotalNutrition().setProtein(75.0);
        });
        nearTarget.getDays().get(0).getTotalNutrition().setProtein(150.0);
        when(provider.createNutritionPlanDraft(any())).thenReturn(nearTarget);
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(
                any(), any(), any())).thenReturn(Optional.empty());
        when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SubscriptionDto quota = new SubscriptionDto();
        quota.setAiRemainingThisPeriod(8);
        when(subscriptionService.consumeAiQuota("user@example.com", 1)).thenReturn(quota);

        AiNutritionPlanDraftResponseDto result = service.createDraft(
                "user@example.com", "nutrition-key-near-target", request());

        assertTrue(result.getWarnings().stream().anyMatch(value ->
                value.contains("protein is slightly outside")));
        verify(provider, times(1)).createNutritionPlanDraft(any());
        verify(subscriptionService, times(1)).consumeAiQuota("user@example.com", 1);
    }

    @Test
    void createDraft_whenTrustedCalorieValidationFails_repairsOnceAndConsumesQuotaOnce() {
        prepareUserAndGoal();
        when(provider.provider()).thenReturn(AiProvider.LOG);
        AiNutritionPlanDraftResponseDto invalid = validResponse();
        invalid.getDays().get(0).getMeals().forEach(meal -> {
            meal.getItems().get(0).getNutrition().setCalories(1300.0);
            meal.getTotalNutrition().setCalories(1300.0);
        });
        invalid.getDays().get(0).getTotalNutrition().setCalories(2600.0);
        invalid.setPromptTokens(100);
        invalid.setCompletionTokens(200);
        invalid.setTotalTokens(300);
        invalid.setEstimatedCost(0.01);
        invalid.setCostCurrency("USD");
        AiNutritionPlanDraftResponseDto repaired = validResponse();
        repaired.setPromptTokens(110);
        repaired.setCompletionTokens(210);
        repaired.setTotalTokens(320);
        repaired.setEstimatedCost(0.02);
        repaired.setCostCurrency("USD");
        when(provider.createNutritionPlanDraft(any())).thenReturn(invalid, repaired);
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(
                any(), any(), any())).thenReturn(Optional.empty());
        when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SubscriptionDto quota = new SubscriptionDto();
        quota.setAiRemainingThisPeriod(8);
        when(subscriptionService.consumeAiQuota("user@example.com", 1)).thenReturn(quota);

        AiNutritionPlanDraftResponseDto result = service.createDraft(
                "user@example.com", "nutrition-key-repair", request());

        assertEquals(8, result.getAiRemainingThisPeriod());
        assertEquals(210, result.getPromptTokens());
        assertEquals(410, result.getCompletionTokens());
        assertEquals(620, result.getTotalTokens());
        assertEquals(0.03, result.getEstimatedCost(), 0.000001);
        verify(provider, times(2)).createNutritionPlanDraft(any());
        verify(subscriptionService, times(1)).consumeAiQuota("user@example.com", 1);
        verify(subscriptionService, never()).refundConsumedAiQuota(any(), anyInt());

        ArgumentCaptor<AiRequestHistoryEntity> historyCaptor =
                ArgumentCaptor.forClass(AiRequestHistoryEntity.class);
        verify(historyRepository, times(2)).save(historyCaptor.capture());
        AiRequestHistoryEntity saved = historyCaptor.getAllValues().get(1);
        assertTrue(saved.getCorrectionSummary().contains("Daily calories"));
        assertTrue(saved.getCorrectionSummary().contains("actual=2600.0"));
        assertTrue(saved.getCorrectionSummary().contains("target=2000.0"));
        assertTrue(saved.getCorrectionSummary().contains("allowedRange=1500.0..2500.0"));
        assertTrue(saved.getQuotaConsumed());
        assertEquals(1, saved.getQuotaConsumedAmount());
    }

    @Test
    void createDraft_withExistingIdempotencyKey_returnsStoredDraft() throws Exception {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setId(55L);
        history.setStatus(AiRequestStatus.DRAFT_CREATED);
        history.setOutputPayload(objectMapper.writeValueAsString(validResponse()));
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(
                user, AiRequestType.AI_NUTRITION_PLAN, "nutrition-key-003"))
                .thenReturn(Optional.of(history));

        AiNutritionPlanDraftResponseDto result = service.createDraft(
                "user@example.com", "nutrition-key-003", request());

        assertEquals(55L, result.getRequestId());
        verifyNoInteractions(goalRepository);
        verify(provider, never()).createNutritionPlanDraft(any());
        verify(subscriptionService, never()).consumeAiQuota(anyString(), anyInt());
    }

    @Test
    void createDraft_whenConcurrentKeyWinsDatabaseRace_doesNotCallProviderOrConsumeQuota() {
        prepareUserAndGoal();
        AiRequestHistoryEntity processing = new AiRequestHistoryEntity();
        processing.setStatus(AiRequestStatus.PROCESSING);
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(
                user, AiRequestType.AI_NUTRITION_PLAN, "nutrition-key-race"))
                .thenReturn(Optional.empty(), Optional.of(processing));
        when(historyRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        RequestConflictException error = assertThrows(RequestConflictException.class,
                () -> service.createDraft("user@example.com", "nutrition-key-race", request()));

        assertTrue(error.getMessage().contains("already processing"));
        verify(provider, never()).createNutritionPlanDraft(any());
        verify(subscriptionService, never()).consumeAiQuota(anyString(), anyInt());
    }

    @Test
    void createDraft_workoutAlignedUsesOwnedScheduleWithoutAddingExerciseCalories() throws Exception {
        prepareUserAndGoal();
        AiNutritionPlanDraftRequestDto alignedRequest = request();
        alignedRequest.setGenerationMode(NutritionPlanGenerationMode.WORKOUT_ALIGNED);
        alignedRequest.setWorkoutPlanId(10L);
        WorkoutPlanEntity workoutPlan = scheduledWorkoutPlan(10L, alignedRequest.getStartDate());
        when(workoutPlanRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(workoutPlan));
        when(provider.provider()).thenReturn(AiProvider.LOG);
        AiNutritionPlanDraftResponseDto alignedResponse = validResponse();
        alignedResponse.getDays().get(0).getMeals().get(0).setSuggestedTime(LocalTime.of(16, 0));
        alignedResponse.getDays().get(0).getMeals().get(0).getItems().get(0)
                .setWorkoutRelation(MealPlanWorkoutRelation.PRE_WORKOUT);
        when(provider.createNutritionPlanDraft(any())).thenReturn(alignedResponse);
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(
                any(), any(), any())).thenReturn(Optional.empty());
        when(historyRepository.save(any())).thenAnswer(invocation -> {
            AiRequestHistoryEntity history = invocation.getArgument(0);
            history.setId(42L);
            return history;
        });
        SubscriptionDto quota = new SubscriptionDto();
        quota.setAiRemainingThisPeriod(8);
        when(subscriptionService.consumeAiQuota("user@example.com", 2)).thenReturn(quota);

        AiNutritionPlanDraftResponseDto result = service.createDraft(
                "user@example.com", "nutrition-key-004", alignedRequest);

        assertEquals(NutritionPlanGenerationMode.WORKOUT_ALIGNED, result.getGenerationMode());
        assertEquals(10L, result.getWorkoutPlanId());
        assertEquals("workout_schedule_v1", result.getWorkoutScheduleVersion());
        assertEquals(NutritionPlanDayType.TRAINING, result.getDays().get(0).getDayType());
        assertEquals(MealPlanWorkoutRelation.PRE_WORKOUT,
                result.getDays().get(0).getMeals().get(0).getItems().get(0).getWorkoutRelation());
        assertEquals(2000.0, result.getDailyTarget().getCalories());
        ArgumentCaptor<AiNutritionPlanDraftRequestDto> requestCaptor =
                ArgumentCaptor.forClass(AiNutritionPlanDraftRequestDto.class);
        verify(provider).createNutritionPlanDraft(requestCaptor.capture());
        assertEquals(1, requestCaptor.getValue().getTrustedWorkoutContext().getSessions().size());
        WorkoutNutritionSessionContextDto session = requestCaptor.getValue()
                .getTrustedWorkoutContext().getSessions().get(0);
        assertEquals(1, session.getExerciseCount());
        assertEquals(3, session.getTotalWorkingSets());
        assertEquals(2000.0, requestCaptor.getValue().getTrustedDailyTarget().getCalories());
        verify(subscriptionService).consumeAiQuota("user@example.com", 2);
    }

    @Test
    void createDraft_workoutAlignedRejectsUnscheduledPlanBeforeProviderOrQuota() {
        prepareUserAndGoal();
        AiNutritionPlanDraftRequestDto alignedRequest = request();
        alignedRequest.setGenerationMode(NutritionPlanGenerationMode.WORKOUT_ALIGNED);
        alignedRequest.setWorkoutPlanId(10L);
        WorkoutPlanEntity workoutPlan = new WorkoutPlanEntity();
        workoutPlan.setId(10L);
        workoutPlan.setUser(user);
        workoutPlan.setActive(true);
        workoutPlan.setStatus(WorkoutPlanStatus.ACTIVE);
        when(workoutPlanRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(workoutPlan));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createDraft(
                        "user@example.com", "nutrition-key-005", alignedRequest));

        assertEquals(
                "Save the workout plan schedule before generating a workout-aligned nutrition plan.",
                error.getMessage());
        verify(provider, never()).createNutritionPlanDraft(any());
        verify(subscriptionService, never()).consumeAiQuota(anyString(), anyInt());
    }

    @Test
    void createDraft_workoutAlignedRejectsArchivedPlanBeforeProviderOrQuota() throws Exception {
        prepareUserAndGoal();
        AiNutritionPlanDraftRequestDto alignedRequest = request();
        alignedRequest.setGenerationMode(NutritionPlanGenerationMode.WORKOUT_ALIGNED);
        alignedRequest.setWorkoutPlanId(10L);
        WorkoutPlanEntity workoutPlan = scheduledWorkoutPlan(10L, alignedRequest.getStartDate());
        workoutPlan.setActive(false);
        workoutPlan.setStatus(WorkoutPlanStatus.ARCHIVED);
        when(workoutPlanRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(workoutPlan));

        assertThrows(IllegalArgumentException.class, () -> service.createDraft(
                "user@example.com", "nutrition-key-006", alignedRequest));

        verify(provider, never()).createNutritionPlanDraft(any());
        verify(subscriptionService, never()).consumeAiQuota(anyString(), anyInt());
    }
    @Test
    void createDraft_workoutAlignedRejectsMissingOrForeignPlanBeforeProviderOrQuota() {
        prepareUserAndGoal();
        AiNutritionPlanDraftRequestDto alignedRequest = request();
        alignedRequest.setGenerationMode(NutritionPlanGenerationMode.WORKOUT_ALIGNED);
        alignedRequest.setWorkoutPlanId(999L);
        when(workoutPlanRepository.findByIdAndUser(999L, user)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.createDraft(
                "user@example.com", "nutrition-key-foreign-workout", alignedRequest));

        verify(provider, never()).createNutritionPlanDraft(any());
        verify(subscriptionService, never()).consumeAiQuota(anyString(), anyInt());
    }

    @Test
    void createDraft_normalizesInvalidPreWorkoutTimingWithoutRetry() throws Exception {
        prepareUserAndGoal();
        AiNutritionPlanDraftRequestDto alignedRequest = request();
        alignedRequest.setGenerationMode(NutritionPlanGenerationMode.WORKOUT_ALIGNED);
        alignedRequest.setWorkoutPlanId(10L);
        when(workoutPlanRepository.findByIdAndUser(10L, user))
                .thenReturn(Optional.of(scheduledWorkoutPlan(10L, alignedRequest.getStartDate())));
        AiNutritionPlanDraftResponseDto response = validResponse();
        AiNutritionPlanMealDto meal = response.getDays().get(0).getMeals().get(0);
        meal.setSuggestedTime(LocalTime.of(10, 0));
        meal.getItems().get(0).setWorkoutRelation(MealPlanWorkoutRelation.PRE_WORKOUT);
        when(provider.provider()).thenReturn(AiProvider.LOG);
        when(provider.createNutritionPlanDraft(any())).thenReturn(response);
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(
                any(), any(), any())).thenReturn(Optional.empty());
        when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SubscriptionDto quota = new SubscriptionDto();
        quota.setAiRemainingThisPeriod(8);
        when(subscriptionService.consumeAiQuota("user@example.com", 2)).thenReturn(quota);

        AiNutritionPlanDraftResponseDto result = service.createDraft(
                "user@example.com", "nutrition-key-007", alignedRequest);

        AiNutritionPlanItemDto item = result.getDays().get(0).getMeals().get(0).getItems().get(0);
        assertEquals(MealPlanWorkoutRelation.NONE, item.getWorkoutRelation());
        assertTrue(item.getWarnings().stream().anyMatch(value ->
                value.contains("Workout timing was not applied")));
        verify(provider, times(1)).createNutritionPlanDraft(any());
        verify(subscriptionService).consumeAiQuota("user@example.com", 2);
    }
    @Test
    void confirmDraft_rejectsWorkoutScheduleChangedAfterGeneration() throws Exception {
        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        LocalDate planDate = LocalDate.now().plusDays(1);
        LocalDateTime originalScheduleTime = LocalDateTime.of(
                planDate.minusDays(1), LocalTime.of(12, 0));
        AiNutritionPlanDraftResponseDto original = validResponse();
        original.setGenerationMode(NutritionPlanGenerationMode.WORKOUT_ALIGNED);
        original.setWorkoutPlanId(10L);
        original.setWorkoutScheduleVersion("workout_schedule_v1");
        original.setWorkoutScheduleUpdatedAt(originalScheduleTime);
        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setId(79L);
        history.setUser(user);
        history.setRequestType(AiRequestType.AI_NUTRITION_PLAN);
        history.setStatus(AiRequestStatus.DRAFT_CREATED);
        history.setOutputPayload(objectMapper.writeValueAsString(original));
        when(historyRepository.findByIdAndUser(79L, user)).thenReturn(Optional.of(history));
        WorkoutPlanEntity changedPlan = scheduledWorkoutPlan(10L, planDate);
        changedPlan.setScheduleUpdatedAt(originalScheduleTime.plusMinutes(5));
        when(workoutPlanRepository.findByIdAndUser(10L, user))
                .thenReturn(Optional.of(changedPlan));
        AiNutritionPlanConfirmRequestDto confirmRequest = new AiNutritionPlanConfirmRequestDto();
        confirmRequest.setDraft(original);

        assertThrows(RequestConflictException.class,
                () -> service.confirmDraft("user@example.com", 79L, confirmRequest));

        verify(mealPlanService, never()).createMealPlan(any(), any());
    }
    @Test
    void confirmDraft_createsSnapshotMealPlanWithoutDiaryWrite() throws Exception {
        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setId(77L);
        history.setUser(user);
        history.setRequestType(AiRequestType.AI_NUTRITION_PLAN);
        history.setStatus(AiRequestStatus.DRAFT_CREATED);
        history.setPromptVersion("nutrition-v1");
        history.setOutputPayload(objectMapper.writeValueAsString(validResponse()));
        when(historyRepository.findByIdAndUser(77L, user)).thenReturn(Optional.of(history));

        MealPlanDto created = new MealPlanDto();
        created.setId(90L);
        when(mealPlanService.createMealPlan(eq("user@example.com"), any())).thenReturn(created);
        MealPlanEntity plan = new MealPlanEntity();
        plan.setId(90L);
        plan.setUser(user);
        plan.setItems(new java.util.ArrayList<>(List.of(new MealPlanItemEntity())));
        MealPlanEntity previousActive = new MealPlanEntity();
        previousActive.setId(89L);
        previousActive.setUser(user);
        previousActive.setStatus(MealPlanStatus.ACTIVE);
        when(mealPlanRepository.findByUserAndStatus(user, MealPlanStatus.ACTIVE))
                .thenReturn(List.of(previousActive));
        when(mealPlanRepository.findByIdAndUser(90L, user)).thenReturn(Optional.of(plan));
        when(mealPlanRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mealPlanService.getMealPlan("user@example.com", 90L)).thenReturn(created);

        AiNutritionPlanConfirmRequestDto request = new AiNutritionPlanConfirmRequestDto();
        request.setDraft(validResponse());
        MealPlanDto result = service.confirmDraft("user@example.com", 77L, request);

        assertEquals(90L, result.getId());
        assertEquals(AiRequestStatus.CONFIRMED, history.getStatus());
        assertEquals(history, plan.getSourceAiRequest());
        assertEquals(MealPlanStatus.ACTIVE, plan.getStatus());
        assertEquals(MealPlanStatus.DRAFT, previousActive.getStatus());
        assertEquals(history, plan.getItems().get(0).getSourceAiRequest());
        ArgumentCaptor<MealPlanRequestDto> requestCaptor =
                ArgumentCaptor.forClass(MealPlanRequestDto.class);
        verify(mealPlanService).createMealPlan(eq("user@example.com"), requestCaptor.capture());
        assertTrue(requestCaptor.getValue().getItems().stream()
                .allMatch(item -> item.getItemType() == MealPlanItemType.AI_SNAPSHOT));
        assertTrue(history.getConfirmationPayload().contains("90"));
        verify(subscriptionService, never()).refundConsumedAiQuota(any(), anyInt());
    }

    @Test
    void rejectDraft_storesFeedbackWithoutAutomaticRefund() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setId(78L);
        history.setUser(user);
        history.setRequestType(AiRequestType.AI_NUTRITION_PLAN);
        history.setStatus(AiRequestStatus.DRAFT_CREATED);
        when(historyRepository.findByIdAndUser(78L, user)).thenReturn(Optional.of(history));
        AiMealDraftRejectRequestDto request = new AiMealDraftRejectRequestDto();
        request.setReason(AiDraftRejectReason.IRRELEVANT_RESULT);
        request.setFeedback("  Meals do not fit my schedule.  ");

        service.rejectDraft("user@example.com", 78L, request);

        assertEquals(AiRequestStatus.REJECTED, history.getStatus());
        assertEquals(AiDraftRejectReason.IRRELEVANT_RESULT, history.getRejectionReason());
        assertEquals("Meals do not fit my schedule.", history.getRejectionFeedback());
        verify(subscriptionService, never()).refundConsumedAiQuota(any(), anyInt());
    }
    private void prepareUserAndGoal() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(goalRepository.findByUser(user)).thenReturn(Optional.of(goal()));
    }

    private UserGoalEntity goal() {
        UserGoalEntity goal = new UserGoalEntity();
        goal.setDailyCalorieGoal(2000);
        goal.setDailyProteinGoal(120.0);
        goal.setDailyCarbGoal(230.0);
        goal.setDailyFatGoal(65.0);
        return goal;
    }

    private WorkoutPlanEntity scheduledWorkoutPlan(Long id, LocalDate date) throws Exception {
        AiWorkoutPlanDayDto day = new AiWorkoutPlanDayDto();
        day.setDayLabel("Day 1");
        day.setFocus("Strength");
        day.setEstimatedDurationMinutes(45);
        day.setScheduledDate(date);
        day.setScheduledStartTime(LocalTime.of(18, 0));
        day.setSessionIntensity(WorkoutSessionIntensity.MODERATE);
        AiWorkoutPlanExerciseDto exercise = new AiWorkoutPlanExerciseDto();
        exercise.setName("Squat");
        exercise.setSetCount(3);
        exercise.setReps(8);
        day.setExercises(List.of(exercise));
        AiWorkoutPlanDraftResponseDto payload = new AiWorkoutPlanDraftResponseDto();
        payload.setDays(List.of(day));

        WorkoutPlanEntity plan = new WorkoutPlanEntity();
        plan.setId(id);
        plan.setUser(user);
        plan.setName("Active plan");
        plan.setStatus(WorkoutPlanStatus.ACTIVE);
        plan.setActive(true);
        plan.setScheduleVersion("workout_schedule_v1");
        plan.setScheduleUpdatedAt(LocalDateTime.of(date.minusDays(1), LocalTime.NOON));
        plan.setPlanPayload(objectMapper.writeValueAsString(payload));
        return plan;
    }
    private AiNutritionPlanDraftRequestDto request() {
        AiNutritionPlanDraftRequestDto request = new AiNutritionPlanDraftRequestDto();
        request.setGenerationMode(NutritionPlanGenerationMode.GENERAL);
        request.setStartDate(LocalDate.now().plusDays(1));
        request.setDayCount(1);
        request.setMealsPerDay(2);
        request.setExcludedFoods(List.of("peanuts"));
        request.setDietaryPreferences(List.of("high protein"));
        return request;
    }

    private AiNutritionPlanDraftResponseDto validResponse() {
        AiNutritionPlanDraftResponseDto response = new AiNutritionPlanDraftResponseDto();
        response.setGenerationMode(NutritionPlanGenerationMode.GENERAL);
        response.setStartDate(LocalDate.now().plusDays(1));
        response.setEndDate(LocalDate.now().plusDays(1));
        response.setDailyTarget(nutrition(2000.0, 120.0, 230.0, 65.0));
        response.setName("balanced day");
        response.setSummary("A balanced day built around the current calorie and macro targets.");
        response.setProfessionalSummary("Protein and energy are distributed across two practical meals.");
        response.setConfidence(0.85);
        response.setQualityScore(85);
        response.setEstimatedUncertainty("LOW");
        response.setAssumptions(List.of("Typical ingredient values are used."));
        response.setWarnings(List.of("Review allergens before confirming."));
        response.setNextBestActions(List.of("Review portions."));

        AiNutritionPlanDayDto day = new AiNutritionPlanDayDto();
        day.setDate(LocalDate.now().plusDays(1));
        day.setMeals(List.of(meal("BREAKFAST", "oats with yogurt"),
                meal("DINNER", "chicken with rice")));
        day.setTotalNutrition(nutrition(2000.0, 120.0, 230.0, 65.0));
        response.setDays(List.of(day));
        return response;
    }

    private AiNutritionPlanMealDto meal(String type, String name) {
        AiNutritionPlanItemDto item = new AiNutritionPlanItemDto();
        item.setDisplayName(name);
        item.setQuantity(1.0);
        item.setUnit(FoodPortionUnit.SERVING);
        item.setNutrition(nutrition(1000.0, 60.0, 115.0, 32.5));
        item.setWorkoutRelation(MealPlanWorkoutRelation.NONE);
        AiNutritionPlanMealDto meal = new AiNutritionPlanMealDto();
        meal.setMealType(type);
        meal.setItems(List.of(item));
        meal.setTotalNutrition(nutrition(1000.0, 60.0, 115.0, 32.5));
        return meal;
    }

    private MealPlanNutritionSnapshotDto nutrition(
            double calories, double protein, double carbs, double fat) {
        MealPlanNutritionSnapshotDto value = new MealPlanNutritionSnapshotDto();
        value.setCalories(calories);
        value.setProtein(protein);
        value.setCarbs(carbs);
        value.setFat(fat);
        value.setFiber(0.0);
        return value;
    }
}
