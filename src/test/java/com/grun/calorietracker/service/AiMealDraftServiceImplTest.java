package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiMealDraftConfirmItemRequestDto;
import com.grun.calorietracker.dto.AiMealDraftConfirmRequestDto;
import com.grun.calorietracker.dto.AiMealDraftConfirmResponseDto;
import com.grun.calorietracker.dto.AiMealDraftItemDto;
import com.grun.calorietracker.dto.AiMealDraftRejectRequestDto;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiPhotoMealDraftRequestDto;
import com.grun.calorietracker.dto.AiVoiceFoodDraftRequestDto;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiDraftRejectReason;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.AiMealDraftServiceImpl;
import com.grun.calorietracker.service.impl.AiMealDraftResponseValidatorImpl;
import com.grun.calorietracker.service.impl.AiMealDraftSafetyServiceImpl;
import com.grun.calorietracker.service.impl.AiProviderConfigurationValidatorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiMealDraftServiceImplTest {

    private AiProperties properties;
    private AiMealDraftProviderClient providerClient;
    private AiRequestHistoryRepository historyRepository;
    private UserRepository userRepository;
    private FoodItemRepository foodItemRepository;
    private SubscriptionService subscriptionService;
    private FoodLogsService foodLogsService;
    private AiMealDraftServiceImpl service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider(AiProvider.LOG);
        properties.setModel("log-draft-v1");
        providerClient = mock(AiMealDraftProviderClient.class);
        historyRepository = mock(AiRequestHistoryRepository.class);
        userRepository = mock(UserRepository.class);
        foodItemRepository = mock(FoodItemRepository.class);
        subscriptionService = mock(SubscriptionService.class);
        foodLogsService = mock(FoodLogsService.class);
        service = new AiMealDraftServiceImpl(
                properties,
                List.of(providerClient),
                historyRepository,
                userRepository,
                foodItemRepository,
                subscriptionService,
                foodLogsService,
                new ObjectMapper().findAndRegisterModules(),
                new AiProviderConfigurationValidatorImpl(properties),
                new AiMealDraftResponseValidatorImpl(),
                new AiMealDraftSafetyServiceImpl(properties)
        );
        user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");
    }

    @Test
    void createVoiceFoodDraft_whenProviderDisabled_failsBeforeQuota() {
        properties.setEnabled(false);

        AiVoiceFoodDraftRequestDto request = request();

        assertThrows(IllegalArgumentException.class,
                () -> service.createVoiceFoodDraft("user@example.com", request));
        verifyNoInteractions(subscriptionService, historyRepository);
    }

    @Test
    void createVoiceFoodDraft_createsDraftConsumesQuotaAndStoresHistory() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(providerClient.provider()).thenReturn(AiProvider.LOG);
        when(providerClient.createVoiceFoodDraft(any())).thenReturn(providerResponse());
        when(foodItemRepository.findVisibleAiMatchCandidates(any(), org.mockito.Mockito.eq(user), any()))
                .thenReturn(List.of(verifiedFoodItem()));
        SubscriptionDto quota = new SubscriptionDto();
        quota.setAiRemainingThisPeriod(14);
        when(subscriptionService.consumeAiQuota("user@example.com")).thenReturn(quota);
        when(historyRepository.save(any(AiRequestHistoryEntity.class))).thenAnswer(invocation -> {
            AiRequestHistoryEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return entity;
        });

        AiMealDraftResponseDto result = service.createVoiceFoodDraft("user@example.com", request());

        assertEquals(10L, result.getRequestId());
        assertEquals(14, result.getAiRemainingThisPeriod());
        assertEquals(12L, result.getItems().get(0).getMatchedFoodItemId());
        assertEquals(false, result.getItems().get(0).getReviewRequired());
        assertEquals("VERIFIED_CATALOG_MATCH", result.getItems().get(0).getMatchReason());
        verify(subscriptionService).assertFeatureAccess("user@example.com", SubscriptionFeature.AI_WORKOUT_PLANNER);
        verify(subscriptionService).consumeAiQuota("user@example.com");

        ArgumentCaptor<AiRequestHistoryEntity> captor = ArgumentCaptor.forClass(AiRequestHistoryEntity.class);
        verify(historyRepository).save(captor.capture());
        assertEquals(AiRequestType.VOICE_FOOD_LOG, captor.getValue().getRequestType());
        assertEquals(AiProvider.LOG, captor.getValue().getProvider());
        assertEquals(AiRequestStatus.DRAFT_CREATED, captor.getValue().getStatus());
        assertEquals(true, captor.getValue().getQuotaConsumed());
        assertFalse(captor.getValue().getInputPayload().contains("I ate chicken and rice"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getInputPayload().contains("transcriptLength"));
        org.junit.jupiter.api.Assertions.assertNotNull(captor.getValue().getLatencyMs());
    }

    @Test
    void createVoiceFoodDraft_whenNoCatalogMatch_marksItemReviewRequired() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(providerClient.provider()).thenReturn(AiProvider.LOG);
        when(providerClient.createVoiceFoodDraft(any())).thenReturn(providerResponse());
        when(foodItemRepository.findVisibleAiMatchCandidates(any(), org.mockito.Mockito.eq(user), any()))
                .thenReturn(List.of());
        SubscriptionDto quota = new SubscriptionDto();
        quota.setAiRemainingThisPeriod(14);
        when(subscriptionService.consumeAiQuota("user@example.com")).thenReturn(quota);
        when(historyRepository.save(any(AiRequestHistoryEntity.class))).thenAnswer(invocation -> {
            AiRequestHistoryEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return entity;
        });

        AiMealDraftResponseDto result = service.createVoiceFoodDraft("user@example.com", request());

        assertEquals(null, result.getItems().get(0).getMatchedFoodItemId());
        assertEquals(true, result.getItems().get(0).getReviewRequired());
        assertEquals("NO_CATALOG_MATCH", result.getItems().get(0).getMatchReason());
    }

    @Test
    void createVoiceFoodDraft_whenProviderResponseInvalid_refundsQuotaAndStoresFailedHistory() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(providerClient.provider()).thenReturn(AiProvider.LOG);
        AiMealDraftResponseDto invalid = providerResponse();
        invalid.setItems(List.of());
        when(providerClient.createVoiceFoodDraft(any())).thenReturn(invalid);
        when(subscriptionService.consumeAiQuota("user@example.com")).thenReturn(new SubscriptionDto());
        when(historyRepository.save(any(AiRequestHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(IllegalArgumentException.class,
                () -> service.createVoiceFoodDraft("user@example.com", request()));

        verify(subscriptionService).assertFeatureAccess("user@example.com", SubscriptionFeature.AI_WORKOUT_PLANNER);
        verify(subscriptionService).consumeAiQuota("user@example.com");
        verify(subscriptionService).refundConsumedAiQuota(1L, 1);

        ArgumentCaptor<AiRequestHistoryEntity> captor = ArgumentCaptor.forClass(AiRequestHistoryEntity.class);
        verify(historyRepository).save(captor.capture());
        assertEquals(AiRequestStatus.FAILED, captor.getValue().getStatus());
        assertEquals(false, captor.getValue().getQuotaConsumed());
        org.junit.jupiter.api.Assertions.assertNotNull(captor.getValue().getLatencyMs());
    }

    @Test
    void createVoiceFoodDraft_whenQuotaUnavailable_doesNotCallProvider() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(subscriptionService.consumeAiQuota("user@example.com"))
                .thenThrow(new IllegalArgumentException("AI quota is not available for the current subscription."));

        assertThrows(IllegalArgumentException.class,
                () -> service.createVoiceFoodDraft("user@example.com", request()));

        verify(subscriptionService).assertFeatureAccess("user@example.com", SubscriptionFeature.AI_WORKOUT_PLANNER);
        verify(providerClient, org.mockito.Mockito.never()).createVoiceFoodDraft(any());
        verify(historyRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void createVoiceFoodDraft_whenSensitiveMedicalPrompt_doesNotCallProviderOrConsumeQuota() {
        AiVoiceFoodDraftRequestDto request = request();
        request.setTranscript("Can you diagnose my stomach pain and prescribe medicine?");

        assertThrows(IllegalArgumentException.class,
                () -> service.createVoiceFoodDraft("user@example.com", request));

        verifyNoInteractions(providerClient, subscriptionService, historyRepository);
    }

    @Test
    void createPhotoMealDraft_whenImageReferencePrefixNotAllowed_doesNotCallProviderOrConsumeQuota() {
        AiPhotoMealDraftRequestDto request = new AiPhotoMealDraftRequestDto();
        request.setImageReference("file:///C:/private/photo.jpg");
        request.setUserNote("Lunch plate");

        assertThrows(IllegalArgumentException.class,
                () -> service.createPhotoMealDraft("user@example.com", request));

        verifyNoInteractions(providerClient, subscriptionService, historyRepository);
    }

    @Test
    void confirmDraft_writesUserApprovedFoodLogsAndClosesDraft() throws com.fasterxml.jackson.core.JsonProcessingException {
        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setId(10L);
        history.setUser(user);
        history.setRequestType(AiRequestType.VOICE_FOOD_LOG);
        history.setProvider(AiProvider.LOG);
        history.setModel("log-draft-v1");
        history.setStatus(AiRequestStatus.DRAFT_CREATED);
        history.setQuotaConsumed(true);
        history.setCreatedAt(LocalDateTime.now());
        history.setOutputPayload(new ObjectMapper().findAndRegisterModules().writeValueAsString(providerResponse()));

        FoodLogsDto created = new FoodLogsDto();
        created.setId(99L);
        created.setFoodItemId(12L);
        created.setPortionSize(150.0);
        created.setMealType("LUNCH");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(historyRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(history));
        when(foodLogsService.addFoodLog(any(FoodLogsDto.class), org.mockito.Mockito.eq("user@example.com"))).thenReturn(created);
        when(historyRepository.save(any(AiRequestHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiMealDraftConfirmResponseDto result = service.confirmDraft("user@example.com", 10L, confirmRequest());

        assertEquals(AiRequestStatus.CONFIRMED, result.getStatus());
        assertEquals(1, result.getCreatedLogs().size());
        verify(foodLogsService).addFoodLog(any(FoodLogsDto.class), org.mockito.Mockito.eq("user@example.com"));

        ArgumentCaptor<AiRequestHistoryEntity> captor = ArgumentCaptor.forClass(AiRequestHistoryEntity.class);
        verify(historyRepository).save(captor.capture());
        assertEquals(AiRequestStatus.CONFIRMED, captor.getValue().getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getOutputPayload().contains("Chicken and rice"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getConfirmationPayload().contains("\"id\":99"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getCorrectionSummary().contains("portionsChanged"));
        org.junit.jupiter.api.Assertions.assertNotNull(captor.getValue().getConfirmedAt());
    }

    @Test
    void confirmDraft_whenFoodLogServiceRejectsItem_doesNotCloseDraft() {
        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setId(10L);
        history.setUser(user);
        history.setRequestType(AiRequestType.VOICE_FOOD_LOG);
        history.setProvider(AiProvider.LOG);
        history.setModel("log-draft-v1");
        history.setStatus(AiRequestStatus.DRAFT_CREATED);
        history.setQuotaConsumed(true);
        history.setCreatedAt(LocalDateTime.now());

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(historyRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(history));
        when(foodLogsService.addFoodLog(any(FoodLogsDto.class), org.mockito.Mockito.eq("user@example.com")))
                .thenThrow(new com.grun.calorietracker.exception.ProductNotFoundException("Food item is not available"));

        assertThrows(com.grun.calorietracker.exception.ProductNotFoundException.class,
                () -> service.confirmDraft("user@example.com", 10L, confirmRequest()));

        assertEquals(AiRequestStatus.DRAFT_CREATED, history.getStatus());
        verify(historyRepository, org.mockito.Mockito.never()).save(history);
    }

    @Test
    void rejectDraft_withFeedback_closesDraftAndStoresReason() {
        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setId(10L);
        history.setUser(user);
        history.setRequestType(AiRequestType.PHOTO_MEAL_LOG);
        history.setProvider(AiProvider.LOG);
        history.setModel("log-draft-v1");
        history.setStatus(AiRequestStatus.DRAFT_CREATED);
        history.setQuotaConsumed(true);
        history.setCreatedAt(LocalDateTime.now());

        AiMealDraftRejectRequestDto request = new AiMealDraftRejectRequestDto();
        request.setReason(AiDraftRejectReason.IRRELEVANT_RESULT);
        request.setFeedback(" Suggested food was completely unrelated. ");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(historyRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(history));
        when(historyRepository.save(any(AiRequestHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.rejectDraft("user@example.com", 10L, request);

        assertEquals(AiRequestStatus.REJECTED, result.getStatus());
        assertEquals(AiDraftRejectReason.IRRELEVANT_RESULT, result.getRejectionReason());
        assertEquals(true, result.getHasRejectionFeedback());
        assertEquals("Suggested food was completely unrelated.", history.getRejectionFeedback());
        org.junit.jupiter.api.Assertions.assertNotNull(history.getRejectedAt());
    }

    private AiVoiceFoodDraftRequestDto request() {
        AiVoiceFoodDraftRequestDto request = new AiVoiceFoodDraftRequestDto();
        request.setTranscript("I ate chicken and rice");
        request.setMealType("LUNCH");
        request.setLogDate(LocalDateTime.of(2026, 6, 1, 13, 30));
        return request;
    }

    private AiMealDraftResponseDto providerResponse() {
        AiMealDraftItemDto item = new AiMealDraftItemDto();
        item.setName("Chicken and rice");
        item.setQuantity(100.0);
        item.setUnit("g");
        item.setConfidence(0.9);

        AiMealDraftResponseDto response = new AiMealDraftResponseDto();
        response.setRequestType(AiRequestType.VOICE_FOOD_LOG);
        response.setProvider(AiProvider.LOG);
        response.setModel("log-draft-v1");
        response.setStatus(AiRequestStatus.DRAFT_CREATED);
        response.setSuggestedMealType("LUNCH");
        response.setSuggestedLogDate(LocalDateTime.of(2026, 6, 1, 13, 30));
        response.setItems(List.of(item));
        return response;
    }

    private FoodItemEntity verifiedFoodItem() {
        FoodItemEntity foodItem = new FoodItemEntity();
        foodItem.setId(12L);
        foodItem.setName("Chicken and rice");
        foodItem.setVerificationStatus(VerificationStatus.VERIFIED);
        foodItem.setQualityScore(90);
        return foodItem;
    }

    private AiMealDraftConfirmRequestDto confirmRequest() {
        AiMealDraftConfirmItemRequestDto item = new AiMealDraftConfirmItemRequestDto();
        item.setFoodItemId(12L);
        item.setPortionSize(150.0);
        item.setPortionUnit(FoodPortionUnit.GRAM);
        item.setMealType("LUNCH");
        item.setLogDate(LocalDateTime.of(2026, 6, 1, 13, 30));

        AiMealDraftConfirmRequestDto request = new AiMealDraftConfirmRequestDto();
        request.setItems(List.of(item));
        return request;
    }
}
