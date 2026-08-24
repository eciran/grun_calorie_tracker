package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.dto.AdminFoodProductEvidenceReadDto;
import com.grun.calorietracker.entity.FoodProductReviewCaseAssetEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodProductAssetDeletionState;
import com.grun.calorietracker.enums.FoodProductAssetUploadState;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AdminFoodProductEvidenceService;
import com.grun.calorietracker.service.evidence.FoodProductDirectUploadStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "grun.food-contribution-storage", name = "provider", havingValue = "S3")
public class AdminFoodProductEvidenceServiceImpl implements AdminFoodProductEvidenceService {
    private final FoodContributionStorageProperties properties;
    private final UserRepository userRepository;
    private final FoodProductReviewCaseAssetRepository assetRepository;
    private final FoodProductDirectUploadStorage directStorage;

    @Override
    @Transactional(readOnly = true)
    public AdminFoodProductEvidenceReadDto authorizeRead(String adminEmail, Long assetId) {
        UserEntity admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        if (admin.getRole() != UserRole.OWNER && admin.getRole() != UserRole.ADMIN_CATALOG) {
            throw new AccessDeniedException("Private product evidence requires owner or catalog-admin access.");
        }
        FoodProductReviewCaseAssetEntity asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Product evidence was not found."));
        if (asset.getUploadState() != FoodProductAssetUploadState.VERIFIED
                || asset.getDeletionState() != FoodProductAssetDeletionState.ACTIVE
                || asset.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResourceNotFoundException("Product evidence is not available for review.");
        }
        var authorization = directStorage.authorizeRead(asset.getStorageKey(), properties.getAdminReadUrlTtl());
        return new AdminFoodProductEvidenceReadDto(
                asset.getId(),
                asset.getAssetType(),
                asset.getContentType(),
                authorization.url().toString(),
                authorization.expiresAt()
        );
    }
}
