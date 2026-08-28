package com.grun.calorietracker.service;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.entity.FoodProductReviewCaseAssetEntity;
import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.entity.FoodProductUploadSessionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodProductAssetDeletionState;
import com.grun.calorietracker.enums.FoodProductAssetUploadState;
import com.grun.calorietracker.enums.FoodProductReviewAssetType;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import com.grun.calorietracker.service.evidence.FoodProductDirectUploadStorage;
import com.grun.calorietracker.service.impl.ProductNutritionOcrEvidenceResolverImpl;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductNutritionOcrEvidenceResolverImplTest {
    private final FoodProductReviewCaseAssetRepository assets = mock(FoodProductReviewCaseAssetRepository.class);
    private final FoodProductDirectUploadStorage storage = mock(FoodProductDirectUploadStorage.class);
    private final FoodContributionStorageProperties properties = new FoodContributionStorageProperties();
    private final ProductNutritionOcrEvidenceResolver resolver =
            new ProductNutritionOcrEvidenceResolverImpl(assets, storage, properties);

    @Test
    void readsOnlyOwnedVerifiedActiveNutritionEvidenceWithBoundedStorageAccess() {
        FoodProductReviewCaseAssetEntity asset = asset(30L, FoodProductReviewAssetType.NUTRITION_LABEL);
        when(assets.findById(20L)).thenReturn(Optional.of(asset));
        when(storage.readBounded("private/nutrition.jpg", properties.getMaxUploadBytes()))
                .thenReturn(new byte[]{1, 2, 3});

        var evidence = resolver.resolve(request(30L));

        assertArrayEquals(new byte[]{1, 2, 3}, evidence.bytes());
        verify(storage).readBounded("private/nutrition.jpg", properties.getMaxUploadBytes());
    }

    @Test
    void rejectsAnotherUsersPrivateEvidenceBeforeStorageRead() {
        when(assets.findById(20L)).thenReturn(Optional.of(asset(99L, FoodProductReviewAssetType.NUTRITION_LABEL)));

        assertThrows(InvalidCredentialsException.class, () -> resolver.resolve(request(30L)));

        verify(storage, never()).readBounded(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void rejectsNonNutritionOrExpiredEvidenceBeforeStorageRead() {
        FoodProductReviewCaseAssetEntity front = asset(30L, FoodProductReviewAssetType.FRONT_PACKAGE);
        when(assets.findById(20L)).thenReturn(Optional.of(front));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request(30L)));
        front.setAssetType(FoodProductReviewAssetType.NUTRITION_LABEL);
        front.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request(30L)));
        verify(storage, never()).readBounded(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    private ProductNutritionOcrFallbackRequest request(Long requesterId) {
        return new ProductNutritionOcrFallbackRequest(10L, 20L, requesterId, "nutrition-label-v4", 0.5,
                List.of("energy"), List.of(), true, "ai-v1");
    }

    private FoodProductReviewCaseAssetEntity asset(Long ownerId, FoodProductReviewAssetType type) {
        UserEntity user = new UserEntity();
        user.setId(ownerId);
        FoodProductReviewCaseEntity reviewCase = new FoodProductReviewCaseEntity();
        reviewCase.setId(10L);
        reviewCase.setSubmittedBy(user);
        FoodProductUploadSessionEntity session = new FoodProductUploadSessionEntity();
        session.setCreatedBy(user);
        FoodProductReviewCaseAssetEntity asset = new FoodProductReviewCaseAssetEntity();
        asset.setId(20L);
        asset.setReviewCase(reviewCase);
        asset.setUploadSession(session);
        asset.setAssetType(type);
        asset.setStorageKey("private/nutrition.jpg");
        asset.setContentType("image/jpeg");
        asset.setSha256("abc");
        asset.setUploadState(FoodProductAssetUploadState.VERIFIED);
        asset.setDeletionState(FoodProductAssetDeletionState.ACTIVE);
        asset.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        return asset;
    }
}
