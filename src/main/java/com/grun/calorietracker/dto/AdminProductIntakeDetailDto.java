package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AdminProductIntakeDetailDto(
        AdminProductIntakeSummaryDto summary,
        String reviewNote,
        Long linkedFoodItemId,
        CatalogPublicationStatus linkedProductPublicationStatus,
        Map<String, Object> submittedFields,
        Map<String, Object> catalogFields,
        List<FieldComparison> fieldComparisons,
        List<String> warnings,
        LocalDateTime evidenceExpiresAt,
        List<EvidenceDescriptor> evidence,
        List<CorroboratingEvidence> corroboratingEvidence
) {
    public record FieldComparison(String field, Object submittedValue, Object catalogValue, boolean equal, boolean highImpact) {}

    public record CorroboratingEvidence(
            FoodEvidenceField field,
            FoodDataSource provider,
            Double numericValue,
            FoodEvidenceBasis basis,
            Integer confidenceScore,
            LocalDateTime observedAt,
            String sourceVersion
    ) {}

    public record EvidenceDescriptor(
            Long assetId,
            FoodProductReviewAssetType assetType,
            String contentType,
            Long sizeBytes,
            Integer width,
            Integer height,
            FoodProductAssetUploadState uploadState,
            FoodProductAssetDeletionState deletionState,
            LocalDateTime expiresAt,
            boolean available
    ) {}
}