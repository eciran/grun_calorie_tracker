package com.grun.calorietracker.service.support;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductReviewCaseAssetEntity;
import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.entity.FoodProductSourceEvidenceEntity;
import com.grun.calorietracker.enums.FoodEvidenceField;
import com.grun.calorietracker.enums.FoodProductAssetDeletionState;
import com.grun.calorietracker.enums.FoodProductAssetUploadState;
import com.grun.calorietracker.enums.FoodProductReviewAssetType;
import com.grun.calorietracker.enums.FoodProductReviewCaseSource;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import com.grun.calorietracker.repository.FoodProductSourceEvidenceRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductIntakeApplyGateTest {
    private final FoodProductReviewCaseAssetRepository assets = mock(FoodProductReviewCaseAssetRepository.class);
    private final FoodProductSourceEvidenceRepository evidence = mock(FoodProductSourceEvidenceRepository.class);
    private final FoodProductIntakeMetrics metrics = mock(FoodProductIntakeMetrics.class);
    private final ProductIntakeApplyGate gate = new ProductIntakeApplyGate(assets, evidence, metrics);

    @Test
    void acceptsUserOcrOnlyWithCurrentEvidenceAndValidNutrition() {
        FoodProductReviewCaseEntity reviewCase = reviewCase();
        when(evidence.findByFoodItemIdOrderByObservedAtDescIdDesc(9L)).thenReturn(List.of(
                evidence(reviewCase, FoodEvidenceField.CALORIES), evidence(reviewCase, FoodEvidenceField.PROTEIN)));
        when(assets.findAllByReviewCaseIdOrderByAssetTypeAsc(5L)).thenReturn(List.of(
                asset(FoodProductReviewAssetType.FRONT_PACKAGE), asset(FoodProductReviewAssetType.NUTRITION_LABEL)));

        assertDoesNotThrow(() -> gate.requirePublishable(reviewCase, reviewCase.getFoodItem()));
    }

    @Test
    void rejectsExpiredOrMissingUserEvidence() {
        FoodProductReviewCaseEntity reviewCase = reviewCase();
        when(evidence.findByFoodItemIdOrderByObservedAtDescIdDesc(9L)).thenReturn(List.of(
                evidence(reviewCase, FoodEvidenceField.CALORIES), evidence(reviewCase, FoodEvidenceField.CARBS)));
        FoodProductReviewCaseAssetEntity expired = asset(FoodProductReviewAssetType.NUTRITION_LABEL);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(assets.findAllByReviewCaseIdOrderByAssetTypeAsc(5L)).thenReturn(List.of(expired));

        assertThrows(IllegalStateException.class,
                () -> gate.requirePublishable(reviewCase, reviewCase.getFoodItem()));
    }

    @Test
    void rejectsCriticalNutritionEvenWhenEvidenceExists() {
        FoodProductReviewCaseEntity reviewCase = reviewCase();
        reviewCase.getFoodItem().setCalories(1200.0);
        when(evidence.findByFoodItemIdOrderByObservedAtDescIdDesc(9L)).thenReturn(List.of(
                evidence(reviewCase, FoodEvidenceField.CALORIES), evidence(reviewCase, FoodEvidenceField.FAT)));

        assertThrows(IllegalStateException.class,
                () -> gate.requirePublishable(reviewCase, reviewCase.getFoodItem()));
    }

    private FoodProductReviewCaseEntity reviewCase() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(9L);
        product.setName("Valid product");
        product.setCalories(120.0);
        product.setProtein(4.0);
        FoodProductReviewCaseEntity value = new FoodProductReviewCaseEntity();
        value.setId(5L);
        value.setFoodItem(product);
        value.setSource(FoodProductReviewCaseSource.USER_OCR);
        return value;
    }

    private FoodProductSourceEvidenceEntity evidence(FoodProductReviewCaseEntity reviewCase, FoodEvidenceField field) {
        FoodProductSourceEvidenceEntity value = new FoodProductSourceEvidenceEntity();
        value.setFoodItem(reviewCase.getFoodItem());
        value.setExternalId("REVIEW_CASE:" + reviewCase.getId());
        value.setFieldName(field);
        return value;
    }

    private FoodProductReviewCaseAssetEntity asset(FoodProductReviewAssetType type) {
        FoodProductReviewCaseAssetEntity value = new FoodProductReviewCaseAssetEntity();
        value.setAssetType(type);
        value.setUploadState(FoodProductAssetUploadState.VERIFIED);
        value.setDeletionState(FoodProductAssetDeletionState.ACTIVE);
        value.setExpiresAt(LocalDateTime.now().plusDays(1));
        return value;
    }
}
