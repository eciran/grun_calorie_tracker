package com.grun.calorietracker.service;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.config.MediaStorageProperties;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductReviewCaseAssetEntity;
import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.enums.FoodProductAssetDeletionState;
import com.grun.calorietracker.enums.FoodProductAssetUploadState;
import com.grun.calorietracker.enums.FoodProductReviewAssetType;
import com.grun.calorietracker.enums.FoodProductReviewCaseSource;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import com.grun.calorietracker.service.evidence.FoodProductDirectUploadStorage;
import com.grun.calorietracker.service.impl.CatalogMediaServiceImpl;
import com.grun.calorietracker.service.media.MediaObjectStorage;
import com.grun.calorietracker.service.media.MediaStorageKeyPolicy;
import com.grun.calorietracker.service.support.FoodProductEvidenceImageInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogMediaServiceImplTest {
    private final FoodProductReviewCaseAssetRepository assetRepository = mock(FoodProductReviewCaseAssetRepository.class);
    private final FoodItemRepository foodItemRepository = mock(FoodItemRepository.class);
    private final FoodProductDirectUploadStorage evidenceStorage = mock(FoodProductDirectUploadStorage.class);
    private final MediaObjectStorage mediaStorage = mock(MediaObjectStorage.class);
    private final FoodProductEvidenceImageInspector inspector = mock(FoodProductEvidenceImageInspector.class);
    private CatalogMediaServiceImpl service;
    private FoodProductReviewCaseEntity reviewCase;
    private FoodItemEntity product;

    @BeforeEach
    void setUp() {
        MediaStorageProperties mediaProperties = new MediaStorageProperties();
        mediaProperties.setPublicBaseUrl("https://cdn.grun.test");
        service = new CatalogMediaServiceImpl(assetRepository, foodItemRepository, evidenceStorage, mediaStorage,
                new MediaStorageKeyPolicy(mediaProperties), inspector,
                new FoodContributionStorageProperties(), mediaProperties);
        reviewCase = new FoodProductReviewCaseEntity();
        reviewCase.setId(9L);
        reviewCase.setSource(FoodProductReviewCaseSource.USER_OCR);
        reviewCase.setPublicMediaAllowed(true);
        product = new FoodItemEntity();
        product.setId(71L);
        when(foodItemRepository.save(any(FoodItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void promotesVerifiedFrontImageToPermanentCatalogNamespace() {
        FoodProductReviewCaseAssetEntity asset = asset();
        byte[] content = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
        when(assetRepository.findAllByReviewCaseIdOrderByAssetTypeAsc(9L)).thenReturn(List.of(asset));
        when(evidenceStorage.readBounded("private/source.jpg", 6_291_456L)).thenReturn(content);

        assertTrue(service.promoteApprovedFrontImage(reviewCase, product));

        ArgumentCaptor<String> destination = ArgumentCaptor.forClass(String.class);
        verify(mediaStorage).store(destination.capture(), any(byte[].class), anyString(), anyString());
        assertTrue(destination.getValue().startsWith("grun/public/catalog/p71/"));
        assertTrue(product.getDisplayImageUrl().matches(
                "https://cdn\\.grun\\.test/api/v1/media/catalog/p71~[a-f0-9-]{36}\\.jpg"));
        assertEquals(ImageStatus.APPROVED, product.getImageStatus());
        assertEquals(ImageSource.USER_UPLOAD, product.getImageSource());
    }

    @Test
    void doesNotPublishEvidenceWithoutExplicitPublicMediaConsent() {
        reviewCase.setPublicMediaAllowed(false);

        assertFalse(service.promoteApprovedFrontImage(reviewCase, product));

        verify(assetRepository, never()).findAllByReviewCaseIdOrderByAssetTypeAsc(anyLong());
        verify(mediaStorage, never()).store(anyString(), any(), anyString(), anyString());
    }

    @Test
    void ignoresNutritionLabelWhenNoFrontPackageExists() {
        FoodProductReviewCaseAssetEntity asset = asset();
        asset.setAssetType(FoodProductReviewAssetType.NUTRITION_LABEL);
        when(assetRepository.findAllByReviewCaseIdOrderByAssetTypeAsc(9L)).thenReturn(List.of(asset));

        assertFalse(service.promoteApprovedFrontImage(reviewCase, product));

        verify(mediaStorage, never()).store(anyString(), any(), anyString(), anyString());
    }

    private FoodProductReviewCaseAssetEntity asset() {
        FoodProductReviewCaseAssetEntity asset = new FoodProductReviewCaseAssetEntity();
        asset.setId(3L);
        asset.setAssetType(FoodProductReviewAssetType.FRONT_PACKAGE);
        asset.setStorageKey("private/source.jpg");
        asset.setContentType("image/jpeg");
        asset.setSha256("abc");
        asset.setUploadState(FoodProductAssetUploadState.VERIFIED);
        asset.setDeletionState(FoodProductAssetDeletionState.ACTIVE);
        return asset;
    }
}
