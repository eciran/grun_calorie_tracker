package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.impl.AdminProductIntakeServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminProductIntakeDetailServiceTest {
    private final FoodProductReviewCaseRepository cases = mock(FoodProductReviewCaseRepository.class);
    private final FoodProductReviewCaseAssetRepository assets = mock(FoodProductReviewCaseAssetRepository.class);
    private final AdminProductIntakeServiceImpl service = new AdminProductIntakeServiceImpl(cases,
            mock(UserRepository.class), mock(FoodItemRepository.class), mock(NotificationRepository.class), assets,
            mock(FoodProductReviewCaseService.class), new ObjectMapper(), 24);

    @Test
    void detailComparesSubmittedAndCatalogFieldsAndExposesOnlyDescriptors() {
        FoodItemEntity food = new FoodItemEntity();
        food.setId(5L);
        food.setName("Catalog name");
        food.setBrand("Same brand");
        food.setSourceCategoryTags(new LinkedHashSet<>(List.of("en:sodas", "en:beverages")));
        food.setPublicationStatus(CatalogPublicationStatus.INTERNAL_REVIEW);
        FoodProductReviewCaseEntity reviewCase = baseCase(food);
        reviewCase.setSubmittedValuesJson("{\"productName\":\"User name\",\"brand\":\"Same brand\"}");
        FoodProductReviewCaseAssetEntity asset = evidence(LocalDateTime.now().plusDays(10));
        when(cases.findById(12L)).thenReturn(Optional.of(reviewCase));
        when(assets.findAllByReviewCaseIdOrderByAssetTypeAsc(12L)).thenReturn(List.of(asset));

        var detail = service.detail(12L);

        assertEquals(5L, detail.linkedFoodItemId());
        assertTrue(detail.fieldComparisons().stream().anyMatch(value -> value.field().equals("productName") && !value.equal()));
        assertTrue(detail.fieldComparisons().stream().anyMatch(value -> value.field().equals("brand") && value.equal()));
        assertEquals(Set.of("en:beverages", "en:sodas"), detail.catalogFields().get("sourceCategoryTags"));
        assertNotSame(food.getSourceCategoryTags(), detail.catalogFields().get("sourceCategoryTags"));
        assertTrue(detail.evidence().get(0).available());
        assertFalse(Arrays.stream(detail.evidence().get(0).getClass().getRecordComponents())
                .anyMatch(component -> component.getName().toLowerCase().contains("url")
                        || component.getName().toLowerCase().contains("storage")));
    }

    @Test
    void highRiskExpiredEvidenceProducesWarningsWithoutUrl() {
        FoodProductReviewCaseEntity reviewCase = baseCase(null);
        reviewCase.setRiskLevel(FoodProductReviewRiskLevel.HIGH);
        reviewCase.setSubmittedValuesJson("not-json");
        when(cases.findById(12L)).thenReturn(Optional.of(reviewCase));
        when(assets.findAllByReviewCaseIdOrderByAssetTypeAsc(12L))
                .thenReturn(List.of(evidence(LocalDateTime.now().minusMinutes(1))));
        var detail = service.detail(12L);
        assertTrue(detail.warnings().contains("HIGH_RISK"));
        assertTrue(detail.warnings().contains("SUBMITTED_VALUES_INVALID"));
        assertFalse(detail.evidence().get(0).available());
    }

    @Test
    void detailExposesPersistedOcrStagesWithoutInventingMissingRuns() {
        FoodProductReviewCaseEntity reviewCase = baseCase(null);
        reviewCase.setSubmittedValuesJson("{\"calories\":120}");
        when(cases.findById(12L)).thenReturn(Optional.of(reviewCase));
        when(assets.findAllByReviewCaseIdOrderByAssetTypeAsc(12L)).thenReturn(List.of());
        ProductNutritionOcrShadowRunRepository ocrRuns = mock(ProductNutritionOcrShadowRunRepository.class);
        ProductNutritionOcrShadowRunEntity run = new ProductNutritionOcrShadowRunEntity();
        run.setId(44L); run.setReviewCaseId(12L); run.setCorrelationId("ocr-correlation");
        run.setParserVersion("nutrition-v4"); run.setModel("gemini-fixture"); run.setFallbackInvoked(true);
        run.setV3FieldsJson("{\"calories\":110}"); run.setV4FieldsJson("{\"calories\":120}");
        run.setGeminiFieldsJson("{\"calories\":118}"); run.setUserFieldsJson("{\"calories\":120}");
        run.setV3ExactMatchRate(0d); run.setV4ExactMatchRate(1d); run.setGeminiExactMatchRate(0d);
        run.setV3BasisExact(false); run.setV4BasisExact(true); run.setGeminiBasisExact(true);
        run.setLatencyMs(240L); run.setEstimatedCostUsd(0.002d);
        run.setReconciliationJson("{\"selected\":\"v4\"}"); run.setCreatedAt(LocalDateTime.now());
        when(ocrRuns.findByReviewCaseIdOrderByCreatedAtDescIdDesc(12L)).thenReturn(List.of(run));
        service.setOcrShadowRunRepository(ocrRuns);

        var detail = service.detail(12L);

        assertEquals(1, detail.ocrRuns().size());
        assertEquals("ocr-correlation", detail.ocrRuns().get(0).correlationId());
        assertEquals(120, detail.ocrRuns().get(0).v4Fields().get("calories"));
        assertEquals("v4", detail.ocrRuns().get(0).reconciliation().get("selected"));
    }

    private FoodProductReviewCaseEntity baseCase(FoodItemEntity food) {
        FoodProductReviewCaseEntity value = new FoodProductReviewCaseEntity();
        value.setId(12L);
        value.setSource(FoodProductReviewCaseSource.USER_OCR);
        value.setStatus(FoodProductReviewCaseStatus.SUBMITTED);
        value.setMarketRegion(MarketRegion.GLOBAL);
        value.setResolutionMode(food == null ? FoodProductResolutionMode.NEW_CANDIDATE : FoodProductResolutionMode.UPDATE_EXISTING);
        value.setRiskLevel(FoodProductReviewRiskLevel.MEDIUM);
        value.setFoodItem(food);
        value.setCreatedAt(LocalDateTime.now());
        value.setUpdatedAt(LocalDateTime.now());
        return value;
    }

    private FoodProductReviewCaseAssetEntity evidence(LocalDateTime expiry) {
        FoodProductReviewCaseAssetEntity value = new FoodProductReviewCaseAssetEntity();
        value.setId(9L);
        value.setAssetType(FoodProductReviewAssetType.NUTRITION_LABEL);
        value.setContentType("image/jpeg");
        value.setSizeBytes(1000L);
        value.setUploadState(FoodProductAssetUploadState.VERIFIED);
        value.setDeletionState(FoodProductAssetDeletionState.ACTIVE);
        value.setExpiresAt(expiry);
        return value;
    }
}
