package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.entity.FoodProductReviewCaseAssetEntity;
import com.grun.calorietracker.enums.FoodProductAssetDeletionState;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import com.grun.calorietracker.repository.FoodProductReviewCaseRepository;
import com.grun.calorietracker.repository.FoodProductReviewCaseExtractionRepository;
import com.grun.calorietracker.repository.FoodProductUploadSessionRepository;
import com.grun.calorietracker.service.FoodProductEvidenceRetentionService;
import com.grun.calorietracker.service.evidence.FoodProductDirectUploadStorage;
import com.grun.calorietracker.service.support.FoodProductIntakeMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "grun.food-contribution-storage", name = "provider", havingValue = "S3")
public class FoodProductEvidenceRetentionServiceImpl implements FoodProductEvidenceRetentionService {
    private final FoodContributionStorageProperties properties;
    private final FoodProductReviewCaseAssetRepository assetRepository;
    private final FoodProductUploadSessionRepository sessionRepository;
    private final FoodProductReviewCaseRepository reviewCaseRepository;
    private final FoodProductDirectUploadStorage directStorage;
    private final FoodProductIntakeMetrics metrics;

    @Autowired(required = false)
    private FoodProductReviewCaseExtractionRepository extractionRepository;

    @Override
    @Scheduled(fixedDelayString = "${grun.food-contribution-storage.cleanup-interval:1h}")
    @Transactional
    public int cleanupExpiredAndWithdrawn() {
        List<FoodProductReviewCaseAssetEntity> claimed = assetRepository.lockCleanupBatch(
                LocalDateTime.now(), PageRequest.of(0, properties.getCleanupBatchSize()));
        int deleted = 0;
        int failed = 0;
        for (FoodProductReviewCaseAssetEntity asset : claimed) {
            asset.setDeletionState(FoodProductAssetDeletionState.PENDING);
            asset.setDeletionAttemptCount(asset.getDeletionAttemptCount() + 1);
            asset.setLastDeletionError(null);
            try {
                directStorage.delete(asset.getStorageKey());
                asset.setDeletionState(FoodProductAssetDeletionState.DELETED);
                asset.setDeletedAt(LocalDateTime.now());
                deleted++;
            } catch (RuntimeException failure) {
                asset.setDeletionState(FoodProductAssetDeletionState.FAILED);
                asset.setLastDeletionError(safeMessage(failure));
                failed++;
            }
        }
        assetRepository.saveAll(claimed);
        redactExpiredOcrPayloads();
        return deleted;
    }

    @Override
    @Transactional
    public void purgeForUser(Long userId) {
        List<FoodProductReviewCaseAssetEntity> assets = assetRepository.findAllByUploadSessionCreatedById(userId);
        for (FoodProductReviewCaseAssetEntity asset : assets) directStorage.delete(asset.getStorageKey());
        assetRepository.deleteAllInBatch(assets);
        sessionRepository.deleteAllByCreatedById(userId);
        reviewCaseRepository.anonymizeSubmittedByUserId(userId);
    }

    private void redactExpiredOcrPayloads() {
        if (extractionRepository == null) return;
        LocalDateTime now = LocalDateTime.now();
        extractionRepository.lockExpiredRawPayloads(
                        now, PageRequest.of(0, properties.getCleanupBatchSize()))
                .forEach(extraction -> extractionRepository.redactRawPayload(extraction.getId(), now));
    }
    private String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return failure.getClass().getSimpleName();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
