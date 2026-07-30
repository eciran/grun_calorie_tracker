package com.grun.calorietracker.service;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.dto.FoodProductUploadSessionRequestDto;
import com.grun.calorietracker.entity.FoodProductReviewCaseAssetEntity;
import com.grun.calorietracker.entity.FoodProductUploadSessionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodProductReviewAssetType;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import com.grun.calorietracker.repository.FoodProductUploadSessionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.evidence.FoodProductDirectUploadStorage;
import com.grun.calorietracker.service.impl.FoodProductUploadSessionServiceImpl;
import com.grun.calorietracker.service.support.FoodProductEvidenceImageInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodProductUploadSessionServiceImplTest {
    @Mock private UserRepository userRepository;
    @Mock private FoodProductUploadSessionRepository sessionRepository;
    @Mock private FoodProductReviewCaseAssetRepository assetRepository;
    @Mock private FoodProductDirectUploadStorage directStorage;
    @Mock private FoodProductEvidenceImageInspector imageInspector;

    private FoodContributionStorageProperties properties;
    private FoodProductUploadSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new FoodContributionStorageProperties();
        properties.getS3().setPrefix("pending/product-intakes");
        service = new FoodProductUploadSessionServiceImpl(
                properties, userRepository, sessionRepository, assetRepository, directStorage, imageInspector
        );
    }

    @Test
    void createsExactlyTwoOpaquePrivateUploadSlots() {
        UserEntity user = user();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(sessionRepository.findByCreatedByIdAndIdempotencyKey(7L, "idem-1")).thenReturn(Optional.empty());
        when(sessionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AtomicLong ids = new AtomicLong(10);
        when(assetRepository.saveAll(any())).thenAnswer(invocation -> {
            List<FoodProductReviewCaseAssetEntity> assets = invocation.getArgument(0);
            assets.forEach(asset -> asset.setId(ids.incrementAndGet()));
            return assets;
        });
        when(directStorage.authorizeUpload(any(), any())).thenReturn(new FoodProductDirectUploadStorage.UploadAuthorization(
                URI.create("https://storage.invalid/signed"), "PUT", Map.of("content-type", "image/jpeg"), Instant.now().plusSeconds(600)
        ));

        var result = service.create(user.getEmail(), validRequest());

        assertEquals(2, result.slots().size());
        assertEquals(2, result.slots().stream().map(slot -> slot.assetType()).distinct().count());
        ArgumentCaptor<FoodProductDirectUploadStorage.UploadObject> objects =
                ArgumentCaptor.forClass(FoodProductDirectUploadStorage.UploadObject.class);
        verify(directStorage, org.mockito.Mockito.times(2)).authorizeUpload(objects.capture(), any());
        assertTrue(objects.getAllValues().stream().allMatch(value -> value.storageKey().startsWith("pending/product-intakes/")));
        assertTrue(objects.getAllValues().stream().noneMatch(value -> value.storageKey().contains("/u7/")));
        assertFalse(result.sessionId().isBlank());
    }

    @Test
    void duplicateIdempotencyKeyReusesSessionAndAssets() {
        UserEntity user = user();
        FoodProductUploadSessionEntity existing = new FoodProductUploadSessionEntity();
        existing.setId("existing-session");
        existing.setCreatedBy(user);
        existing.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        FoodProductReviewCaseAssetEntity front = asset(existing, 1L, FoodProductReviewAssetType.FRONT_PACKAGE);
        FoodProductReviewCaseAssetEntity nutrition = asset(existing, 2L, FoodProductReviewAssetType.NUTRITION_LABEL);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(sessionRepository.findByCreatedByIdAndIdempotencyKey(7L, "idem-1")).thenReturn(Optional.of(existing));
        when(assetRepository.findAllByUploadSessionIdOrderByAssetTypeAsc("existing-session"))
                .thenReturn(List.of(front, nutrition));
        when(directStorage.authorizeUpload(any(), any())).thenReturn(new FoodProductDirectUploadStorage.UploadAuthorization(
                URI.create("https://storage.invalid/signed"), "PUT", Map.of(), Instant.now().plusSeconds(600)
        ));

        var result = service.create(user.getEmail(), validRequest());

        assertEquals("existing-session", result.sessionId());
        assertEquals(2, result.slots().size());
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateOrUnsupportedAssetTypes() {
        UserEntity user = user();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var duplicate = new FoodProductUploadSessionRequestDto("idem-2", List.of(
                assetRequest(FoodProductReviewAssetType.FRONT_PACKAGE),
                assetRequest(FoodProductReviewAssetType.FRONT_PACKAGE)
        ));
        assertThrows(IllegalArgumentException.class, () -> service.create(user.getEmail(), duplicate));
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsExpiredIdempotentSession() {
        UserEntity user = user();
        FoodProductUploadSessionEntity existing = new FoodProductUploadSessionEntity();
        existing.setId("expired");
        existing.setCreatedBy(user);
        existing.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(sessionRepository.findByCreatedByIdAndIdempotencyKey(7L, "idem-1")).thenReturn(Optional.of(existing));
        assertThrows(RequestConflictException.class, () -> service.create(user.getEmail(), validRequest()));
    }

    @Test
    void finalizesOnlyAfterMetadataAndBoundedImageValidation() {
        UserEntity user = user();
        FoodProductUploadSessionEntity session = uploadSession(user, "ready", LocalDateTime.now().plusMinutes(5));
        FoodProductReviewCaseAssetEntity front = asset(session, 1L, FoodProductReviewAssetType.FRONT_PACKAGE);
        FoodProductReviewCaseAssetEntity nutrition = asset(session, 2L, FoodProductReviewAssetType.NUTRITION_LABEL);
        byte[] bytes = new byte[]{1, 2, 3};
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(sessionRepository.findById("ready")).thenReturn(Optional.of(session));
        when(assetRepository.findAllByUploadSessionIdOrderByAssetTypeAsc("ready")).thenReturn(List.of(front, nutrition));
        when(directStorage.inspect(any())).thenAnswer(invocation -> new FoodProductDirectUploadStorage.StoredObject(
                invocation.getArgument(0), "image/jpeg", 1024, "a".repeat(64)
        ));
        when(directStorage.readBounded(any(), any(Long.class))).thenReturn(bytes);
        when(imageInspector.inspect(bytes, "image/jpeg", "a".repeat(64)))
                .thenReturn(new FoodProductEvidenceImageInspector.Dimensions(1200, 800));
        when(assetRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.finalizeUpload(user.getEmail(), "ready");

        assertEquals(com.grun.calorietracker.enums.FoodProductUploadSessionStatus.FINALIZED, result.status());
        assertTrue(result.assets().stream().allMatch(value -> value.width() == 1200 && value.height() == 800));
        assertTrue(result.assets().stream().allMatch(value -> value.uploadState()
                == com.grun.calorietracker.enums.FoodProductAssetUploadState.VERIFIED));
    }

    @Test
    void finalizeRejectsMismatchedStoredMetadata() {
        UserEntity user = user();
        FoodProductUploadSessionEntity session = uploadSession(user, "mismatch", LocalDateTime.now().plusMinutes(5));
        FoodProductReviewCaseAssetEntity front = asset(session, 1L, FoodProductReviewAssetType.FRONT_PACKAGE);
        FoodProductReviewCaseAssetEntity nutrition = asset(session, 2L, FoodProductReviewAssetType.NUTRITION_LABEL);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(sessionRepository.findById("mismatch")).thenReturn(Optional.of(session));
        when(assetRepository.findAllByUploadSessionIdOrderByAssetTypeAsc("mismatch")).thenReturn(List.of(front, nutrition));
        when(directStorage.inspect(front.getStorageKey())).thenReturn(new FoodProductDirectUploadStorage.StoredObject(
                front.getStorageKey(), "image/png", 1024, "a".repeat(64)
        ));

        assertThrows(IllegalArgumentException.class, () -> service.finalizeUpload(user.getEmail(), "mismatch"));
        verify(assetRepository, never()).saveAll(any());
    }

    @Test
    void finalizedSessionIsIdempotentAndDoesNotReadStorageAgain() {
        UserEntity user = user();
        FoodProductUploadSessionEntity session = uploadSession(user, "done", LocalDateTime.now().plusMinutes(5));
        session.setStatus(com.grun.calorietracker.enums.FoodProductUploadSessionStatus.FINALIZED);
        session.setFinalizedAt(LocalDateTime.now().minusSeconds(10));
        FoodProductReviewCaseAssetEntity front = asset(session, 1L, FoodProductReviewAssetType.FRONT_PACKAGE);
        front.setUploadState(com.grun.calorietracker.enums.FoodProductAssetUploadState.VERIFIED);
        front.setWidth(100);
        front.setHeight(200);
        FoodProductReviewCaseAssetEntity nutrition = asset(session, 2L, FoodProductReviewAssetType.NUTRITION_LABEL);
        nutrition.setUploadState(com.grun.calorietracker.enums.FoodProductAssetUploadState.VERIFIED);
        nutrition.setWidth(300);
        nutrition.setHeight(400);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(sessionRepository.findById("done")).thenReturn(Optional.of(session));
        when(assetRepository.findAllByUploadSessionIdOrderByAssetTypeAsc("done")).thenReturn(List.of(front, nutrition));

        var result = service.finalizeUpload(user.getEmail(), "done");

        assertEquals(com.grun.calorietracker.enums.FoodProductUploadSessionStatus.FINALIZED, result.status());
        verify(directStorage, never()).inspect(any());
    }

    private FoodProductUploadSessionEntity uploadSession(UserEntity user, String id, LocalDateTime expiresAt) {
        FoodProductUploadSessionEntity session = new FoodProductUploadSessionEntity();
        session.setId(id);
        session.setCreatedBy(user);
        session.setStatus(com.grun.calorietracker.enums.FoodProductUploadSessionStatus.UPLOADING);
        session.setExpiresAt(expiresAt);
        return session;
    }
    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setEmail("user@example.com");
        return user;
    }

    private FoodProductUploadSessionRequestDto validRequest() {
        return new FoodProductUploadSessionRequestDto("idem-1", List.of(
                assetRequest(FoodProductReviewAssetType.FRONT_PACKAGE),
                assetRequest(FoodProductReviewAssetType.NUTRITION_LABEL)
        ));
    }

    private FoodProductUploadSessionRequestDto.Asset assetRequest(FoodProductReviewAssetType type) {
        return new FoodProductUploadSessionRequestDto.Asset(type, "image/jpeg", 1024, "a".repeat(64));
    }

    private FoodProductReviewCaseAssetEntity asset(
            FoodProductUploadSessionEntity session,
            Long id,
            FoodProductReviewAssetType type
    ) {
        FoodProductReviewCaseAssetEntity asset = new FoodProductReviewCaseAssetEntity();
        asset.setId(id);
        asset.setUploadSession(session);
        asset.setAssetType(type);
        asset.setStorageKey("pending/product-intakes/existing/" + id);
        asset.setContentType("image/jpeg");
        asset.setSizeBytes(1024L);
        asset.setSha256("a".repeat(64));
        return asset;
    }
}
