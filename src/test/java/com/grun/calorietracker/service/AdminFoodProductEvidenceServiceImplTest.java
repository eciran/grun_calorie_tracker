package com.grun.calorietracker.service;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.entity.FoodProductReviewCaseAssetEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodProductAssetDeletionState;
import com.grun.calorietracker.enums.FoodProductAssetUploadState;
import com.grun.calorietracker.enums.FoodProductReviewAssetType;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.evidence.FoodProductDirectUploadStorage;
import com.grun.calorietracker.service.impl.AdminFoodProductEvidenceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFoodProductEvidenceServiceImplTest {
    @Mock private UserRepository userRepository;
    @Mock private FoodProductReviewCaseAssetRepository assetRepository;
    @Mock private FoodProductDirectUploadStorage directStorage;

    private FoodContributionStorageProperties properties;
    private AdminFoodProductEvidenceServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new FoodContributionStorageProperties();
        service = new AdminFoodProductEvidenceServiceImpl(properties, userRepository, assetRepository, directStorage);
    }

    @Test
    void ownerReceivesShortLivedSignedRead() {
        assertAuthorized(UserRole.OWNER);
    }

    @Test
    void catalogAdminReceivesShortLivedSignedRead() {
        assertAuthorized(UserRole.ADMIN_CATALOG);
    }

    @Test
    void readOnlyAdminCannotReceivePrivateEvidenceUrl() {
        UserEntity admin = admin(UserRole.ADMIN_READ_ONLY);
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        assertThrows(AccessDeniedException.class, () -> service.authorizeRead(admin.getEmail(), 10L));
        verify(assetRepository, never()).findById(10L);
        verify(directStorage, never()).authorizeRead(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void supportAdminCannotReceivePrivateEvidenceUrl() {
        UserEntity admin = admin(UserRole.ADMIN_SUPPORT);
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        assertThrows(AccessDeniedException.class, () -> service.authorizeRead(admin.getEmail(), 10L));
    }

    @Test
    void expiredOrUnverifiedEvidenceDoesNotReceiveUrl() {
        UserEntity admin = admin(UserRole.OWNER);
        FoodProductReviewCaseAssetEntity asset = asset();
        asset.setUploadState(FoodProductAssetUploadState.RESERVED);
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(assetRepository.findById(10L)).thenReturn(Optional.of(asset));
        assertThrows(ResourceNotFoundException.class, () -> service.authorizeRead(admin.getEmail(), 10L));
        verify(directStorage, never()).authorizeRead(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private void assertAuthorized(UserRole role) {
        UserEntity admin = admin(role);
        FoodProductReviewCaseAssetEntity asset = asset();
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(assetRepository.findById(10L)).thenReturn(Optional.of(asset));
        Instant expiry = Instant.now().plusSeconds(300);
        when(directStorage.authorizeRead(asset.getStorageKey(), properties.getAdminReadUrlTtl()))
                .thenReturn(new FoodProductDirectUploadStorage.ReadAuthorization(
                        URI.create("https://storage.invalid/private-signed-url"), expiry
                ));

        var result = service.authorizeRead(admin.getEmail(), 10L);

        assertEquals(10L, result.assetId());
        assertEquals(expiry, result.expiresAt());
        assertEquals("https://storage.invalid/private-signed-url", result.signedUrl());
    }

    private UserEntity admin(UserRole role) {
        UserEntity admin = new UserEntity();
        admin.setId(2L);
        admin.setEmail(role.name().toLowerCase() + "@example.com");
        admin.setRole(role);
        return admin;
    }

    private FoodProductReviewCaseAssetEntity asset() {
        FoodProductReviewCaseAssetEntity asset = new FoodProductReviewCaseAssetEntity();
        asset.setId(10L);
        asset.setAssetType(FoodProductReviewAssetType.NUTRITION_LABEL);
        asset.setContentType("image/jpeg");
        asset.setStorageKey("pending/product-intakes/session/object");
        asset.setUploadState(FoodProductAssetUploadState.VERIFIED);
        asset.setDeletionState(FoodProductAssetDeletionState.ACTIVE);
        asset.setExpiresAt(LocalDateTime.now().plusDays(2));
        return asset;
    }
}
