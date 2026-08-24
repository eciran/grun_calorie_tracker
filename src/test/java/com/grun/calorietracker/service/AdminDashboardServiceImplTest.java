package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminDashboardSummaryDto;
import com.grun.calorietracker.enums.AiDraftRejectReason;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.ProductCorrectionStatus;
import com.grun.calorietracker.enums.ProductQualitySuggestionStatus;
import com.grun.calorietracker.enums.RecipeImportCandidateStatus;
import com.grun.calorietracker.enums.RecipeReportStatus;
import com.grun.calorietracker.enums.RecipeVisibility;import com.grun.calorietracker.enums.SubscriptionPlan;
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
import com.grun.calorietracker.repository.RecipeRepository;import com.grun.calorietracker.repository.SubscriptionProviderEventRepository;
import com.grun.calorietracker.repository.SubscriptionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.AdminDashboardServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FoodItemRepository foodItemRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionProviderEventRepository subscriptionProviderEventRepository;

    @Mock
    private AiRequestHistoryRepository aiRequestHistoryRepository;

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private RecipeImportCandidateRepository recipeImportCandidateRepository;

    @Mock
    private RecipeReportRepository recipeReportRepository;

    @Mock
    private ProductCorrectionSuggestionRepository productCorrectionSuggestionRepository;

    @Mock
    private ProductQualitySuggestionRepository productQualitySuggestionRepository;
    @Test
    void getSummary_returnsUserAndFoodCatalogMetrics() {
        AdminDashboardServiceImpl service = new AdminDashboardServiceImpl(
                userRepository,
                foodItemRepository,
                subscriptionRepository,
                subscriptionProviderEventRepository,
                aiRequestHistoryRepository,
                recipeRepository,
                recipeImportCandidateRepository,
                recipeReportRepository,
                productCorrectionSuggestionRepository,
                productQualitySuggestionRepository
        );

        when(userRepository.count()).thenReturn(10L);
        when(userRepository.countByRole(UserRole.STANDARD)).thenReturn(7L);
        when(userRepository.countByRole(UserRole.PRO)).thenReturn(2L);
        when(userRepository.countByRoleIn(java.util.Arrays.stream(UserRole.values()).filter(UserRole::isAdminRole).toList())).thenReturn(1L);
        when(foodItemRepository.count()).thenReturn(100L);
        when(foodItemRepository.countByVerificationStatus(VerificationStatus.VERIFIED)).thenReturn(60L);
        when(foodItemRepository.countByVerificationStatus(VerificationStatus.RAW_IMPORTED)).thenReturn(25L);
        when(foodItemRepository.countByVerificationStatus(VerificationStatus.NEEDS_REVIEW)).thenReturn(10L);
        when(foodItemRepository.countByVerificationStatus(VerificationStatus.REJECTED)).thenReturn(5L);
        when(foodItemRepository.countReviewQueueProducts(
                List.of(VerificationStatus.RAW_IMPORTED, VerificationStatus.NEEDS_REVIEW)
        )).thenReturn(35L);
        when(recipeRepository.countByVisibilityAndVerificationStatusAndArchivedFalse(
                RecipeVisibility.COMMUNITY_PENDING,
                VerificationStatus.NEEDS_REVIEW
        )).thenReturn(4L);
        when(recipeImportCandidateRepository.countByStatus(RecipeImportCandidateStatus.PENDING)).thenReturn(2L);
        when(recipeReportRepository.countByStatus(RecipeReportStatus.OPEN)).thenReturn(3L);
        when(productCorrectionSuggestionRepository.countByStatus(ProductCorrectionStatus.OPEN)).thenReturn(5L);
        when(productQualitySuggestionRepository.countByStatus(ProductQualitySuggestionStatus.OPEN)).thenReturn(6L);
        when(aiRequestHistoryRepository.countRefundableRejectedDrafts()).thenReturn(7L);        when(subscriptionRepository.countByPlanTypeAndStatus(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE)).thenReturn(4L);
        when(subscriptionRepository.countByPlanTypeAndStatus(SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE)).thenReturn(2L);
        when(subscriptionRepository.countByStatus(SubscriptionStatus.CANCELED)).thenReturn(3L);
        when(subscriptionRepository.countByStatus(SubscriptionStatus.REFUNDED)).thenReturn(1L);
        when(subscriptionRepository.countActiveSubscriptionsWithExhaustedAiQuota()).thenReturn(6L);
        when(subscriptionProviderEventRepository.countByStatus(SubscriptionProviderEventStatus.FAILED)).thenReturn(8L);
        when(subscriptionProviderEventRepository.countByReceivedAtAfter(org.mockito.ArgumentMatchers.any())).thenReturn(12L);
        when(aiRequestHistoryRepository.countByCreatedAtAfter(org.mockito.ArgumentMatchers.any())).thenReturn(50L);
        when(aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(eq(AiRequestStatus.CONFIRMED), org.mockito.ArgumentMatchers.any())).thenReturn(30L);
        when(aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(eq(AiRequestStatus.REJECTED), org.mockito.ArgumentMatchers.any())).thenReturn(12L);
        when(aiRequestHistoryRepository.countByStatusAndCreatedAtAfter(eq(AiRequestStatus.FAILED), org.mockito.ArgumentMatchers.any())).thenReturn(3L);
        when(aiRequestHistoryRepository.countRejectedDraftsByReasonAfter(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new Object[]{AiDraftRejectReason.WRONG_PORTION, 7L}, new Object[]{AiDraftRejectReason.LOW_CONFIDENCE, 5L}));

        AdminDashboardSummaryDto summary = service.getSummary();

        assertThat(summary.getTotalUsers()).isEqualTo(10L);
        assertThat(summary.getStandardUsers()).isEqualTo(7L);
        assertThat(summary.getProUsers()).isEqualTo(2L);
        assertThat(summary.getAdminUsers()).isEqualTo(1L);
        assertThat(summary.getTotalProducts()).isEqualTo(100L);
        assertThat(summary.getVerifiedProducts()).isEqualTo(60L);
        assertThat(summary.getRawImportedProducts()).isEqualTo(25L);
        assertThat(summary.getNeedsReviewProducts()).isEqualTo(10L);
        assertThat(summary.getRejectedProducts()).isEqualTo(5L);
        assertThat(summary.getReviewQueueProducts()).isEqualTo(35L);
        assertThat(summary.getPendingRecipeApprovals()).isEqualTo(4L);
        assertThat(summary.getPendingRecipeImportCandidates()).isEqualTo(2L);
        assertThat(summary.getOpenRecipeReports()).isEqualTo(3L);
        assertThat(summary.getOpenProductCorrectionSuggestions()).isEqualTo(5L);
        assertThat(summary.getOpenProductQualitySuggestions()).isEqualTo(6L);
        assertThat(summary.getRefundableAiRequests()).isEqualTo(7L);
        assertThat(summary.getTotalAdminApprovalItems()).isEqualTo(62L);        assertThat(summary.getActivePlusSubscriptions()).isEqualTo(4L);
        assertThat(summary.getActiveProSubscriptions()).isEqualTo(2L);
        assertThat(summary.getCanceledSubscriptions()).isEqualTo(3L);
        assertThat(summary.getRefundedSubscriptions()).isEqualTo(1L);
        assertThat(summary.getAiQuotaExhaustedSubscriptions()).isEqualTo(6L);
        assertThat(summary.getFailedSubscriptionProviderEvents()).isEqualTo(8L);
        assertThat(summary.getSubscriptionProviderEventsLast24Hours()).isEqualTo(12L);
        assertThat(summary.getAiRequestsLast7Days()).isEqualTo(50L);
        assertThat(summary.getAiConfirmedLast7Days()).isEqualTo(30L);
        assertThat(summary.getAiRejectedLast7Days()).isEqualTo(12L);
        assertThat(summary.getAiFailedLast7Days()).isEqualTo(3L);
        assertThat(summary.getAiRejectionReasonsLast7Days()).containsEntry("WRONG_PORTION", 7L).containsEntry("LOW_CONFIDENCE", 5L);

        verify(foodItemRepository).countReviewQueueProducts(
                List.of(VerificationStatus.RAW_IMPORTED, VerificationStatus.NEEDS_REVIEW)
        );
    }
}


