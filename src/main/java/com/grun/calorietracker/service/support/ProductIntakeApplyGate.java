package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.enums.FoodEvidenceField;
import com.grun.calorietracker.enums.FoodProductAssetDeletionState;
import com.grun.calorietracker.enums.FoodProductAssetUploadState;
import com.grun.calorietracker.enums.FoodProductReviewCaseSource;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import com.grun.calorietracker.repository.FoodProductSourceEvidenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;

@Component
@RequiredArgsConstructor
public class ProductIntakeApplyGate {
    private static final EnumSet<FoodEvidenceField> MACRO_FIELDS = EnumSet.of(
            FoodEvidenceField.PROTEIN, FoodEvidenceField.FAT, FoodEvidenceField.CARBS);

    private final FoodProductReviewCaseAssetRepository assetRepository;
    private final FoodProductSourceEvidenceRepository sourceEvidenceRepository;
    private final FoodProductIntakeMetrics metrics;

    public void requirePublishable(FoodProductReviewCaseEntity reviewCase, FoodItemEntity product) {
        requireAcceptedEvidence(reviewCase);
        requireProductQuality(product);
        if (reviewCase.getSource() == FoodProductReviewCaseSource.USER_OCR) {
            var assets = assetRepository.findAllByReviewCaseIdOrderByAssetTypeAsc(reviewCase.getId());
            LocalDateTime now = LocalDateTime.now();
            long activeEvidence = assets.stream()
                    .filter(asset -> asset.getUploadState() == FoodProductAssetUploadState.VERIFIED)
                    .filter(asset -> asset.getDeletionState() == FoodProductAssetDeletionState.ACTIVE)
                    .filter(asset -> asset.getExpiresAt() != null && asset.getExpiresAt().isAfter(now))
                    .map(asset -> asset.getAssetType())
                    .distinct()
                    .count();
            if (activeEvidence < 2) {
                metrics.record("apply_gate", "evidence_assets_unavailable");
                throw new IllegalStateException("Two active verified evidence assets are required for publication.");
            }
        }
    }

    public void requireAcceptedEvidence(FoodProductReviewCaseEntity reviewCase) {
        if (reviewCase.getFoodItem() == null || reviewCase.getFoodItem().getId() == null) {
            metrics.record("apply_gate", "product_missing");
            throw new IllegalStateException("Product intake must be linked to a persisted product.");
        }
        String externalId = "REVIEW_CASE:" + reviewCase.getId();
        var evidence = sourceEvidenceRepository
                .findByFoodItemIdOrderByObservedAtDescIdDesc(reviewCase.getFoodItem().getId()).stream()
                .filter(value -> externalId.equals(value.getExternalId()))
                .toList();
        boolean calories = evidence.stream().anyMatch(value -> value.getFieldName() == FoodEvidenceField.CALORIES);
        boolean macro = evidence.stream().anyMatch(value -> MACRO_FIELDS.contains(value.getFieldName()));
        if (!calories || !macro) {
            metrics.record("apply_gate", "nutrition_evidence_missing");
            throw new IllegalStateException("Accepted calorie and macro source evidence are required.");
        }
    }

    public void requireProductQuality(FoodItemEntity product) {
        if (FoodProductQualityRules.hasCriticalIssue(product)) {
            metrics.record("apply_gate", "blocking_quality_issue");
            throw new IllegalStateException("Product has blocking nutrition quality issues.");
        }
    }
}
