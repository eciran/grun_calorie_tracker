package com.grun.calorietracker.service.support;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.enums.FoodProductReviewCaseStatus;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FoodProductEvidenceExpiryScheduler {
    private final FoodContributionStorageProperties properties;
    private final FoodProductReviewCaseAssetRepository assetRepository;

    public int schedule(Long reviewCaseId, FoodProductReviewCaseStatus status, LocalDateTime decidedAt) {
        if (reviewCaseId == null || status == null || decidedAt == null) {
            throw new IllegalArgumentException("Evidence expiry decision is incomplete.");
        }
        LocalDateTime expiresAt = switch (status) {
            case APPROVED, APPLIED -> decidedAt.plus(properties.getApprovedEvidenceDeletionDelay());
            case REJECTED -> decidedAt.plus(properties.getRejectedEvidenceRetention());
            case WITHDRAWN, EXPIRED -> decidedAt;
            default -> null;
        };
        return expiresAt == null ? 0 : assetRepository.expireReviewCaseAssets(reviewCaseId, expiresAt);
    }
}
