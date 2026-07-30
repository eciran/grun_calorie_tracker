package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodProductReviewCaseRepository;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import com.grun.calorietracker.service.FoodProductReviewCaseService;
import com.grun.calorietracker.service.model.FoodProductReviewCaseCommand;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import com.grun.calorietracker.service.support.GtinValidator;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FoodProductReviewCaseServiceImpl implements FoodProductReviewCaseService {

    private static final int MAX_JSON_CHARS = 64_000;
    private static final Map<FoodProductReviewCaseStatus, Set<FoodProductReviewCaseStatus>> TRANSITIONS = Map.of(
            FoodProductReviewCaseStatus.SUBMITTED, EnumSet.of(
                    FoodProductReviewCaseStatus.IN_REVIEW,
                    FoodProductReviewCaseStatus.NEEDS_SUBMITTER_ACTION,
                    FoodProductReviewCaseStatus.WITHDRAWN,
                    FoodProductReviewCaseStatus.EXPIRED
            ),
            FoodProductReviewCaseStatus.NEEDS_SUBMITTER_ACTION, EnumSet.of(
                    FoodProductReviewCaseStatus.SUBMITTED,
                    FoodProductReviewCaseStatus.WITHDRAWN,
                    FoodProductReviewCaseStatus.EXPIRED
            ),
            FoodProductReviewCaseStatus.IN_REVIEW, EnumSet.of(
                    FoodProductReviewCaseStatus.APPROVED,
                    FoodProductReviewCaseStatus.REJECTED,
                    FoodProductReviewCaseStatus.WITHDRAWN,
                    FoodProductReviewCaseStatus.EXPIRED
            ),
            FoodProductReviewCaseStatus.APPROVED, EnumSet.of(FoodProductReviewCaseStatus.APPLIED)
    );

    private final FoodProductReviewCaseRepository reviewCaseRepository;
    private final FoodItemRepository foodItemRepository;

    @Autowired(required = false)
    private FoodProductReviewCaseAssetRepository reviewCaseAssetRepository;

    @Override
    @Transactional
    public synchronized FoodProductReviewCaseEntity finalizeCase(FoodProductReviewCaseCommand command) {
        validateCommand(command);
        return reviewCaseRepository.findByIdempotencyKey(command.idempotencyKey())
                .orElseGet(() -> createCase(command));
    }

    @Override
    @Transactional
    public FoodProductReviewCaseEntity transition(
            Long caseId,
            FoodProductReviewCaseStatus target,
            String actor,
            String note
    ) {
        FoodProductReviewCaseEntity reviewCase = reviewCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Food product review case was not found"));
        Set<FoodProductReviewCaseStatus> allowed =
                TRANSITIONS.getOrDefault(reviewCase.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            throw new RequestConflictException(
                    "Review case cannot transition from " + reviewCase.getStatus() + " to " + target
            );
        }
        if ((target == FoodProductReviewCaseStatus.APPROVED
                || target == FoodProductReviewCaseStatus.REJECTED)
                && (actor == null || actor.isBlank())) {
            throw new IllegalArgumentException("Reviewer identity is required");
        }
        reviewCase.setStatus(target);
        reviewCase.setReviewNote(trimToNull(note));
        if (target == FoodProductReviewCaseStatus.APPROVED
                || target == FoodProductReviewCaseStatus.REJECTED) {
            reviewCase.setReviewedBy(actor.trim());
            reviewCase.setReviewedAt(LocalDateTime.now());
        }
        if (target == FoodProductReviewCaseStatus.APPLIED) {
            reviewCase.setAppliedAt(LocalDateTime.now());
        }
        FoodProductReviewCaseEntity saved = reviewCaseRepository.save(reviewCase);
        if (target == FoodProductReviewCaseStatus.WITHDRAWN && reviewCaseAssetRepository != null) {
            reviewCaseAssetRepository.expireReviewCaseAssets(saved.getId(), LocalDateTime.now());
        }
        return saved;
    }

    private FoodProductReviewCaseEntity createCase(FoodProductReviewCaseCommand command) {
        String normalizedBarcode = normalizeAndValidateBarcode(command);
        FoodItemEntity target;
        FoodProductResolutionMode resolutionMode;

        if (command.existingFoodItemId() != null) {
            target = foodItemRepository.findById(command.existingFoodItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Food item was not found"));
            resolutionMode = FoodProductResolutionMode.UPDATE_EXISTING;
        } else {
            target = findProduct(
                    normalizedBarcode,
                    command.marketRegion(),
                    CatalogPublicationStatus.PUBLISHED
            );
            if (target != null) {
                resolutionMode = FoodProductResolutionMode.UPDATE_EXISTING;
            } else {
                target = findProduct(
                        normalizedBarcode,
                        command.marketRegion(),
                        CatalogPublicationStatus.INTERNAL_REVIEW
                );
                if (target == null) {
                    target = createCandidate(command, normalizedBarcode);
                }
                resolutionMode = FoodProductResolutionMode.NEW_CANDIDATE;
            }
        }

        FoodProductReviewCaseEntity reviewCase = new FoodProductReviewCaseEntity();
        reviewCase.setIdempotencyKey(command.idempotencyKey().trim());
        reviewCase.setSource(Objects.requireNonNull(command.source()));
        reviewCase.setSourceReference(trimToNull(command.sourceReference()));
        reviewCase.setSubmittedBy(command.submittedBy());
        reviewCase.setOriginalBarcode(trimToNull(command.barcode()));
        reviewCase.setNormalizedBarcode(normalizedBarcode);
        reviewCase.setMarketRegion(command.marketRegion());
        reviewCase.setFoodItem(target);
        reviewCase.setResolutionMode(resolutionMode);
        reviewCase.setStatus(FoodProductReviewCaseStatus.SUBMITTED);
        reviewCase.setRiskLevel(command.riskLevel() == null
                ? FoodProductReviewRiskLevel.MEDIUM : command.riskLevel());
        reviewCase.setSchemaVersion(command.schemaVersion());
        reviewCase.setSubmittedValuesJson(command.submittedValuesJson());
        reviewCase.setFieldConfidenceJson(trimToNull(command.fieldConfidenceJson()));
        reviewCase.setCorrectionSummaryJson(trimToNull(command.correctionSummaryJson()));
        reviewCase.setNutritionBasis(command.nutritionBasis());
        reviewCase.setConsentVersion(trimToNull(command.consentVersion()));
        reviewCase.setTemporaryEvidenceAllowed(command.temporaryEvidenceAllowed());
        reviewCase.setPublicMediaAllowed(command.publicMediaAllowed());
        try {
            return reviewCaseRepository.saveAndFlush(reviewCase);
        } catch (DataIntegrityViolationException duplicate) {
            return reviewCaseRepository.findByIdempotencyKey(command.idempotencyKey())
                    .orElseThrow(() -> duplicate);
        }
    }

    private FoodItemEntity findProduct(
            String barcode,
            MarketRegion marketRegion,
            CatalogPublicationStatus publicationStatus
    ) {
        Specification<FoodItemEntity> spec = (root, query, cb) -> {
            Predicate sameBarcode = cb.equal(root.get("normalizedBarcode"), barcode);
            Predicate samePublication = cb.equal(root.get("publicationStatus"), publicationStatus);
            Predicate sameMarket = cb.equal(root.get("marketRegion"), marketRegion);
            return cb.and(sameBarcode, samePublication, sameMarket);
        };
        return foodItemRepository.findOne(spec).orElse(null);
    }

    private FoodItemEntity createCandidate(
            FoodProductReviewCaseCommand command,
            String normalizedBarcode
    ) {
        FoodItemEntity candidate = new FoodItemEntity();
        candidate.setName(requireText(command.productName(), "Product name is required"));
        candidate.setBrand(trimToNull(command.brand()));
        candidate.setBarcode(normalizedBarcode);
        candidate.setNormalizedBarcode(normalizedBarcode);
        candidate.setSourceKey("review-candidate:" + command.marketRegion() + ":" + normalizedBarcode);
        candidate.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        candidate.setDataSource(command.source() == FoodProductReviewCaseSource.ADMIN_MANUAL
                ? FoodDataSource.ADMIN_IMPORT : FoodDataSource.MANUAL);
        candidate.setVerificationStatus(VerificationStatus.NEEDS_REVIEW);
        candidate.setPublicationStatus(CatalogPublicationStatus.INTERNAL_REVIEW);
        candidate.setImageStatus(ImageStatus.NEEDS_REVIEW);
        candidate.setMarketRegion(command.marketRegion());
        candidate.setNutritionBasis(command.nutritionBasis());
        candidate.setCalories(command.calories());
        candidate.setProtein(command.protein());
        candidate.setFat(command.fat());
        candidate.setCarbs(command.carbs());
        candidate.setFiber(command.fiber());
        candidate.setSugar(command.sugar());
        candidate.setSodium(command.sodium());
        candidate.setIsCustom(false);
        candidate.setUsageCount(0L);
        try {
            return foodItemRepository.saveAndFlush(candidate);
        } catch (DataIntegrityViolationException duplicate) {
            FoodItemEntity existing = findProduct(
                    normalizedBarcode,
                    command.marketRegion(),
                    CatalogPublicationStatus.INTERNAL_REVIEW
            );
            if (existing != null) return existing;
            throw duplicate;
        }
    }

    private void validateCommand(FoodProductReviewCaseCommand command) {
        if (command == null) throw new IllegalArgumentException("Review case command is required");
        requireText(command.idempotencyKey(), "Idempotency key is required");
        if (command.idempotencyKey().length() > 100) {
            throw new IllegalArgumentException("Idempotency key is too long");
        }
        Objects.requireNonNull(command.source(), "Review case source is required");
        Objects.requireNonNull(command.marketRegion(), "Market region is required");
        if (command.schemaVersion() < 1) {
            throw new IllegalArgumentException("Schema version must be positive");
        }
        boundedJson(command.submittedValuesJson(), true, "Submitted values");
        boundedJson(command.fieldConfidenceJson(), false, "Field confidence");
        boundedJson(command.correctionSummaryJson(), false, "Correction summary");
    }

    private String normalizeAndValidateBarcode(FoodProductReviewCaseCommand command) {
        String barcode = FoodProductNormalizationRules.normalizeBarcode(command.barcode());
        if (command.existingFoodItemId() == null && !GtinValidator.isValid(barcode)) {
            throw new IllegalArgumentException("Barcode has an invalid GTIN check digit");
        }
        return barcode;
    }

    private void boundedJson(String value, boolean required, String label) {
        if (required && (value == null || value.isBlank())) {
            throw new IllegalArgumentException(label + " JSON is required");
        }
        if (value != null && value.length() > MAX_JSON_CHARS) {
            throw new IllegalArgumentException(label + " JSON exceeds the maximum size");
        }
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
