package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductReviewAuditEntity;
import com.grun.calorietracker.repository.FoodProductReviewAuditRepository;
import com.grun.calorietracker.service.support.ProductIntakeCatalogMutationOrchestrator;
import com.grun.calorietracker.service.support.ProductQualitySuggestionReconciliationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ProductIntakeCatalogMutationOrchestratorTest {
    private final ProductQualitySuggestionReconciliationService reconciliation = mock(ProductQualitySuggestionReconciliationService.class);
    private final FoodProductReviewAuditRepository audits = mock(FoodProductReviewAuditRepository.class);
    private final ProductIntakeCatalogMutationOrchestrator service = new ProductIntakeCatalogMutationOrchestrator(reconciliation, audits);

    @Test
    void recordsOnlyChangedSelectedFieldsAndReconcilesCatalogState() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(11L);
        service.reconcileAndAudit(product, "old-key", "admin@grun.app", 72L,
                Map.of("calories", 100.0, "protein", 4.0),
                Map.of("calories", 220.0, "protein", 4.0));
        @SuppressWarnings("unchecked") var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(audits).saveAll(captor.capture());
        List<FoodProductReviewAuditEntity> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals("calories", saved.get(0).getFieldName());
        assertEquals("100.0", saved.get(0).getOldValue());
        assertEquals("220.0", saved.get(0).getNewValue());
        assertTrue(saved.get(0).getNote().contains("72"));
        verify(reconciliation).reconcile(product, "old-key", "admin@grun.app", 72L);
    }

    @Test
    void reconciliationFailurePropagatesSoOuterTransactionCanRollBack() {
        FoodItemEntity product = new FoodItemEntity();
        doThrow(new IllegalStateException("quality sync failed"))
                .when(reconciliation).reconcile(product, null, "admin@grun.app", 73L);
        var failure = assertThrows(IllegalStateException.class,
                () -> service.reconcileAndAudit(product, null, "admin@grun.app", 73L,
                        Map.of("calories", 100.0), Map.of("calories", 200.0)));
        assertEquals("quality sync failed", failure.getMessage());
        verify(audits).saveAll(anyList());
    }
}