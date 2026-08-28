package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.entity.FoodProductReviewCaseAssetEntity;
import com.grun.calorietracker.enums.FoodProductAssetDeletionState;
import com.grun.calorietracker.enums.FoodProductAssetUploadState;
import com.grun.calorietracker.enums.FoodProductReviewAssetType;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import com.grun.calorietracker.service.ProductNutritionOcrEvidenceResolver;
import com.grun.calorietracker.service.evidence.FoodProductDirectUploadStorage;
import com.grun.calorietracker.service.model.ProductNutritionOcrEvidence;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "grun.food-contribution-storage", name = "provider", havingValue = "S3")
public class ProductNutritionOcrEvidenceResolverImpl implements ProductNutritionOcrEvidenceResolver {
    private final FoodProductReviewCaseAssetRepository assets;
    private final FoodProductDirectUploadStorage storage;
    private final FoodContributionStorageProperties storageProperties;

    @Override
    @Transactional(readOnly = true)
    public ProductNutritionOcrEvidence resolve(ProductNutritionOcrFallbackRequest request) {
        FoodProductReviewCaseAssetEntity asset = assets.findById(request.nutritionAssetId())
                .orElseThrow(() -> new IllegalArgumentException("Nutrition evidence was not found."));
        requireOwnedCase(asset, request);
        if (asset.getAssetType() != FoodProductReviewAssetType.NUTRITION_LABEL
                || asset.getUploadState() != FoodProductAssetUploadState.VERIFIED
                || asset.getDeletionState() != FoodProductAssetDeletionState.ACTIVE
                || asset.getExpiresAt() == null
                || !asset.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Nutrition evidence is not active and verified.");
        }
        byte[] bytes = storage.readBounded(asset.getStorageKey(), storageProperties.getMaxUploadBytes());
        return new ProductNutritionOcrEvidence(bytes, asset.getContentType(), asset.getSha256());
    }

    private void requireOwnedCase(FoodProductReviewCaseAssetEntity asset, ProductNutritionOcrFallbackRequest request) {
        if (asset.getReviewCase() == null
                || !request.reviewCaseId().equals(asset.getReviewCase().getId())
                || asset.getReviewCase().getSubmittedBy() == null
                || !request.requesterUserId().equals(asset.getReviewCase().getSubmittedBy().getId())
                || asset.getUploadSession() == null
                || asset.getUploadSession().getCreatedBy() == null
                || !request.requesterUserId().equals(asset.getUploadSession().getCreatedBy().getId())) {
            throw new InvalidCredentialsException("Invalid credential");
        }
    }
}
