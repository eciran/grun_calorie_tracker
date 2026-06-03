package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductQualityIssueEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodProductQualityIssue;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.repository.FoodProductQualityIssueRepository;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoodProductQualityIssueTrackerTest {

    private final FoodProductQualityIssueRepository repository = mock(FoodProductQualityIssueRepository.class);
    private final FoodProductQualityIssueTracker tracker = new FoodProductQualityIssueTracker(repository);

    @Test
    void syncImportIssues_createsCurrentIssuesAndResolvesFixedOnes() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(10L);
        product.setName("Imported Product");
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        product.setNormalizedBarcode("NOTNUMERIC");
        product.setQualityScore(45);
        product.setMarketRegion(MarketRegion.GLOBAL);

        FoodProductQualityIssueEntity fixedIssue = new FoodProductQualityIssueEntity();
        fixedIssue.setFoodItem(product);
        fixedIssue.setIssueType(FoodProductQualityIssue.MISSING_REGION);
        fixedIssue.setResolved(false);

        when(repository.findByFoodItemIdAndResolvedFalse(eq(10L))).thenReturn(List.of(fixedIssue));

        tracker.syncImportIssues(product, false, true, "admin@grun.app");

        ArgumentCaptor<List<FoodProductQualityIssueEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        List<FoodProductQualityIssueEntity> savedIssues = captor.getValue();
        assertTrue(savedIssues.stream().anyMatch(issue -> issue.getIssueType() == FoodProductQualityIssue.LOW_QUALITY));
        assertTrue(savedIssues.stream().anyMatch(issue -> issue.getIssueType() == FoodProductQualityIssue.INVALID_BARCODE_FORMAT));
        assertTrue(savedIssues.stream().anyMatch(issue -> issue.getIssueType() == FoodProductQualityIssue.UNSUPPORTED_REGION));

        FoodProductQualityIssueEntity resolvedMissingRegion = savedIssues.stream()
                .filter(issue -> issue.getIssueType() == FoodProductQualityIssue.MISSING_REGION)
                .findFirst()
                .orElseThrow();
        assertTrue(resolvedMissingRegion.getResolved());
        assertEquals("admin@grun.app", resolvedMissingRegion.getResolvedBy());
        assertNotNull(resolvedMissingRegion.getResolvedAt());

        FoodProductQualityIssueEntity invalidBarcode = savedIssues.stream()
                .filter(issue -> issue.getIssueType() == FoodProductQualityIssue.INVALID_BARCODE_FORMAT)
                .findFirst()
                .orElseThrow();
        assertFalse(invalidBarcode.getResolved());
        assertTrue(invalidBarcode.getReason().contains("between 6 and 18 digits"));
    }
}
