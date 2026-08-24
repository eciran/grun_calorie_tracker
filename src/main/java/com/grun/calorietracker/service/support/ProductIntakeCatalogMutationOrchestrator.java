package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductReviewAuditEntity;
import com.grun.calorietracker.enums.FoodProductReviewAuditAction;
import com.grun.calorietracker.repository.FoodProductReviewAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ProductIntakeCatalogMutationOrchestrator {
    private final ProductQualitySuggestionReconciliationService reconciliationService;
    private final FoodProductReviewAuditRepository auditRepository;

    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"}, allEntries = true)
    public void reconcileAndAudit(FoodItemEntity product, String previousCanonicalKey, String actor,
                                  Long reviewCaseId, Map<String, Object> oldValues, Map<String, Object> newValues) {
        List<FoodProductReviewAuditEntity> audits = new ArrayList<>();
        newValues.forEach((field, newValue) -> {
            Object oldValue = oldValues.get(field);
            if (!Objects.equals(oldValue, newValue)) {
                FoodProductReviewAuditEntity audit = new FoodProductReviewAuditEntity();
                audit.setFoodItem(product);
                audit.setReviewedBy(actor);
                audit.setActionType(FoodProductReviewAuditAction.REVIEW_UPDATE);
                audit.setFieldName(field);
                audit.setOldValue(stringValue(oldValue));
                audit.setNewValue(stringValue(newValue));
                audit.setNote("Applied from product intake review case " + reviewCaseId);
                audits.add(audit);
            }
        });
        if (!audits.isEmpty()) auditRepository.saveAll(audits);
        reconciliationService.reconcile(product, previousCanonicalKey, actor, reviewCaseId);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}