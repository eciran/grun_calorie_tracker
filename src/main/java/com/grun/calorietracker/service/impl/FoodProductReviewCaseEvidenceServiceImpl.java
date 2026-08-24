package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.entity.FoodProductSourceEvidenceEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.FoodProductSourceEvidenceRepository;
import com.grun.calorietracker.service.FoodProductReviewCaseEvidenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FoodProductReviewCaseEvidenceServiceImpl implements FoodProductReviewCaseEvidenceService {
    private static final Map<String, FoodEvidenceField> FIELDS = Map.of(
            "calories", FoodEvidenceField.CALORIES,
            "protein", FoodEvidenceField.PROTEIN,
            "fat", FoodEvidenceField.FAT,
            "carbs", FoodEvidenceField.CARBS,
            "fiber", FoodEvidenceField.FIBER,
            "sugar", FoodEvidenceField.SUGAR,
            "sodium", FoodEvidenceField.SODIUM
    );
    private final FoodProductSourceEvidenceRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public int recordAcceptedEvidence(FoodProductReviewCaseEntity reviewCase) {
        if (reviewCase.getId() == null || reviewCase.getFoodItem() == null || reviewCase.getFoodItem().getId() == null) {
            throw new IllegalArgumentException("Accepted review case must be linked to a product.");
        }
        if (reviewCase.getStatus() != FoodProductReviewCaseStatus.APPROVED
                || reviewCase.getReviewedBy() == null || reviewCase.getReviewedBy().isBlank()) {
            throw new IllegalStateException("Only reviewer-approved cases can create source evidence.");
        }
        Map<String, Object> submitted = parse(reviewCase.getSubmittedValuesJson());
        List<FoodProductSourceEvidenceEntity> candidates = new ArrayList<>();
        FoodDataSource provider = userSource(reviewCase.getSource())
                ? FoodDataSource.USER_SUBMITTED_LABEL : FoodDataSource.ADMIN_REVIEWED_LABEL;
        String externalId = "REVIEW_CASE:" + reviewCase.getId();
        LocalDateTime observedAt = reviewCase.getReviewedAt() == null ? LocalDateTime.now() : reviewCase.getReviewedAt();
        String sourceVersion = "review-case-schema:" + reviewCase.getSchemaVersion();
        for (Map.Entry<String, FoodEvidenceField> field : FIELDS.entrySet()) {
            Double value = number(submitted.get(field.getKey()));
            if (value == null || value < 0 || value.isNaN() || value.isInfinite()) continue;
            FoodProductSourceEvidenceEntity evidence = new FoodProductSourceEvidenceEntity();
            evidence.setFoodItem(reviewCase.getFoodItem());
            evidence.setProvider(provider);
            evidence.setExternalId(externalId);
            evidence.setFieldName(field.getValue());
            evidence.setNumericValue(value);
            evidence.setBasis(FoodEvidenceBasis.LABEL);
            evidence.setConfidenceScore(100);
            evidence.setObservedAt(observedAt);
            evidence.setSourceVersion(sourceVersion);
            evidence.setReviewerIdentity(reviewCase.getReviewedBy());
            evidence.setFingerprint(fingerprint(provider, externalId, field.getValue(), value, sourceVersion));
            candidates.add(evidence);
        }
        if (candidates.isEmpty()) throw new IllegalArgumentException("Accepted review case has no numeric nutrition evidence.");
        Set<String> existing = new HashSet<>(repository.findExistingFingerprints(candidates.stream().map(FoodProductSourceEvidenceEntity::getFingerprint).toList()));
        List<FoodProductSourceEvidenceEntity> additions = candidates.stream().filter(value -> !existing.contains(value.getFingerprint())).toList();
        repository.saveAll(additions);
        return additions.size();
    }

    private Map<String, Object> parse(String json) {
        try {
            if (json == null || json.isBlank()) return Map.of();
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception failure) {
            throw new IllegalArgumentException("Review case submitted values are invalid.", failure);
        }
    }

    private Double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String text && !text.isBlank()) {
            try { return Double.valueOf(text.replace(',', '.')); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private boolean userSource(FoodProductReviewCaseSource source) {
        return source != FoodProductReviewCaseSource.ADMIN_MANUAL;
    }

    private String fingerprint(FoodDataSource provider, String externalId, FoodEvidenceField field, Double value, String sourceVersion) {
        String raw = provider + "|" + externalId + "|" + field + "|" + value + "|LABEL|" + sourceVersion;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }
}