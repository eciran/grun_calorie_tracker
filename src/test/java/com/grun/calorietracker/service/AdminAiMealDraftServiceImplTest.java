package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminAiQuotaRefundRequestDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.service.impl.AdminAiMealDraftServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAiMealDraftServiceImplTest {

    private final AiRequestHistoryRepository historyRepository = mock(AiRequestHistoryRepository.class);
    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final AdminAiMealDraftServiceImpl service = new AdminAiMealDraftServiceImpl(historyRepository, subscriptionService);

    @Test
    void listRequests_whenRefundableOnly_returnsReviewMetadata() {
        AiRequestHistoryEntity history = history(3, 1, AiRequestStatus.REJECTED);
        when(historyRepository.findRefundableRejectedDrafts(any())).thenReturn(new PageImpl<>(List.of(history)));

        var result = service.listRequests(AiRequestStatus.REJECTED, true, PageRequest.of(0, 25));

        assertEquals(1, result.getTotalElements());
        assertEquals(10L, result.getContent().get(0).getRequestId());
        assertEquals("user@example.com", result.getContent().get(0).getUserEmail());
        assertEquals(2, result.getContent().get(0).getRefundableAmount());
    }

    @Test
    void refundQuota_whenRejectedDraftIsRefundable_updatesHistoryAndSubscription() {
        AiRequestHistoryEntity history = history(1, 0, AiRequestStatus.REJECTED);
        AdminAiQuotaRefundRequestDto request = request(1);
        SubscriptionDto subscription = new SubscriptionDto();
        subscription.setAiUsedThisPeriod(4);
        subscription.setAiRemainingThisPeriod(11);

        when(historyRepository.findById(10L)).thenReturn(Optional.of(history));
        when(subscriptionService.refundConsumedAiQuota(1L, 1)).thenReturn(subscription);
        when(historyRepository.save(any(AiRequestHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.refundQuota("admin@test.com", 10L, request);

        assertEquals(1, result.getRefundedNow());
        assertEquals(1, result.getQuotaRefundedAmount());
        assertEquals("admin@test.com", result.getQuotaRefundedBy());
        assertEquals("AI result was unrelated.", result.getQuotaRefundReason());
        assertEquals(4, result.getSubscription().getAiUsedThisPeriod());
        verify(subscriptionService).refundConsumedAiQuota(1L, 1);
    }

    @Test
    void refundQuota_whenAmountExceedsRequestConsumedQuota_rejects() {
        AiRequestHistoryEntity history = history(1, 0, AiRequestStatus.REJECTED);
        AdminAiQuotaRefundRequestDto request = request(2);

        when(historyRepository.findById(10L)).thenReturn(Optional.of(history));

        assertThrows(IllegalArgumentException.class, () -> service.refundQuota("admin@test.com", 10L, request));
    }

    @Test
    void refundQuota_whenAlreadyFullyRefunded_rejectsSecondRefund() {
        AiRequestHistoryEntity history = history(1, 1, AiRequestStatus.REJECTED);
        AdminAiQuotaRefundRequestDto request = request(1);

        when(historyRepository.findById(10L)).thenReturn(Optional.of(history));

        assertThrows(IllegalArgumentException.class, () -> service.refundQuota("admin@test.com", 10L, request));
    }

    @Test
    void refundQuota_whenDraftNotRejected_rejects() {
        AiRequestHistoryEntity history = history(1, 0, AiRequestStatus.CONFIRMED);
        AdminAiQuotaRefundRequestDto request = request(1);

        when(historyRepository.findById(10L)).thenReturn(Optional.of(history));

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
