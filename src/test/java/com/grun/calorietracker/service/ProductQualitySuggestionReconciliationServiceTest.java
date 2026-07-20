package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodCanonicalResolutionEntity;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductReviewAuditEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.ProductQualitySuggestionSource;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodCanonicalResolutionRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodProductReviewAuditRepository;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import com.grun.calorietracker.service.support.ProductQualitySuggestionReconciliationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductQualitySuggestionReconciliationServiceTest {

    @Mock
    private FoodItemRepository foodItemRepository;

    @Mock
    private FoodCanonicalResolutionRepository foodCanonicalResolutionRepository;

    @Mock
    private FoodProductReviewAuditRepository foodProductReviewAuditRepository;

    @Mock
    private FoodProductQualityIssueTracker qualityIssueTracker;

    @InjectMocks
    private ProductQualitySuggestionReconciliationService service;

    @Test
    void reconcile_recalculatesQualityAndInvalidatesPreviousAiValidation() {
        FoodItemEntity product = product();
        product.setQualityValidatedAt(LocalDateTime.now());
        product.setQualityValidatedBy("ai-provider");
        product.setQualityValidationSource(ProductQualitySuggestionSource.AI_ASSISTED);
        product.setQualityValidationNotes("previous validation");
        product.setQualityScore(1);
        product.setConfidenceScore(1);

        service.reconcile(product, null, "admin@test.com", 42L);

        assertNull(product.getQualityValidatedAt());
        assertNull(product.getQualityValidatedBy());
        assertNull(product.getQualityValidationSource());
        assertTrue(product.getQualityValidationNotes().contains("42"));
        assertTrue(product.getQualityScore() > 1);
        assertTrue(product.getConfidenceScore() > 1);
        verify(foodItemRepository).save(product);
        verify(qualityIssueTracker).syncReviewIssues(product, "admin@test.com");
    }

    @Test
    void reconcile_rebuildsCanonicalKeyAndClearsStalePrimaryResolution() {
        FoodItemEntity product = product();
        product.setCatalogType(FoodCatalogType.GENERIC_INGREDIENT);
        product.setDisplayName("Cooked White Rice");
        product.setPreparationState(FoodPreparationState.COOKED);
        product.setMarketRegion(MarketRegion.GLOBAL);
        product.setCanonicalFoodKey("GLOBAL:GENERIC_INGREDIENT:RAW:white_rice");

        FoodCanonicalResolutionEntity staleResolution = new FoodCanonicalResolutionEntity();
        staleResolution.setCanonicalFoodKey(product.getCanonicalFoodKey());
        staleResolution.setPrimaryFoodItem(product);
        when(foodCanonicalResolutionRepository.findById("GLOBAL:GENERIC_INGREDIENT:RAW:white_rice"))
                .thenReturn(Optional.of(staleResolution));
        when(foodCanonicalResolutionRepository.findById("GLOBAL:GENERIC_INGREDIENT:COOKED:white_rice"))
                .thenReturn(Optional.empty());

        service.reconcile(
                product,
                "GLOBAL:GENERIC_INGREDIENT:RAW:white_rice",
                "admin@test.com",
                43L
        );

        assertEquals("GLOBAL:GENERIC_INGREDIENT:COOKED:white_rice", product.getCanonicalFoodKey());
        verify(foodCanonicalResolutionRepository).delete(staleResolution);
        ArgumentCaptor<FoodProductReviewAuditEntity> audit =
                ArgumentCaptor.forClass(FoodProductReviewAuditEntity.class);
        verify(foodProductReviewAuditRepository).save(audit.capture());
        assertTrue(audit.getValue().getNote().contains("Stale canonical resolution cleared"));
    }

    private FoodItemEntity product() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(1L);
        product.setName("White Rice");
        product.setDisplayName("White Rice");
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        product.setCalories(130.0);
        product.setProtein(2.7);
        product.setFat(0.3);
        product.setCarbs(28.0);
        product.setServingSizeGrams(100.0);
        product.setMarketRegion(MarketRegion.GLOBAL);
        product.setUsageCount(0L);
        return product;
    }
}
