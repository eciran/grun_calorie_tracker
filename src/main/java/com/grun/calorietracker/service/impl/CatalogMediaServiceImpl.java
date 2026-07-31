package com.grun.calorietracker.service.impl;

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
import com.grun.calorietracker.service.CatalogMediaService;
import com.grun.calorietracker.service.evidence.FoodProductDirectUploadStorage;
import com.grun.calorietracker.service.media.MediaNamespace;
import com.grun.calorietracker.service.media.MediaObjectStorage;
import com.grun.calorietracker.service.media.MediaStorageKeyPolicy;
import com.grun.calorietracker.service.support.FoodProductEvidenceImageInspector;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Comparator;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "grun.food-contribution-storage", name = "provider", havingValue = "S3")
public class CatalogMediaServiceImpl implements CatalogMediaService {
    private final FoodProductReviewCaseAssetRepository assetRepository;
    private final FoodItemRepository foodItemRepository;
    private final FoodProductDirectUploadStorage evidenceStorage;
    private final MediaObjectStorage mediaStorage;
    private final MediaStorageKeyPolicy keyPolicy;
    private final FoodProductEvidenceImageInspector imageInspector;
    private final FoodContributionStorageProperties evidenceProperties;
    private final MediaStorageProperties mediaProperties;

    @Override
    public boolean promoteApprovedFrontImage(FoodProductReviewCaseEntity reviewCase, FoodItemEntity product) {
        if (!Boolean.TRUE.equals(reviewCase.getPublicMediaAllowed()) || product == null || product.getId() == null) {
            return false;
        }
        FoodProductReviewCaseAssetEntity source = assetRepository
                .findAllByReviewCaseIdOrderByAssetTypeAsc(reviewCase.getId()).stream()
                .filter(asset -> asset.getAssetType() == FoodProductReviewAssetType.FRONT_PACKAGE)
                .filter(asset -> asset.getUploadState() == FoodProductAssetUploadState.VERIFIED)
                .filter(asset -> asset.getDeletionState() == FoodProductAssetDeletionState.ACTIVE)
                .min(Comparator.comparing(FoodProductReviewCaseAssetEntity::getId))
                .orElse(null);
        if (source == null) return false;

        byte[] content = evidenceStorage.readBounded(source.getStorageKey(), evidenceProperties.getMaxUploadBytes());
        imageInspector.inspect(content, source.getContentType(), source.getSha256());
        String extension = extension(source.getContentType());
        String owner = "p" + product.getId();
        String destinationKey = keyPolicy.create(MediaNamespace.CATALOG_MEDIA, owner, extension);
        String previousKey = storageKeyFromPublicUrl(product.getDisplayImageUrl());

        mediaStorage.store(destinationKey, content, source.getContentType(), source.getSha256());
        try {
            product.setDisplayImageUrl(publicUrl(publicToken(destinationKey, owner)));
            product.setImageUrl(product.getDisplayImageUrl());
            product.setImageStatus(ImageStatus.APPROVED);
            product.setImageSource(reviewCase.getSource() == FoodProductReviewCaseSource.ADMIN_MANUAL
                    ? ImageSource.ADMIN_UPLOAD : ImageSource.USER_UPLOAD);
            foodItemRepository.save(product);
            registerCleanup(destinationKey, previousKey);
            return true;
        } catch (RuntimeException exception) {
            safelyDelete(destinationKey);
            throw exception;
        }
    }

    @Override
    public Resource load(String publicToken) {
        String storageKey = storageKeyFromToken(publicToken);
        mediaStorage.inspect(storageKey);
        return new ByteArrayResource(mediaStorage.readBounded(storageKey, evidenceProperties.getMaxUploadBytes()));
    }

    private void registerCleanup(String createdKey, String previousKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (previousKey != null) safelyDelete(previousKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    if (previousKey != null) safelyDelete(previousKey);
                } else {
                    safelyDelete(createdKey);
                }
            }
        });
    }

    private String publicUrl(String token) {
        String base = mediaProperties.getPublicBaseUrl().replaceAll("/+$", "");
        return base + "/api/v1/media/catalog/" + token;
    }

    private String publicToken(String storageKey, String owner) {
        String prefix = keyPolicy.prefix(MediaNamespace.CATALOG_MEDIA) + "/" + owner + "/";
        if (!storageKey.startsWith(prefix)) throw new IllegalArgumentException("Catalog media storage key is invalid.");
        return owner + "~" + storageKey.substring(prefix.length());
    }

    private String storageKeyFromPublicUrl(String url) {
        if (url == null || url.isBlank()) return null;
        String marker = "/api/v1/media/catalog/";
        int index = url.indexOf(marker);
        return index < 0 ? null : storageKeyFromToken(url.substring(index + marker.length()));
    }

    private String storageKeyFromToken(String token) {
        if (token == null || !token.matches("p\\d+~[a-fA-F0-9-]{36}\\.(jpg|png|webp)")) {
            throw new IllegalArgumentException("Catalog media token is invalid.");
        }
        int separator = token.indexOf('~');
        String owner = token.substring(0, separator);
        String filename = token.substring(separator + 1);
        return keyPolicy.requireManaged(keyPolicy.prefix(MediaNamespace.CATALOG_MEDIA)
                + "/" + owner + "/" + filename);
    }

    private String extension(String contentType) {
        return switch (contentType == null ? "" : contentType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/jpeg" -> ".jpg";
            default -> throw new IllegalArgumentException("Catalog media content type is not allowed.");
        };
    }

    private void safelyDelete(String key) {
        try {
            mediaStorage.delete(key);
        } catch (RuntimeException ignored) {
            // Object cleanup is retryable and must not corrupt the catalog transaction.
        }
    }
}
