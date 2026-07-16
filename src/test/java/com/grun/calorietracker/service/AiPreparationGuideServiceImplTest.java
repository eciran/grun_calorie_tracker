package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.AiProviderException;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.impl.AiPreparationGuideServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiPreparationGuideServiceImplTest {
    @Mock AiMealDraftProviderClient provider;
    @Mock AiRequestHistoryRepository historyRepository;
    @Mock UserRepository userRepository;
    @Mock MealPlanItemRepository itemRepository;
    @Mock MealPlanPreparationGuideRepository guideRepository;
    @Mock SubscriptionService subscriptionService;
    @Mock AiProviderConfigurationValidator configurationValidator;

    private AiPreparationGuideServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private UserEntity user;
    private MealPlanItemEntity item;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider(AiProvider.LOG);
        properties.setModel("test-model");
        properties.setPromptVersion("prep-v1");
        service = new AiPreparationGuideServiceImpl(properties, List.of(provider), historyRepository,
                userRepository, itemRepository, guideRepository, subscriptionService,
                objectMapper, configurationValidator);
        user = new UserEntity();
        user.setId(7L);
        user.setEmail("user@grun.test");
        MealPlanEntity plan = new MealPlanEntity();
        plan.setId(10L);
        plan.setUser(user);
        item = new MealPlanItemEntity();
        item.setId(20L);
        item.setMealPlan(plan);
        item.setPlanDate(LocalDate.now().plusDays(1));
        item.setMealType("DINNER");
        item.setSnapshotName("grilled chicken");
        item.setPortionSize(150.0);
        item.setPortionUnit(FoodPortionUnit.GRAM);
        item.setSnapshotCalories(240.0);
        item.setSnapshotProtein(45.0);
        item.setSnapshotCarbs(0.0);
        item.setSnapshotFat(6.0);
        when(userRepository.findByEmail("user@grun.test")).thenReturn(Optional.of(user));
        lenient().when(itemRepository.findOwnedForUpdate(20L, 10L, user)).thenReturn(Optional.of(item));
        lenient().when(itemRepository.findOwned(20L, 10L, user)).thenReturn(Optional.of(item));
        lenient().when(provider.provider()).thenReturn(AiProvider.LOG);
    }

    @Test
    void generatePreservesSnapshotAndChargesConfiguredCostOnce() {
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(guideRepository.findTopByMealPlanItemOrderByVersionDesc(item)).thenReturn(Optional.empty());
        when(subscriptionService.resolveAiCreditCost("user@grun.test", SubscriptionFeature.AI_MEAL_PREPARATION_GUIDE))
                .thenReturn(3);
        when(provider.createPreparationGuide(any())).thenReturn(providerResponse(999.0));
        SubscriptionDto quota = new SubscriptionDto();
        quota.setAiRemainingThisPeriod(17);
        when(subscriptionService.consumeAiQuota("user@grun.test", 3)).thenReturn(quota);
        when(historyRepository.save(any())).thenAnswer(invocation -> {
            AiRequestHistoryEntity value = invocation.getArgument(0);
            if (value.getId() == null) value.setId(30L);
            return value;
        });
        when(guideRepository.save(any())).thenAnswer(invocation -> {
            MealPlanPreparationGuideEntity value = invocation.getArgument(0);
            value.setId(40L);
            return value;
        });

        AiPreparationGuideResponseDto result = service.generate(
                "user@grun.test", 10L, 20L, "prep-key-001", new AiPreparationGuideGenerateRequestDto());

        assertThat(result.getGuideId()).isEqualTo(40L);
        assertThat(result.getItemName()).isEqualTo("Grilled Chicken");
        assertThat(result.getPlannedNutrition().getCalories()).isEqualTo(240.0);
        assertThat(result.getCreditCost()).isEqualTo(3);
        assertThat(result.getAiRemainingThisPeriod()).isEqualTo(17);
        verify(subscriptionService).consumeAiQuota("user@grun.test", 3);
        verify(provider, times(1)).createPreparationGuide(any());
    }

    @Test
    void reopenUsesStoredSnapshotWithoutProviderOrQuota() throws Exception {
        AiRequestHistoryEntity history = history(AiRequestStatus.DRAFT_CREATED);
        MealPlanPreparationGuideEntity guide = guide(history, 1, providerResponse(240.0));
        when(guideRepository.findTopByMealPlanItemOrderByVersionDesc(item)).thenReturn(Optional.of(guide));

        AiPreparationGuideResponseDto result = service.reopen("user@grun.test", 10L, 20L);

        assertThat(result.getGuideId()).isEqualTo(40L);
        verifyNoInteractions(subscriptionService);
        verify(provider, never()).createPreparationGuide(any());
    }

    @Test
    void invalidProviderResultDoesNotConsumeQuota() {
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(guideRepository.findTopByMealPlanItemOrderByVersionDesc(item)).thenReturn(Optional.empty());
        when(subscriptionService.resolveAiCreditCost(anyString(), any())).thenReturn(2);
        when(provider.createPreparationGuide(any())).thenReturn(new AiPreparationGuideResponseDto());
        when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.generate("user@grun.test", 10L, 20L,
                "prep-key-002", null)).isInstanceOf(AiProviderException.class)
                .hasMessage("AI preparation guide could not produce a usable result.");
        verify(subscriptionService, never()).consumeAiQuota(anyString(), anyInt());
    }

    @Test
    void regenerateCreatesNextVersionAndChargesAgain() throws Exception {
        AiRequestHistoryEntity oldHistory = history(AiRequestStatus.DRAFT_CREATED);
        MealPlanPreparationGuideEntity oldGuide = guide(oldHistory, 1, providerResponse(240.0));
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(guideRepository.findTopByMealPlanItemOrderByVersionDesc(item)).thenReturn(Optional.of(oldGuide));
        when(subscriptionService.resolveAiCreditCost(anyString(), any())).thenReturn(1);
        when(provider.createPreparationGuide(any())).thenReturn(providerResponse(240.0));
        SubscriptionDto quota = new SubscriptionDto(); quota.setAiRemainingThisPeriod(9);
        when(subscriptionService.consumeAiQuota(anyString(), eq(1))).thenReturn(quota);
        when(historyRepository.save(any())).thenAnswer(invocation -> {
            AiRequestHistoryEntity value = invocation.getArgument(0); value.setId(31L); return value;
        });
        when(guideRepository.save(any())).thenAnswer(invocation -> {
            MealPlanPreparationGuideEntity value = invocation.getArgument(0); value.setId(41L); return value;
        });

        AiPreparationGuideResponseDto result = service.regenerate(
                "user@grun.test", 10L, 20L, "prep-key-003", null);

        assertThat(result.getVersion()).isEqualTo(2);
        verify(subscriptionService).consumeAiQuota("user@grun.test", 1);
    }

    @Test
    void duplicateIdempotencyKeyReturnsStoredGuideWithoutSecondCharge() throws Exception {
        AiRequestHistoryEntity history = history(AiRequestStatus.DRAFT_CREATED);
        MealPlanPreparationGuideEntity guide = guide(history, 1, providerResponse(240.0));
        when(historyRepository.findByUserAndRequestTypeAndIdempotencyKey(
                user, AiRequestType.AI_MEAL_PREPARATION_GUIDE, "prep-key-004"))
                .thenReturn(Optional.of(history));
        when(guideRepository.findBySourceAiRequest(history)).thenReturn(Optional.of(guide));

        AiPreparationGuideResponseDto result = service.generate(
                "user@grun.test", 10L, 20L, "prep-key-004", null);

        assertThat(result.getGuideId()).isEqualTo(40L);
        verify(subscriptionService, never()).consumeAiQuota(anyString(), anyInt());
        verify(provider, never()).createPreparationGuide(any());
    }

    @Test
    void rejectStoresFeedbackAndMarksGuideRejected() throws Exception {
        AiRequestHistoryEntity history = history(AiRequestStatus.DRAFT_CREATED);
        MealPlanPreparationGuideEntity guide = guide(history, 1, providerResponse(240.0));
        when(historyRepository.findByIdAndUser(30L, user)).thenReturn(Optional.of(history));
        when(guideRepository.findBySourceAiRequest(history)).thenReturn(Optional.of(guide));
        when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(guideRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AiMealDraftRejectRequestDto request = new AiMealDraftRejectRequestDto();
        request.setReason(AiDraftRejectReason.IRRELEVANT_RESULT);
        request.setFeedback("The preparation method does not fit this meal.");

        service.reject("user@grun.test", 10L, 20L, 30L, request);

        assertThat(history.getStatus()).isEqualTo(AiRequestStatus.REJECTED);
        assertThat(history.getRejectionFeedback()).contains("does not fit");
        assertThat(guide.getStatus()).isEqualTo(AiPreparationGuideStatus.REJECTED);
    }
    private AiPreparationGuideResponseDto providerResponse(double calories) {
        AiPreparationGuideResponseDto response = new AiPreparationGuideResponseDto();
        response.setPlannedNutrition(new MealPlanNutritionSnapshotDto(calories, 1.0, 1.0, 1.0,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        response.setPreparationMinutes(5);
        response.setCookingMinutes(15);
        AiPreparationGuideIngredientDto ingredient = new AiPreparationGuideIngredientDto();
        ingredient.setName("chicken breast"); ingredient.setQuantity(150.0);
        ingredient.setUnit(FoodPortionUnit.GRAM); ingredient.setOptional(false);
        ingredient.setChangesPlannedNutrition(false);
        response.setIngredients(List.of(ingredient));
        AiPreparationGuideStepDto step = new AiPreparationGuideStepDto();
        step.setInstruction("Cook the chicken thoroughly and verify it is safely done.");
        step.setStepNumber(9); step.setDurationMinutes(15);
        response.setSteps(List.of(step));
        response.setFoodSafetyNotes(List.of("Avoid cross contamination."));
        response.setStorageInstructions(List.of("Refrigerate promptly."));
        response.setQualityScore(85); response.setConfidence(0.85);
        response.setEstimatedUncertainty("LOW");
        return response;
    }

    private AiRequestHistoryEntity history(AiRequestStatus status) {
        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setId(30L); history.setUser(user); history.setRequestType(AiRequestType.AI_MEAL_PREPARATION_GUIDE);
        history.setStatus(status); history.setProvider(AiProvider.LOG); history.setModel("test-model");
        history.setPromptVersion("prep-v1"); return history;
    }

    private MealPlanPreparationGuideEntity guide(AiRequestHistoryEntity history, int version,
                                                   AiPreparationGuideResponseDto response) throws Exception {
        MealPlanPreparationGuideEntity guide = new MealPlanPreparationGuideEntity();
        guide.setId(40L); guide.setMealPlanItem(item); guide.setSourceAiRequest(history);
        guide.setVersion(version); guide.setSchemaVersion("ai_preparation_guide_v1");
        guide.setStatus(AiPreparationGuideStatus.ACTIVE); guide.setCreatedAt(java.time.LocalDateTime.now());
        guide.setGuidePayload(objectMapper.writeValueAsString(response)); return guide;
    }
}