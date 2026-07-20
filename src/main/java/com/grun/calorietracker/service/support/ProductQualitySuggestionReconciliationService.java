package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodCanonicalResolutionEntity;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductReviewAuditEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodProductReviewAuditAction;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodCanonicalResolutionRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodProductReviewAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ProductQualitySuggestionReconciliationService {

    private final FoodItemRepository foodItemRepository;
    private final FoodCanonicalResolutionRepository foodCanonicalResolutionRepository;
    private final FoodProductReviewAuditRepository foodProductReviewAuditRepository;
    private final FoodProductQualityIssueTracker qualityIssueTracker;

    public void reconcile(
            FoodItemEntity product,
            String previousCanonicalKey,
            String actor,
            Long suggestionId
    ) {
        String normalizedActor = actor == null || actor.isBlank() ? "system" : actor.trim();
        String rebuiltCanonicalKey = FoodProductCanonicalKeyRules.resolve(product);
        product.setCanonicalFoodKey(rebuiltCanonicalKey);
        product.setQualityValidatedAt(null);
        product.setQualityValidatedBy(null);
        product.setQualityValidationSource(null);
        product.setQualityValidationNotes(
                "Quality validation reset after accepted suggestion " + suggestionId + "; revalidation required."
        );
        FoodProductQualityRules.updateQualityAndReviewPriority(product);
        foodItemRepository.save(product);
        qualityIssueTracker.syncReviewIssues(product, normalizedActor);
        reconcileCanonicalResolutions(product, previousCanonicalKey, rebuiltCanonicalKey, normalizedActor, suggestionId);
    }

    private void reconcileCanonicalResolutions(
            FoodItemEntity product,
            String previousCanonicalKey,
            String rebuiltCanonicalKey,
            String actor,
            Long suggestionId
    ) {
        Set<String> affectedKeys = new LinkedHashSet<>();
        if (previousCanonicalKey != null && !previousCanonicalKey.isBlank()) {
            affectedKeys.add(previousCanonicalKey);
        }
        if (rebuiltCanonicalKey != null && !rebuiltCanonicalKey.isBlank()) {
            affectedKeys.add(rebuiltCanonicalKey);
        }
        for (String key : affectedKeys) {
            foodCanonicalResolutionRepository.findById(key)
                    .filter(resolution -> isResolutionStale(resolution, key))
                    .ifPresent(resolution -> clearStaleResolution(resolution, product, actor, suggestionId));
        }
    }

    private boolean isResolutionStale(FoodCanonicalResolutionEntity resolution, String key) {
        FoodItemEntity primary = resolution.getPrimaryFoodItem();
        if (primary == null
                || primary.getId() == null
                || !Objects.equals(key, primary.getCanonicalFoodKey())
                || primary.getCatalogType() != FoodCatalogType.GENERIC_INGREDIENT
                || primary.getVerificationStatus() == VerificationStatus.REJECTED
                || FoodProductQualityRules.hasCriticalIssue(primary)) {
            return true;
        }
        List<FoodItemEntity> candidates = foodItemRepository.findByCanonicalFoodKeyIn(
                List.of(key),
                Sort.by(Sort.Order.asc("id"))
        );
        return candidates.stream()
                .filter(candidate -> candidate.getCatalogType() == FoodCatalogType.GENERIC_INGREDIENT)
                .map(FoodItemEntity::getId)
                .filter(Objects::nonNull)
                .distinct()
                .count() < 2;
    }

    private void clearStaleResolution(
            FoodCanonicalResolutionEntity resolution,
            FoodItemEntity changedProduct,
            String actor,
            Long suggestionId
    ) {
        FoodItemEntity previousPrimary = resolution.getPrimaryFoodItem();
        foodCanonicalResolutionRepository.delete(resolution);

        FoodProductReviewAuditEntity audit = new FoodProductReviewAuditEntity();
        audit.setFoodItem(previousPrimary == null ? changedProduct : previousPrimary);
        audit.setReviewedBy(actor);
        audit.setActionType(FoodProductReviewAuditAction.CANONICAL_PRIMARY_CHANGE);
        audit.setFieldName("canonicalPrimaryProductId");
        audit.setOldValue(previousPrimary == null ? null : String.valueOf(previousPrimary.getId()));
        audit.setNewValue(null);
        audit.setNote(
                "Stale canonical resolution cleared after accepted suggestion " + suggestionId
                        + "; canonicalFoodKey=" + resolution.getCanonicalFoodKey()
        );
        audit.setCreatedAt(LocalDateTime.now());
        foodProductReviewAuditRepository.save(audit);
    }
}
