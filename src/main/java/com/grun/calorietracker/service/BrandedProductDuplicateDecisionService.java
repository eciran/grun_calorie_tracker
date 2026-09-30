package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.BrandedProductDuplicateDecisionDto;
import com.grun.calorietracker.dto.BrandedProductDuplicateDecisionRequestDto;
import com.grun.calorietracker.dto.BrandedProductDuplicateGroupDto;
import com.grun.calorietracker.entity.FoodBrandedDuplicateDecisionAuditEntity;
import com.grun.calorietracker.entity.FoodBrandedDuplicateDecisionEntity;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.BrandedDuplicateDecision;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodBrandedDuplicateDecisionAuditRepository;
import com.grun.calorietracker.repository.FoodBrandedDuplicateDecisionRepository;
import com.grun.calorietracker.repository.FoodBrandedDuplicateSearchCollapseRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class BrandedProductDuplicateDecisionService {
    private final BrandedProductDuplicateAnalysisService analysisService;
    private final FoodBrandedDuplicateDecisionRepository decisionRepository;
    private final FoodBrandedDuplicateDecisionAuditRepository auditRepository;
    private final FoodItemRepository foodItemRepository;
    private final FoodBrandedDuplicateSearchCollapseRepository collapseRepository;

    public BrandedProductDuplicateDecisionService(
            BrandedProductDuplicateAnalysisService analysisService,
            FoodBrandedDuplicateDecisionRepository decisionRepository,
            FoodBrandedDuplicateDecisionAuditRepository auditRepository,
            FoodItemRepository foodItemRepository,
            FoodBrandedDuplicateSearchCollapseRepository collapseRepository
    ) {
        this.analysisService = analysisService;
        this.decisionRepository = decisionRepository;
        this.auditRepository = auditRepository;
        this.foodItemRepository = foodItemRepository;
        this.collapseRepository = collapseRepository;
    }

    @Transactional(readOnly = true)
    public BrandedProductDuplicateDecisionDto get(String brandKey, String nameKey) {
        return decisionRepository.findByBrandKeyAndNameKey(normalizeKey(brandKey), normalizeKey(nameKey))
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Branded duplicate decision was not found."));
    }

    @Transactional
    public BrandedProductDuplicateDecisionDto decide(
            BrandedProductDuplicateDecisionRequestDto request,
            String reviewedBy
    ) {
        String brandKey = normalizeKey(request.brandKey());
        String nameKey = normalizeKey(request.nameKey());
        BrandedProductDuplicateGroupDto currentGroup = analysisService.getExactGroup(brandKey, nameKey);
        if (!Objects.equals(currentGroup.candidateFingerprint(), request.candidateFingerprint())) {
            throw new IllegalArgumentException(
                    "Candidate group changed after it was loaded. Refresh the group before recording a decision."
            );
        }

        FoodItemEntity survivor = validateSurvivor(request, currentGroup);
        FoodBrandedDuplicateDecisionEntity entity = decisionRepository
                .findByBrandKeyAndNameKey(brandKey, nameKey)
                .orElseGet(FoodBrandedDuplicateDecisionEntity::new);
        if (entity.getId() != null && collapseRepository.existsByDecisionIdAndActiveTrue(entity.getId())) {
            throw new IllegalStateException("Revert the active search collapse before changing this decision.");
        }
        BrandedDuplicateDecision previousDecision = entity.getDecision();
        Long previousSurvivorId = entity.getSurvivorFoodItem() == null
                ? null : entity.getSurvivorFoodItem().getId();

        entity.setBrandKey(brandKey);
        entity.setNameKey(nameKey);
        entity.setDecision(request.decision());
        entity.setSurvivorFoodItem(survivor);
        entity.setCandidateFingerprint(currentGroup.candidateFingerprint());
        entity.setReason(request.reason().trim());
        entity.setReviewedBy(reviewer(reviewedBy));
        FoodBrandedDuplicateDecisionEntity saved = decisionRepository.saveAndFlush(entity);

        auditRepository.save(buildAudit(
                saved, "SET", previousDecision, request.decision(), previousSurvivorId,
                survivor == null ? null : survivor.getId(), request.reason(), reviewedBy
        ));
        return toDto(saved);
    }

    @Transactional
    public void clear(String brandKey, String nameKey, String reason, String reviewedBy) {
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException("A clear reason of at least 10 characters is required.");
        }
        FoodBrandedDuplicateDecisionEntity entity = decisionRepository
                .findByBrandKeyAndNameKey(normalizeKey(brandKey), normalizeKey(nameKey))
                .orElseThrow(() -> new ResourceNotFoundException("Branded duplicate decision was not found."));
        if (collapseRepository.existsByDecisionIdAndActiveTrue(entity.getId())) {
            throw new IllegalStateException("Revert the active search collapse before clearing this decision.");
        }
        auditRepository.save(buildAudit(
                entity, "CLEAR", entity.getDecision(), null,
                entity.getSurvivorFoodItem() == null ? null : entity.getSurvivorFoodItem().getId(),
                null, reason, reviewedBy
        ));
        decisionRepository.delete(entity);
    }

    private FoodItemEntity validateSurvivor(
            BrandedProductDuplicateDecisionRequestDto request,
            BrandedProductDuplicateGroupDto currentGroup
    ) {
        if (request.decision() != BrandedDuplicateDecision.SURVIVOR_SELECTED) {
            if (request.survivorProductId() != null) {
                throw new IllegalArgumentException("Survivor product is only valid for SURVIVOR_SELECTED.");
            }
            return null;
        }
        if (request.survivorProductId() == null) {
            throw new IllegalArgumentException("Survivor product is required for SURVIVOR_SELECTED.");
        }
        boolean member = currentGroup.candidates().stream()
                .anyMatch(candidate -> Objects.equals(candidate.productId(), request.survivorProductId()));
        if (!member) {
            throw new IllegalArgumentException("Survivor product must belong to the current candidate group.");
        }
        return foodItemRepository.findById(request.survivorProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Survivor product was not found."));
    }

    private FoodBrandedDuplicateDecisionAuditEntity buildAudit(
            FoodBrandedDuplicateDecisionEntity entity,
            String action,
            BrandedDuplicateDecision previousDecision,
            BrandedDuplicateDecision newDecision,
            Long previousSurvivorId,
            Long newSurvivorId,
            String reason,
            String reviewedBy
    ) {
        FoodBrandedDuplicateDecisionAuditEntity audit = new FoodBrandedDuplicateDecisionAuditEntity();
        audit.setBrandKey(entity.getBrandKey());
        audit.setNameKey(entity.getNameKey());
        audit.setAction(action);
        audit.setPreviousDecision(previousDecision);
        audit.setNewDecision(newDecision);
        audit.setPreviousSurvivorFoodItemId(previousSurvivorId);
        audit.setNewSurvivorFoodItemId(newSurvivorId);
        audit.setCandidateFingerprint(entity.getCandidateFingerprint());
        audit.setReason(reason.trim());
        audit.setReviewedBy(reviewer(reviewedBy));
        return audit;
    }

    private BrandedProductDuplicateDecisionDto toDto(FoodBrandedDuplicateDecisionEntity entity) {
        return new BrandedProductDuplicateDecisionDto(
                entity.getId(), entity.getBrandKey(), entity.getNameKey(), entity.getDecision(),
                entity.getSurvivorFoodItem() == null ? null : entity.getSurvivorFoodItem().getId(),
                entity.getCandidateFingerprint(), entity.getReason(), entity.getReviewedBy(),
                entity.getReviewedAt() == null ? null : entity.getReviewedAt().toString(), entity.getVersion()
        );
    }

    private String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Brand and name keys are required.");
        }
        return value.trim();
    }

    private String reviewer(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
