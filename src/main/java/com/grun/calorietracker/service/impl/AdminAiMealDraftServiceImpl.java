package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminAiRequestReviewDto;
import com.grun.calorietracker.dto.AdminAiQuotaRefundRequestDto;
import com.grun.calorietracker.dto.AdminAiQuotaRefundResponseDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.service.AdminAiMealDraftService;
import com.grun.calorietracker.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminAiMealDraftServiceImpl implements AdminAiMealDraftService {

    private final AiRequestHistoryRepository aiRequestHistoryRepository;
    private final SubscriptionService subscriptionService;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminAiRequestReviewDto> listRequests(AiRequestStatus status, boolean refundableOnly, Pageable pageable) {
        Page<AiRequestHistoryEntity> requests;
        if (refundableOnly) {
            requests = aiRequestHistoryRepository.findRefundableRejectedDrafts(pageable);
        } else if (status != null) {
            requests = aiRequestHistoryRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            requests = aiRequestHistoryRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return requests.map(this::toReviewDto);
    }

    @Override
    @Transactional
    public AdminAiQuotaRefundResponseDto refundQuota(String adminEmail, Long requestId, AdminAiQuotaRefundRequestDto request) {
        AiRequestHistoryEntity history = aiRequestHistoryRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("AI request was not found."));
        if (!Boolean.TRUE.equals(history.getQuotaConsumed())) {
            throw new IllegalArgumentException("AI request did not consume quota.");
        }
        if (history.getStatus() != AiRequestStatus.REJECTED) {
            throw new IllegalArgumentException("AI quota can be refunded only for rejected drafts.");
        }
        int consumed = safeInt(history.getQuotaConsumedAmount());
        int refunded = safeInt(history.getQuotaRefundedAmount());
        int refundable = consumed - refunded;
        int amount = request.getAmount() == null ? 0 : request.getAmount();
        if (amount <= 0) {
            throw new IllegalArgumentException("AI quota refund amount must be greater than zero.");
        }
        if (amount > refundable) {
            throw new IllegalArgumentException("AI quota refund amount exceeds refundable quota for this request.");
        }
        if (history.getUser() == null || history.getUser().getId() == null) {
            throw new IllegalArgumentException("AI request user is missing.");
        }

        SubscriptionDto subscription = subscriptionService.refundConsumedAiQuota(history.getUser().getId(), amount);
        LocalDateTime now = LocalDateTime.now();
        history.setQuotaRefundedAmount(refunded + amount);
        history.setQuotaRefundReason(request.getReason().trim());
        history.setQuotaRefundedBy(adminEmail);
        history.setQuotaRefundedAt(now);
        AiRequestHistoryEntity saved = aiRequestHistoryRepository.save(history);
        return toDto(saved, amount, subscription);
    }

    private AdminAiRequestReviewDto toReviewDto(AiRequestHistoryEntity entity) {
        int consumed = safeInt(entity.getQuotaConsumedAmount());
        int refunded = safeInt(entity.getQuotaRefundedAmount());
        AdminAiRequestReviewDto dto = new AdminAiRequestReviewDto();
        dto.setRequestId(entity.getId());
        dto.setUserId(entity.getUser() == null ? null : entity.getUser().getId());
        dto.setUserEmail(entity.getUser() == null ? null : entity.getUser().getEmail());
        dto.setRequestType(entity.getRequestType());
        dto.setProvider(entity.getProvider());
        dto.setModel(entity.getModel());
        dto.setStatus(entity.getStatus());
        dto.setQuotaConsumed(entity.getQuotaConsumed());
        dto.setQuotaConsumedAmount(consumed);
        dto.setQuotaRefundedAmount(refunded);
        dto.setRefundableAmount(Math.max(consumed - refunded, 0));
        dto.setRejectionReason(entity.getRejectionReason());
        dto.setRejectionFeedback(entity.getRejectionFeedback());
        dto.setLatencyMs(entity.getLatencyMs());
        dto.setTotalTokens(entity.getTotalTokens());
        dto.setEstimatedCost(entity.getEstimatedCost());
        dto.setCostCurrency(entity.getCostCurrency());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setRejectedAt(entity.getRejectedAt());
        dto.setQuotaRefundReason(entity.getQuotaRefundReason());
        dto.setQuotaRefundedBy(entity.getQuotaRefundedBy());
        dto.setQuotaRefundedAt(entity.getQuotaRefundedAt());
        return dto;
    }

    private AdminAiQuotaRefundResponseDto toDto(AiRequestHistoryEntity entity, int refundedNow, SubscriptionDto subscription) {
        AdminAiQuotaRefundResponseDto dto = new AdminAiQuotaRefundResponseDto();
        dto.setRequestId(entity.getId());
        dto.setUserId(entity.getUser() == null ? null : entity.getUser().getId());
        dto.setStatus(entity.getStatus());
        dto.setQuotaConsumedAmount(safeInt(entity.getQuotaConsumedAmount()));
        dto.setQuotaRefundedAmount(safeInt(entity.getQuotaRefundedAmount()));
        dto.setRefundedNow(refundedNow);
        dto.setQuotaRefundReason(entity.getQuotaRefundReason());
        dto.setQuotaRefundedBy(entity.getQuotaRefundedBy());
        dto.setQuotaRefundedAt(entity.getQuotaRefundedAt());
        dto.setSubscription(subscription);
        return dto;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
