package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;

import com.grun.calorietracker.dto.AdminAiQuotaRefundRequestDto;
import com.grun.calorietracker.dto.AdminAiQuotaRefundRejectRequestDto;
import com.grun.calorietracker.dto.AdminAiMonitoringSummaryDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiQuotaRefundDecision;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.service.impl.AdminAiMealDraftServiceImpl;
import com.grun.calorietracker.service.impl.AdminAiRequestPayloadSanitizer;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAiMealDraftServiceImplTest {

    private final AiRequestHistoryRepository historyRepository = mock(AiRequestHistoryRepository.class);
    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final PushDeliveryService pushDeliveryService = mock(PushDeliveryService.class);
    private final AiProperties aiProperties = new AiProperties();
    private final AdminAiRequestPayloadSanitizer payloadSanitizer = new AdminAiRequestPayloadSanitizer(new ObjectMapper());
    private final AdminAiMealDraftServiceImpl service = new AdminAiMealDraftServiceImpl(historyRepository, subscriptionService, notificationRepository, pushDeliveryService, aiProperties, payloadSanitizer);

    @Test
    void listRequests_whenRefundableOnly_returnsReviewMetadata() {
        AiRequestHistoryEntity history = history(3, 1, AiRequestStatus.REJECTED);
        when(historyRepository.findRefundableRejectedDrafts(any())).thenReturn(new PageImpl<>(List.of(history)));

        var result = service.listRequests(null, AiRequestStatus.REJECTED, true, PageRequest.of(0, 25));

        assertEquals(1, result.getTotalElements());
        assertEquals(10L, result.getContent().get(0).getRequestId());
        assertEquals("user@example.com", result.getContent().get(0).getUserEmail());
        assertEquals(2, result.getContent().get(0).getRefundableAmount());
    }

    @Test
    void getMonitoringSummary_aggregatesProviderCostStatusAndQuotaWithoutUserData() {
        when(historyRepository.summarizeByProviderModelAfter(any())).thenReturn(List.<Object[]>of(
                new Object[]{AiProvider.OPENAI, "gpt-test", "ai-prompt-v2", "USD", 10L, 1000L, 500L, 1500L, 0.12d, 10L, 2L}
        ));
        when(historyRepository.summarizeByRequestTypeStatusAfter(any())).thenReturn(List.<Object[]>of(
                new Object[]{AiRequestType.VOICE_FOOD_LOG, AiRequestStatus.DRAFT_CREATED, 5L, 800L, 5L, 0L},
                new Object[]{AiRequestType.PHOTO_MEAL_LOG, AiRequestStatus.FAILED, 2L, 200L, 2L, 2L},
                new Object[]{AiRequestType.AI_RECIPE_GENERATION, AiRequestStatus.REJECTED, 3L, 500L, 3L, 0L}
        ));

        AdminAiMonitoringSummaryDto result = service.getMonitoringSummary(24);

        assertEquals(10, result.getTotalRequests());
        assertEquals(5, result.getDraftCreated());
        assertEquals(3, result.getRejected());
        assertEquals(2, result.getFailed());
        assertEquals(0.2d, result.getFailureRate(), 0.00001d);
        assertEquals(1500, result.getTotalTokens());
        assertEquals(10, result.getQuotaConsumedAmount());
        assertEquals(2, result.getQuotaRefundedAmount());
        assertEquals(0.12d, result.getEstimatedCostByCurrency().get("USD"), 0.00001d);
        assertEquals("ai-prompt-v2", result.getProviderModels().get(0).getPromptVersion());
        assertEquals(3, result.getRequestStatuses().size());
        assertEquals(true, result.isAttentionRequired());
        assertEquals("FAILURE_RATE_HIGH", result.getAlerts().get(0).getCode());
    }
    @Test
    void getMonitoringSummary_serializedContractDoesNotExposePromptOrUserPayloads() throws Exception {
        when(historyRepository.summarizeByProviderModelAfter(any())).thenReturn(List.of());
        when(historyRepository.summarizeByRequestTypeStatusAfter(any())).thenReturn(List.of());

        String json = new ObjectMapper().findAndRegisterModules()
                .writeValueAsString(service.getMonitoringSummary(24));

        assertFalse(json.contains("inputPayload"));
        assertFalse(json.contains("outputPayload"));
        assertFalse(json.contains("confirmationPayload"));
        assertFalse(json.contains("userEmail"));
        assertFalse(json.contains("rejectionFeedback"));
    }

    @Test
    void refundQuota_whenRejectedDraftIsRefundable_updatesHistoryAndSubscription() {
        AiRequestHistoryEntity history = history(1, 0, AiRequestStatus.REJECTED);
        AdminAiQuotaRefundRequestDto request = request(1);
        SubscriptionDto subscription = new SubscriptionDto();
        subscription.setAiUsedThisPeriod(4);
        subscription.setAiRemainingThisPeriod(11);

        when(historyRepository.findByIdForQuotaRefund(10L)).thenReturn(Optional.of(history));
        when(subscriptionService.refundConsumedAiQuota(1L, 1)).thenReturn(subscription);
        when(historyRepository.save(any(AiRequestHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationRepository.save(any(NotificationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.refundQuota("admin@test.com", 10L, request);

        assertEquals(1, result.getRefundedNow());
        assertEquals(1, result.getQuotaRefundedAmount());
        assertEquals("admin@test.com", result.getQuotaRefundedBy());
        assertEquals("AI result was unrelated.", result.getQuotaRefundReason());
        assertEquals(AiQuotaRefundDecision.APPROVED, result.getQuotaRefundDecision());
        assertEquals(4, result.getSubscription().getAiUsedThisPeriod());
        verify(subscriptionService).refundConsumedAiQuota(1L, 1);
        var notificationCaptor = forClass(NotificationEntity.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        NotificationEntity notification = notificationCaptor.getValue();
        assertEquals("ai_quota_refund_approved", notification.getType());
        assertEquals("VIEW_AI_CREDITS", notification.getPrimaryAction());
        assertEquals("INFO", notification.getSeverity());
        assertEquals("AI_QUOTA_REFUND", notification.getSource());
        assertEquals("AI_REQUEST", notification.getTargetType());
        assertEquals("10", notification.getTargetId());
        assertEquals("ai-credits", notification.getTargetRoute());
        assertEquals("1 AI credit refunded to your account.", notification.getMessage());
        assertEquals(false, notification.getIsRead());
        verify(pushDeliveryService).deliver(notification);
    }

    @Test
    void rejectQuotaRefund_whenRequestIsPending_recordsDecisionAndNotifiesUser() {
        AiRequestHistoryEntity history = history(1, 0, AiRequestStatus.REJECTED);
        history.getUser().setPreferredLanguage(PreferredLanguage.TR);
        AdminAiQuotaRefundRejectRequestDto request = new AdminAiQuotaRefundRejectRequestDto();
        request.setReason("The generated result matched the submitted meal.");

        when(historyRepository.findByIdForQuotaRefund(10L)).thenReturn(Optional.of(history));
        when(historyRepository.save(any(AiRequestHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationRepository.save(any(NotificationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.rejectQuotaRefund("admin@test.com", 10L, request);

        assertEquals(AiQuotaRefundDecision.REJECTED, result.getQuotaRefundDecision());
        assertEquals("The generated result matched the submitted meal.", result.getQuotaRefundDecisionReason());
        assertEquals(0, result.getRefundedNow());
        var notificationCaptor = forClass(NotificationEntity.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertEquals("ai_quota_refund_rejected", notificationCaptor.getValue().getType());
        assertEquals("İsteğinle ilgili bir güncelleme", notificationCaptor.getValue().getTitle());
        assertEquals("AI kredi iadesi isteğin onaylanmadı.", notificationCaptor.getValue().getMessage());
        assertEquals("The generated result matched the submitted meal.", notificationCaptor.getValue().getNote());
        assertEquals("VIEW_AI_CREDITS", notificationCaptor.getValue().getPrimaryAction());
        verify(pushDeliveryService).deliver(notificationCaptor.getValue());
    }
    @Test
    void refundQuota_whenAmountExceedsRequestConsumedQuota_rejects() {
        AiRequestHistoryEntity history = history(1, 0, AiRequestStatus.REJECTED);
        AdminAiQuotaRefundRequestDto request = request(2);

        when(historyRepository.findByIdForQuotaRefund(10L)).thenReturn(Optional.of(history));

        assertThrows(IllegalArgumentException.class, () -> service.refundQuota("admin@test.com", 10L, request));
    }

    @Test
    void refundQuota_whenAlreadyFullyRefunded_rejectsSecondRefund() {
        AiRequestHistoryEntity history = history(1, 1, AiRequestStatus.REJECTED);
        AdminAiQuotaRefundRequestDto request = request(1);

        when(historyRepository.findByIdForQuotaRefund(10L)).thenReturn(Optional.of(history));

        assertThrows(IllegalArgumentException.class, () -> service.refundQuota("admin@test.com", 10L, request));
    }

    @Test
    void refundQuota_whenDraftNotRejected_rejects() {
        AiRequestHistoryEntity history = history(1, 0, AiRequestStatus.CONFIRMED);
        AdminAiQuotaRefundRequestDto request = request(1);

        when(historyRepository.findByIdForQuotaRefund(10L)).thenReturn(Optional.of(history));

        assertThrows(IllegalArgumentException.class, () -> service.refundQuota("admin@test.com", 10L, request));
    }

    private AiRequestHistoryEntity history(int consumedAmount, int refundedAmount, AiRequestStatus status) {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");

        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setId(10L);
        history.setUser(user);
        history.setRequestType(AiRequestType.PHOTO_MEAL_LOG);
        history.setProvider(AiProvider.LOG);
        history.setModel("log-draft-v1");
        history.setStatus(status);
        history.setQuotaConsumed(consumedAmount > 0);
        history.setQuotaConsumedAmount(consumedAmount);
        history.setQuotaRefundedAmount(refundedAmount);
        history.setCreatedAt(LocalDateTime.now());
        return history;
    }

    private AdminAiQuotaRefundRequestDto request(int amount) {
        AdminAiQuotaRefundRequestDto request = new AdminAiQuotaRefundRequestDto();
        request.setAmount(amount);
        request.setReason("AI result was unrelated.");
        return request;
    }
}
