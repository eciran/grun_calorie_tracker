package com.grun.calorietracker.service.support;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.enums.FoodProductReviewCaseStatus;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FoodProductEvidenceExpirySchedulerTest {
    @Mock
    private FoodProductReviewCaseAssetRepository repository;
    private FoodProductEvidenceExpiryScheduler scheduler;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        FoodContributionStorageProperties properties = new FoodContributionStorageProperties();
        properties.setApprovedEvidenceDeletionDelay(Duration.ofHours(24));
        properties.setRejectedEvidenceRetention(Duration.ofDays(7));
        scheduler = new FoodProductEvidenceExpiryScheduler(properties, repository);
        now = LocalDateTime.of(2026, 7, 31, 12, 0);
    }

    @Test
    void approvedEvidenceIsScheduledForDeletionWithinOneDay() {
        scheduler.schedule(10L, FoodProductReviewCaseStatus.APPROVED, now);
        verify(repository).expireReviewCaseAssets(10L, now.plusHours(24));
    }

    @Test
    void rejectedEvidenceKeepsOnlyShortAppealWindow() {
        scheduler.schedule(11L, FoodProductReviewCaseStatus.REJECTED, now);
        verify(repository).expireReviewCaseAssets(11L, now.plusDays(7));
    }

    @Test
    void withdrawnEvidenceExpiresImmediately() {
        scheduler.schedule(12L, FoodProductReviewCaseStatus.WITHDRAWN, now);
        verify(repository).expireReviewCaseAssets(12L, now);
    }

    @Test
    void openStatusDoesNotChangeExpiry() {
        scheduler.schedule(13L, FoodProductReviewCaseStatus.IN_REVIEW, now);
        verify(repository, never()).expireReviewCaseAssets(13L, now);
    }
}
