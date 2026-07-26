package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminDashboardSummaryDto;
import com.grun.calorietracker.enums.AiDraftRejectReason;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.ProductCorrectionStatus;
import com.grun.calorietracker.enums.ProductQualitySuggestionStatus;
import com.grun.calorietracker.enums.RecipeImportCandidateStatus;
import com.grun.calorietracker.enums.RecipeReportStatus;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import com.grun.calorietracker.enums.SubscriptionStatus;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.ProductCorrectionSuggestionRepository;
import com.grun.calorietracker.repository.ProductQualitySuggestionRepository;
import com.grun.calorietracker.repository.RecipeImportCandidateRepository;
import com.grun.calorietracker.repository.RecipeReportRepository;
import com.grun.calorietracker.repository.RecipeRepository;
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
    private final RecipeRepository recipeRepository;
    private final RecipeImportCandidateRepository recipeImportCandidateRepository;
    private final RecipeReportRepository recipeReportRepository;
    private final ProductCorrectionSuggestionRepository productCorrectionSuggestionRepository;
    private final ProductQualitySuggestionRepository productQualitySuggestionRepository;

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
        long pendingRecipeApprovals = recipeRepository.countByVisibilityAndVerificationStatusAndArchivedFalse(
                RecipeVisibility.COMMUNITY_PENDING,
                VerificationStatus.NEEDS_REVIEW
        );
        long pendingRecipeImportCandidates = recipeImportCandidateRepository.countByStatus(RecipeImportCandidateStatus.PENDING);
        long openRecipeReports = recipeReportRepository.countByStatus(RecipeReportStatus.OPEN);
        long openProductCorrectionSuggestions = productCorrectionSuggestionRepository.countByStatus(ProductCorrectionStatus.OPEN);
        long openProductQualitySuggestions = productQualitySuggestionRepository.countByStatus(ProductQualitySuggestionStatus.OPEN);
        long refundableAiRequests = aiRequestHistoryRepository.countRefundableRejectedDrafts();
        long totalAdminApprovalItems = reviewQueueProducts
                + pendingRecipeApprovals
                + pendingRecipeImportCandidates
                + openRecipeReports
                + openProductCorrectionSuggestions
                + openProductQualitySuggestions
                + refundableAiRequests;
        LocalDateTime aiWindowStart = LocalDateTime.now().minusDays(7);
        Map<String, Long> aiRejectionReasons = countAiRejectionReasons(aiWindowStart);

        AdminDashboardSummaryDto summary = new AdminDashboardSummaryDto();
        summary.setTotalUsers(userRepository.count());
        summary.setStandardUsers(userRepository.countByRole(UserRole.STANDARD));
        summary.setProUsers(userRepository.countByRole(UserRole.PRO));
        summary.setAdminUsers(userRepository.countByRoleIn(java.util.Arrays.stream(UserRole.values()).filter(UserRole::isAdminRole).toList()));
        summary.setTotalProducts(foodItemRepository.count());
        summary.setVerifiedProducts(verifiedProducts);
        summary.setRawImportedProducts(rawImportedProducts);
        summary.setNeedsReviewProducts(needsReviewProducts);
        summary.setRejectedProducts(rejectedProducts);
        summary.setReviewQueueProducts(reviewQueueProducts);
        summary.setPendingRecipeApprovals(pendingRecipeApprovals);
        summary.setPendingRecipeImportCandidates(pendingRecipeImportCandidates);
        summary.setOpenRecipeReports(openRecipeReports);
        summary.setOpenProductCorrectionSuggestions(openProductCorrectionSuggestions);
        summary.setOpenProductQualitySuggestions(openProductQualitySuggestions);
        summary.setRefundableAiRequests(refundableAiRequests);
        summary.setTotalAdminApprovalItems(totalAdminApprovalItems);
        summary.setActivePlusSubscriptions(subscriptionRepository.countByPlanTypeAndStatus(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE));
        summary.setActiveProSubscriptions(subscriptionRepository.countByPlanTypeAndStatus(SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE));
        summary.setCanceledSubscriptions(subscriptionRepository.countByStatus(SubscriptionStatus.CANCELED));
        summary.setRefundedSubscriptions(subscriptionRepository.countByStatus(SubscriptionStatus.REFUNDED));
        summary.setAiQuotaExhaustedSubscriptions(subscriptionRepository.countActiveSubscriptionsWithExhaustedAiQuota());
        summary.setFailedSubscriptionProviderEvents(subscriptionProviderEventRepository.countByStatus(SubscriptionProviderEventStatus.FAILED));
        summary.setSubscriptionProviderEventsLast24Hours(subscriptionProviderEventRepository.countByReceivedAtAfter(LocalDateTime.now().minusHours(24)));
        summary.setAiRequestsLast7Days(aiRequestHistoryRepository.countByCreatedAtAfter(aiWindowStart));
        summary.setAiConfirmedLast7Days(aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(AiRequestStatus.CONFIRMED, aiWindowStart));
        summary.setAiRejectedLast7Days(aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(AiRequestStatus.REJECTED, aiWindowStart));
        summary.setAiFailedLast7Days(aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(AiRequestStatus.FAILED, aiWindowStart));
        summary.setAiRejectionReasonsLast7Days(aiRejectionReasons);
        return summary;
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

