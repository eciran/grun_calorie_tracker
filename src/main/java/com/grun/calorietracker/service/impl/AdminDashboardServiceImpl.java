package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminDashboardSummaryDto;
import com.grun.calorietracker.enums.AiDraftRejectReason;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import com.grun.calorietracker.enums.SubscriptionStatus;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.SubscriptionProviderEventRepository;
import com.grun.calorietracker.repository.SubscriptionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserRepository userRepository;
    private final FoodItemRepository foodItemRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionProviderEventRepository subscriptionProviderEventRepository;
    private final AiRequestHistoryRepository aiRequestHistoryRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardSummaryDto getSummary() {
        long verifiedProducts = foodItemRepository.countByVerificationStatus(VerificationStatus.VERIFIED);
        long rawImportedProducts = foodItemRepository.countByVerificationStatus(VerificationStatus.RAW_IMPORTED);
        long needsReviewProducts = foodItemRepository.countByVerificationStatus(VerificationStatus.NEEDS_REVIEW);
        long rejectedProducts = foodItemRepository.countByVerificationStatus(VerificationStatus.REJECTED);
        long reviewQueueProducts = foodItemRepository.countReviewQueueProducts(
                List.of(VerificationStatus.RAW_IMPORTED, VerificationStatus.NEEDS_REVIEW)
        );
        LocalDateTime aiWindowStart = LocalDateTime.now().minusDays(7);
        Map<String, Long> aiRejectionReasons = countAiRejectionReasons(aiWindowStart);

        return new AdminDashboardSummaryDto(
                userRepository.count(),
                userRepository.countByRole(UserRole.STANDARD),
                userRepository.countByRole(UserRole.PRO),
                userRepository.countByRole(UserRole.ADMIN),
                foodItemRepository.count(),
                verifiedProducts,
                rawImportedProducts,
                needsReviewProducts,
                rejectedProducts,
                reviewQueueProducts,
                subscriptionRepository.countByPlanTypeAndStatus(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE),
                subscriptionRepository.countByPlanTypeAndStatus(SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE),
                subscriptionRepository.countByStatus(SubscriptionStatus.CANCELED),
                subscriptionRepository.countByStatus(SubscriptionStatus.REFUNDED),
                subscriptionRepository.countActiveSubscriptionsWithExhaustedAiQuota(),
                subscriptionProviderEventRepository.countByStatus(SubscriptionProviderEventStatus.FAILED),
                subscriptionProviderEventRepository.countByReceivedAtAfter(LocalDateTime.now().minusHours(24)),
                aiRequestHistoryRepository.countByCreatedAtAfter(aiWindowStart),
                aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(AiRequestStatus.CONFIRMED, aiWindowStart),
                aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(AiRequestStatus.REJECTED, aiWindowStart),
                aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(AiRequestStatus.FAILED, aiWindowStart),
                aiRejectionReasons
        );
    }

    private Map<String, Long> countAiRejectionReasons(LocalDateTime rejectedAfter) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (AiDraftRejectReason reason : AiDraftRejectReason.values()) {
            counts.put(reason.name(), 0L);
        }
        for (Object[] row : aiRequestHistoryRepository.countRejectedDraftsByReasonAfter(rejectedAfter)) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            String reason = row[0] instanceof AiDraftRejectReason enumReason ? enumReason.name() : String.valueOf(row[0]);
            long count = row[1] instanceof Number number ? number.longValue() : 0L;
            counts.put(reason, count);
        }
        return counts;
    }
}

