package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.CatalogPublicationStatus;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.service.impl.CatalogPublicationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CatalogPublicationServiceImplTest {

    @Mock
    private FoodItemRepository foodItemRepository;
    @Mock
    private AdminAuditService adminAuditService;
    @InjectMocks
    private CatalogPublicationServiceImpl service;

    @Test
    void publishRejectsMissingAndNonFiniteCoreNutritionWithoutWrites() {
        for (Double invalid : new Double[]{null, Double.NaN, Double.POSITIVE_INFINITY, -1.0, 101.0}) {
            FoodItemEntity product = candidate();
            product.setProtein(invalid);
            assertThrows(IllegalArgumentException.class,
                    () -> service.publishNew(product, "owner@grun.local", "Review", null));
            assertEquals(CatalogPublicationStatus.INTERNAL_REVIEW, product.getPublicationStatus());
        }
        FoodItemEntity product = candidate();
        product.setCalories(Double.NaN);
        assertThrows(IllegalArgumentException.class,
                () -> service.publishNew(product, "owner@grun.local", "Review", null));
        org.mockito.Mockito.verifyNoInteractions(adminAuditService);
        org.mockito.Mockito.verifyNoInteractions(foodItemRepository);
    }

    @Test
    void publishAppliesGuardPersistsAndAudits() {
        FoodItemEntity product = candidate();
        product.setId(7L);
        when(foodItemRepository.findById(7L)).thenReturn(Optional.of(product));
        when(foodItemRepository.save(product)).thenReturn(product);

        FoodItemEntity result = service.publish(7L, "owner@grun.local", "Review approved", "cid-1");

        assertEquals(CatalogPublicationStatus.PUBLISHED, result.getPublicationStatus());
        verify(adminAuditService).record(
                "owner@grun.local",
                AdminAuditActionType.CATALOG_REVIEW_ASSIGNMENT,
                AdminAuditTargetType.CATALOG_REVIEW_ITEM,
                "7",
                java.util.Map.of("publicationStatus", "INTERNAL_REVIEW"),
                java.util.Map.of("publicationStatus", "PUBLISHED", "reason", "Review approved"),
                "cid-1"
        );
    }

    @Test
    void publishRejectsPrivateUserProduct() {
        FoodItemEntity product = candidate();
        product.setId(8L);
        product.setIsCustom(true);
        product.setPublicationStatus(CatalogPublicationStatus.PRIVATE_USER);
        when(foodItemRepository.findById(8L)).thenReturn(Optional.of(product));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.publish(8L, "owner@grun.local", "Invalid attempt", null)
        );
    }

    @Test
    void publishRejectsProductThatIsNotVerified() {
        FoodItemEntity product = candidate();
        product.setId(9L);
        product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        when(foodItemRepository.findById(9L)).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class,
                () -> service.publish(9L, "owner@grun.local", "Not reviewed", null));

        verify(foodItemRepository, never()).save(any());
    }

    @Test
    void publishBatchValidatesEveryProductBeforeAnyWrite() {
        FoodItemEntity valid = candidate();
        valid.setId(10L);
        FoodItemEntity invalid = candidate();
        invalid.setId(11L);
        invalid.setCalories(null);
        when(foodItemRepository.findAllById(List.of(10L, 11L))).thenReturn(List.of(valid, invalid));

        assertThrows(IllegalArgumentException.class,
                () -> service.publishBatch(List.of(10L, 11L), "owner@grun.local", "Batch review", null));

        assertEquals(CatalogPublicationStatus.INTERNAL_REVIEW, valid.getPublicationStatus());
        verify(foodItemRepository, never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(adminAuditService);
    }

    @Test
    void publishBatchPublishesInRequestedOrder() {
        FoodItemEntity first = candidate();
        first.setId(10L);
        FoodItemEntity second = candidate();
        second.setId(11L);
        when(foodItemRepository.findAllById(List.of(11L, 10L))).thenReturn(List.of(first, second));
        when(foodItemRepository.save(any(FoodItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<FoodItemEntity> result = service.publishBatch(
                List.of(11L, 10L), "owner@grun.local", "Batch review", "batch-1");

        assertEquals(List.of(11L, 10L), result.stream().map(FoodItemEntity::getId).toList());
        result.forEach(product -> assertEquals(CatalogPublicationStatus.PUBLISHED, product.getPublicationStatus()));
        verify(foodItemRepository, org.mockito.Mockito.times(2)).save(any(FoodItemEntity.class));
    }

    @Test
    void verifyAndPublishBatchPromotesRawImportedProductsAtomically() {
        FoodItemEntity raw = candidate();
        raw.setId(12L);
        raw.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        FoodItemEntity needsReview = candidate();
        needsReview.setId(13L);
        needsReview.setVerificationStatus(VerificationStatus.NEEDS_REVIEW);
        when(foodItemRepository.findAllById(List.of(12L, 13L))).thenReturn(List.of(raw, needsReview));
        when(foodItemRepository.save(any(FoodItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<FoodItemEntity> result = service.verifyAndPublishBatch(
                List.of(12L, 13L), "owner@grun.local", "Reviewed import batch", "batch-verify-1");

        result.forEach(product -> {
            assertEquals(VerificationStatus.VERIFIED, product.getVerificationStatus());
            assertEquals(CatalogPublicationStatus.PUBLISHED, product.getPublicationStatus());
        });
        verify(foodItemRepository, org.mockito.Mockito.times(2)).save(any(FoodItemEntity.class));
    }

    @Test
    void verifyAndPublishBatchPreflightsEveryProductBeforeMutation() {
        FoodItemEntity valid = candidate();
        valid.setId(14L);
        valid.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        FoodItemEntity invalid = candidate();
        invalid.setId(15L);
        invalid.setVerificationStatus(VerificationStatus.NEEDS_REVIEW);
        invalid.setCalories(null);
        when(foodItemRepository.findAllById(List.of(14L, 15L))).thenReturn(List.of(valid, invalid));

        assertThrows(IllegalArgumentException.class,
                () -> service.verifyAndPublishBatch(List.of(14L, 15L), "owner@grun.local", "Batch review", null));

        assertEquals(VerificationStatus.RAW_IMPORTED, valid.getVerificationStatus());
        assertEquals(CatalogPublicationStatus.INTERNAL_REVIEW, valid.getPublicationStatus());
        verify(foodItemRepository, never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(adminAuditService);
    }

    @Test
    void verifyAndPublishBatchReportsEveryInvalidProductAndField() {
        FoodItemEntity missingReference = candidate();
        missingReference.setId(21L);
        missingReference.setName("Missing reference");
        missingReference.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        missingReference.setNutritionReferenceUnit(null);

        FoodItemEntity invalidNutrition = candidate();
        invalidNutrition.setId(22L);
        invalidNutrition.setName("Invalid nutrition");
        invalidNutrition.setVerificationStatus(VerificationStatus.NEEDS_REVIEW);
        invalidNutrition.setCalories(null);
        invalidNutrition.setSugar(12.0);
        invalidNutrition.setCarbs(10.0);
        when(foodItemRepository.findAllById(List.of(21L, 22L)))
                .thenReturn(List.of(missingReference, invalidNutrition));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.verifyAndPublishBatch(
                        List.of(21L, 22L), "owner@grun.local", "Batch review", "cid-fields"));

        assertTrue(error.getMessage().contains("Product #21 (Missing reference): nutritionReferenceUnit: required"));
        assertTrue(error.getMessage().contains("Product #22 (Invalid nutrition): calories: required"));
        assertTrue(error.getMessage().contains("sugar: cannot exceed carbs"));
        verify(foodItemRepository, never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(adminAuditService);
    }

    private FoodItemEntity candidate() {
        FoodItemEntity product = new FoodItemEntity();
        product.setName("Candidate");
        product.setCalories(120.0);
        product.setProtein(3.0);
        product.setFat(4.0);
        product.setCarbs(18.0);
        product.setNutritionReferenceUnit(com.grun.calorietracker.enums.FoodNutritionReferenceUnit.PER_100G);
        product.setVerificationStatus(VerificationStatus.VERIFIED);
        product.setPublicationStatus(CatalogPublicationStatus.INTERNAL_REVIEW);
        product.setIsCustom(false);
        return product;
    }
}
