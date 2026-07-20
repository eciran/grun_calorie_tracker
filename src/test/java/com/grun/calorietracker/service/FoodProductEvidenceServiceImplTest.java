package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductSourceEvidenceEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodEvidenceBasis;
import com.grun.calorietracker.enums.FoodEvidenceComparisonState;
import com.grun.calorietracker.enums.FoodEvidenceField;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodProductSourceEvidenceRepository;
import com.grun.calorietracker.service.impl.FoodProductEvidenceServiceImpl;
import com.grun.calorietracker.service.support.FoodProductEvidenceBulkWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodProductEvidenceServiceImplTest {

    @Mock
    private FoodProductSourceEvidenceRepository evidenceRepository;

    @Mock
    private FoodItemRepository foodItemRepository;

    @Mock
    private FoodProductEvidenceBulkWriter evidenceBulkWriter;

    @InjectMocks
    private FoodProductEvidenceServiceImpl service;

    @Test
    void recordImportEvidence_isIdempotentForExistingFingerprints() {
        FoodItemEntity product = product(1L, FoodDataSource.USDA_FOODDATA, FoodCatalogType.GENERIC_INGREDIENT);
        product.setCalories(130.0);
        product.setProtein(2.7);
        product.setFat(-1.0);
        when(evidenceRepository.findExistingFingerprints(any())).thenAnswer(invocation -> {
            java.util.Collection<String> fingerprints = invocation.getArgument(0);
            return List.of(fingerprints.iterator().next());
        });

        int created = service.recordImportEvidence(
                List.of(product), FoodEvidenceBasis.PER_100_G,
                LocalDateTime.of(2026, 7, 14, 10, 0), "USDA_FOODDATA", "admin@grun.local");

        assertEquals(1, created);
        ArgumentCaptor<List<FoodProductSourceEvidenceEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(evidenceRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertTrue(captor.getValue().stream().allMatch(value -> value.getFingerprint().length() == 64));
        assertTrue(captor.getValue().stream().allMatch(value -> value.getNumericValue() >= 0.0));
        assertTrue(captor.getValue().stream()
                .allMatch(value -> "admin@grun.local".equals(value.getReviewerIdentity())));
    }

    @Test
    void recordImportEvidence_largePostgresBatchReliesOnConflictSafeInsert() {
        List<FoodItemEntity> products = new ArrayList<>();
        for (long id = 1; id <= 1000; id++) {
            FoodItemEntity product = product(id, FoodDataSource.OPEN_FOOD_FACTS, FoodCatalogType.BRANDED_PRODUCT);
            product.setCalories(100.0 + id);
            products.add(product);
        }
        when(evidenceBulkWriter.supportsConflictSafeBulkInsert()).thenReturn(true);
        when(evidenceBulkWriter.insertIgnoringFingerprintConflicts(any())).thenReturn(1000);

        int created = service.recordImportEvidence(
                products, FoodEvidenceBasis.PER_100_G,
                LocalDateTime.of(2026, 7, 15, 10, 0), "OPEN_FOOD_FACTS", null);

        assertEquals(1000, created);
        verify(evidenceBulkWriter).insertIgnoringFingerprintConflicts(any());
        verifyNoInteractions(evidenceRepository);
    }
    @Test
    void compare_matchingProviders_staysWithinToleranceAndPrefersUsdaForGeneric() {
        FoodItemEntity usda = product(1L, FoodDataSource.USDA_FOODDATA, FoodCatalogType.GENERIC_INGREDIENT);
        FoodItemEntity off = product(2L, FoodDataSource.OPEN_FOOD_FACTS, FoodCatalogType.GENERIC_INGREDIENT);
        FoodProductSourceEvidenceEntity usdaEvidence = evidence(10L, usda, 100.0, LocalDateTime.now());
        FoodProductSourceEvidenceEntity offEvidence = evidence(11L, off, 106.0, LocalDateTime.now());
        when(evidenceRepository.findByFoodItemIdInOrderByObservedAtDescIdDesc(List.of(1L, 2L)))
                .thenReturn(List.of(usdaEvidence, offEvidence));

        var result = service.compare(List.of(usda, off), FoodEvidenceField.CALORIES, FoodEvidenceBasis.PER_100_G);

        assertEquals(FoodEvidenceComparisonState.MATCH, result.getState());
        assertEquals(10L, result.getPreferredEvidenceId());
        assertEquals(6.0, result.getMaximumDifference());
    }

    @Test
    void compare_conflictingProviders_blocksExactReplacement() {
        FoodItemEntity usda = product(1L, FoodDataSource.USDA_FOODDATA, FoodCatalogType.GENERIC_INGREDIENT);
        FoodItemEntity off = product(2L, FoodDataSource.OPEN_FOOD_FACTS, FoodCatalogType.GENERIC_INGREDIENT);
        when(evidenceRepository.findByFoodItemIdInOrderByObservedAtDescIdDesc(List.of(1L, 2L)))
                .thenReturn(List.of(
                        evidence(10L, usda, 100.0, LocalDateTime.now()),
                        evidence(11L, off, 145.0, LocalDateTime.now())
                ));

        var result = service.compare(List.of(usda, off), FoodEvidenceField.CALORIES, FoodEvidenceBasis.PER_100_G);

        assertEquals(FoodEvidenceComparisonState.CONFLICT, result.getState());
        assertTrue(result.getReason().contains("exact AI replacement is not allowed"));
    }

    @Test
    void compare_missingAndStaleEvidence_areExplicitStates() {
        FoodItemEntity product = product(1L, FoodDataSource.OPEN_FOOD_FACTS, FoodCatalogType.BRANDED_PRODUCT);
        when(evidenceRepository.findByFoodItemIdInOrderByObservedAtDescIdDesc(List.of(1L)))
                .thenReturn(List.of());

        var missing = service.compare(List.of(product), FoodEvidenceField.PROTEIN, FoodEvidenceBasis.PER_100_G);
        assertEquals(FoodEvidenceComparisonState.MISSING, missing.getState());

        when(evidenceRepository.findByFoodItemIdInOrderByObservedAtDescIdDesc(List.of(1L)))
                .thenReturn(List.of(evidence(20L, product, 4.0, LocalDateTime.now().minusDays(181))));
        var stale = service.compare(List.of(product), FoodEvidenceField.CALORIES, FoodEvidenceBasis.PER_100_G);
        assertEquals(FoodEvidenceComparisonState.STALE, stale.getState());
    }

    @Test
    void compare_doesNotMixIncompatibleNutritionBases() {
        FoodItemEntity usda = product(1L, FoodDataSource.USDA_FOODDATA, FoodCatalogType.GENERIC_INGREDIENT);
        FoodItemEntity off = product(2L, FoodDataSource.OPEN_FOOD_FACTS, FoodCatalogType.GENERIC_INGREDIENT);
        FoodProductSourceEvidenceEntity per100g = evidence(10L, usda, 100.0, LocalDateTime.now());
        FoodProductSourceEvidenceEntity per100ml = evidence(11L, off, 100.0, LocalDateTime.now());
        per100ml.setBasis(FoodEvidenceBasis.PER_100_ML);
        when(evidenceRepository.findByFoodItemIdInOrderByObservedAtDescIdDesc(List.of(1L, 2L)))
                .thenReturn(List.of(per100g, per100ml));

        var grams = service.compare(
                List.of(usda, off),
                FoodEvidenceField.CALORIES,
                FoodEvidenceBasis.PER_100_G
        );

        assertEquals(FoodEvidenceComparisonState.SINGLE_SOURCE, grams.getState());
        assertEquals(List.of(10L), grams.getEvidenceIds());
    }

    private FoodItemEntity product(Long id, FoodDataSource source, FoodCatalogType catalogType) {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(id);
        product.setSourceKey(source + ":" + id);
        product.setDataSource(source);
        product.setCatalogType(catalogType);
        return product;
    }

    private FoodProductSourceEvidenceEntity evidence(
            Long id,
            FoodItemEntity product,
            Double value,
            LocalDateTime observedAt
    ) {
        FoodProductSourceEvidenceEntity evidence = new FoodProductSourceEvidenceEntity();
        evidence.setId(id);
        evidence.setFoodItem(product);
        evidence.setProvider(product.getDataSource());
        evidence.setExternalId(product.getSourceKey());
        evidence.setFieldName(FoodEvidenceField.CALORIES);
        evidence.setNumericValue(value);
        evidence.setBasis(FoodEvidenceBasis.PER_100_G);
        evidence.setConfidenceScore(product.getDataSource() == FoodDataSource.USDA_FOODDATA ? 95 : 75);
        evidence.setObservedAt(observedAt);
        evidence.setFingerprint("fingerprint-" + id);
        return evidence;
    }
}