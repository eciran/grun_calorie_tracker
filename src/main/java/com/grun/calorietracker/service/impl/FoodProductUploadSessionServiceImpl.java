package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.dto.FoodProductUploadFinalizeDto;
import com.grun.calorietracker.dto.FoodProductUploadSessionDto;
import com.grun.calorietracker.dto.FoodProductUploadSessionRequestDto;
import com.grun.calorietracker.dto.FoodProductUploadSessionStateDto;
import com.grun.calorietracker.entity.FoodProductReviewCaseAssetEntity;
import com.grun.calorietracker.entity.FoodProductUploadSessionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodProductAssetDeletionState;
import com.grun.calorietracker.enums.FoodProductAssetUploadState;
import com.grun.calorietracker.enums.FoodProductReviewAssetType;
import com.grun.calorietracker.enums.FoodProductUploadSessionStatus;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import com.grun.calorietracker.repository.FoodProductUploadSessionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.FoodProductUploadSessionService;
import com.grun.calorietracker.service.evidence.FoodProductDirectUploadStorage;
import com.grun.calorietracker.service.support.FoodProductEvidenceImageInspector;
import com.grun.calorietracker.service.support.ProductIntakeRolloutPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "grun.food-contribution-storage", name = "provider", havingValue = "S3")
public class FoodProductUploadSessionServiceImpl implements FoodProductUploadSessionService {
    private static final Set<FoodProductReviewAssetType> REQUIRED_TYPES =
            EnumSet.of(FoodProductReviewAssetType.FRONT_PACKAGE, FoodProductReviewAssetType.NUTRITION_LABEL);

    private final FoodContributionStorageProperties properties;
    private final UserRepository userRepository;
    private final FoodProductUploadSessionRepository sessionRepository;
    private final FoodProductReviewCaseAssetRepository assetRepository;
    private final FoodProductDirectUploadStorage directStorage;
    private final FoodProductEvidenceImageInspector imageInspector;
    private final ProductIntakeRolloutPolicy rolloutPolicy;

    @Override
    @Transactional
    public synchronized FoodProductUploadSessionDto create(
            String userEmail,
            FoodProductUploadSessionRequestDto request
    ) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        rolloutPolicy.requireAvailable(user);
        validateRequest(request);
        return sessionRepository.findByCreatedByIdAndIdempotencyKey(user.getId(), request.idempotencyKey())
                .map(existing -> existingResponse(existing, user.getId()))
                .orElseGet(() -> createSession(user, request));
    }

    @Override
    @Transactional(readOnly = true)
    public FoodProductUploadSessionStateDto get(String userEmail, String sessionId) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        FoodProductUploadSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Product evidence upload session was not found."));
        if (!session.getCreatedBy().getId().equals(user.getId())) {
            throw new InvalidCredentialsException("Invalid credential");
        }
        var values = assetRepository.findAllByUploadSessionIdOrderByAssetTypeAsc(sessionId).stream()
                .map(asset -> new FoodProductUploadSessionStateDto.Asset(
                        asset.getId(), asset.getAssetType(), asset.getUploadState()))
                .toList();
        return new FoodProductUploadSessionStateDto(session.getId(), session.getIdempotencyKey(),
                session.getStatus(), session.getExpiresAt(), session.getFinalizedAt(), values);
    }
    @Override
    @Transactional
    public synchronized FoodProductUploadFinalizeDto finalizeUpload(String userEmail, String sessionId) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        FoodProductUploadSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Product evidence upload session was not found."));
        if (!session.getCreatedBy().getId().equals(user.getId())) {
            throw new InvalidCredentialsException("Invalid credential");
        }
        List<FoodProductReviewCaseAssetEntity> assets =
                assetRepository.findAllByUploadSessionIdOrderByAssetTypeAsc(sessionId);
        if (session.getStatus() == FoodProductUploadSessionStatus.FINALIZED) {
            return finalizeResponse(session, assets);
        }
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RequestConflictException("Product evidence upload session has expired.");
        }
        if (session.getStatus() != FoodProductUploadSessionStatus.UPLOADING || assets.size() != 2) {
            throw new RequestConflictException("Product evidence upload session is not ready to finalize.");
        }
        for (FoodProductReviewCaseAssetEntity asset : assets) {
            FoodProductDirectUploadStorage.StoredObject stored = directStorage.inspect(asset.getStorageKey());
            if (!asset.getContentType().equalsIgnoreCase(stored.contentType())
                    || asset.getSizeBytes() != stored.sizeBytes()
                    || stored.sha256() == null
                    || !asset.getSha256().equalsIgnoreCase(stored.sha256())) {
                throw new IllegalArgumentException("Stored product evidence metadata does not match the reserved upload.");
            }
            byte[] bytes = directStorage.readBounded(asset.getStorageKey(), properties.getMaxUploadBytes());
            FoodProductEvidenceImageInspector.Dimensions dimensions =
                    imageInspector.inspect(bytes, asset.getContentType(), asset.getSha256());
            asset.setWidth(dimensions.width());
            asset.setHeight(dimensions.height());
            asset.setUploadState(FoodProductAssetUploadState.VERIFIED);
        }
        assetRepository.saveAll(assets);
        LocalDateTime now = LocalDateTime.now();
        session.setStatus(FoodProductUploadSessionStatus.FINALIZED);
        session.setFinalizedAt(now);
        session.setUpdatedAt(now);
        sessionRepository.save(session);
        return finalizeResponse(session, assets);
    }

    private FoodProductUploadFinalizeDto finalizeResponse(
            FoodProductUploadSessionEntity session,
            List<FoodProductReviewCaseAssetEntity> assets
    ) {
        return new FoodProductUploadFinalizeDto(
                session.getId(),
                session.getStatus(),
                session.getFinalizedAt(),
                assets.stream().map(asset -> new FoodProductUploadFinalizeDto.Asset(
                        asset.getId(), asset.getAssetType(), asset.getUploadState(),
                        asset.getWidth() == null ? 0 : asset.getWidth(),
                        asset.getHeight() == null ? 0 : asset.getHeight()
                )).toList()
        );
    }
    private FoodProductUploadSessionDto createSession(
            UserEntity user,
            FoodProductUploadSessionRequestDto request
    ) {
        LocalDateTime now = LocalDateTime.now();
        FoodProductUploadSessionEntity session = new FoodProductUploadSessionEntity();
        session.setId(UUID.randomUUID().toString());
        session.setCreatedBy(user);
        session.setIdempotencyKey(request.idempotencyKey());
        session.setStatus(FoodProductUploadSessionStatus.CREATED);
        session.setExpiresAt(now.plus(properties.getUploadSessionTtl()));
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionRepository.saveAndFlush(session);

        List<FoodProductReviewCaseAssetEntity> assets = new ArrayList<>();
        for (FoodProductUploadSessionRequestDto.Asset requested : request.assets()) {
            FoodProductReviewCaseAssetEntity asset = new FoodProductReviewCaseAssetEntity();
            asset.setUploadSession(session);
            asset.setAssetType(requested.assetType());
            asset.setStorageKey(opaqueKey(session.getId()));
            asset.setContentType(requested.contentType().toLowerCase(Locale.ROOT));
            asset.setSizeBytes(requested.sizeBytes());
            asset.setSha256(requested.sha256());
            asset.setUploadState(FoodProductAssetUploadState.RESERVED);
            asset.setExpiresAt(now.plusDays(properties.getPendingRetentionDays()));
            asset.setDeletionState(FoodProductAssetDeletionState.ACTIVE);
            asset.setDeletionAttemptCount(0);
            asset.setCreatedAt(now);
            assets.add(asset);
        }
        assets = assetRepository.saveAll(assets);
        session.setStatus(FoodProductUploadSessionStatus.UPLOADING);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
        return response(session, assets);
    }

    private FoodProductUploadSessionDto existingResponse(FoodProductUploadSessionEntity session, Long userId) {
        if (!session.getCreatedBy().getId().equals(userId)) {
            throw new InvalidCredentialsException("Invalid credential");
        }
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RequestConflictException("Product evidence upload session has expired.");
        }
        if (session.getStatus() != FoodProductUploadSessionStatus.UPLOADING) {
            throw new RequestConflictException("Product evidence upload session no longer accepts uploads.");
        }
        return response(session, assetRepository.findAllByUploadSessionIdOrderByAssetTypeAsc(session.getId()));
    }

    private FoodProductUploadSessionDto response(
            FoodProductUploadSessionEntity session,
            List<FoodProductReviewCaseAssetEntity> assets
    ) {
        List<FoodProductUploadSessionDto.Slot> slots = assets.stream().map(asset -> {
            var authorization = directStorage.authorizeUpload(
                    new FoodProductDirectUploadStorage.UploadObject(
                            asset.getStorageKey(),
                            asset.getContentType(),
                            asset.getSizeBytes(),
                            asset.getSha256()
                    ),
                    properties.getUploadUrlTtl()
            );
            return new FoodProductUploadSessionDto.Slot(
                    asset.getId(),
                    asset.getAssetType(),
                    authorization.url().toString(),
                    authorization.method(),
                    authorization.requiredHeaders(),
                    authorization.expiresAt()
            );
        }).toList();
        return new FoodProductUploadSessionDto(session.getId(), session.getExpiresAt(), slots);
    }

    private void validateRequest(FoodProductUploadSessionRequestDto request) {
        if (request == null || request.assets() == null || request.assets().size() != 2) {
            throw new IllegalArgumentException("Exactly two product evidence assets are required.");
        }
        Set<FoodProductReviewAssetType> types = new HashSet<>();
        Set<String> allowedTypes = allowedContentTypes();
        for (FoodProductUploadSessionRequestDto.Asset asset : request.assets()) {
            if (asset == null || asset.assetType() == null || !REQUIRED_TYPES.contains(asset.assetType())) {
                throw new IllegalArgumentException("Only front-package and nutrition-label evidence slots are accepted.");
            }
            if (!types.add(asset.assetType())) {
                throw new IllegalArgumentException("Product evidence asset types must be unique.");
            }
            String contentType = asset.contentType() == null ? "" : asset.contentType().toLowerCase(Locale.ROOT);
            if (!allowedTypes.contains(contentType)) {
                throw new IllegalArgumentException("Product evidence content type is not allowed.");
            }
            if (asset.sizeBytes() <= 0 || asset.sizeBytes() > properties.getMaxUploadBytes()) {
                throw new IllegalArgumentException("Product evidence size exceeds the configured upload limit.");
            }
            if (asset.sha256() == null || !asset.sha256().matches("^[0-9a-f]{64}$")) {
                throw new IllegalArgumentException("Product evidence checksum is invalid.");
            }
        }
        if (!types.equals(REQUIRED_TYPES)) {
            throw new IllegalArgumentException("Front-package and nutrition-label evidence are both required.");
        }
    }

    private Set<String> allowedContentTypes() {
        Set<String> values = new HashSet<>();
        for (String value : properties.getAllowedContentTypes().split(",")) {
            if (!value.isBlank()) {
                values.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return values;
    }

    private String opaqueKey(String sessionId) {
        String prefix = properties.getS3().getPrefix();
        prefix = prefix == null ? "" : prefix.trim().replaceAll("^/+|/+$", "");
        if (prefix.isBlank()) {
            prefix = "pending/product-intakes";
        }
        return prefix + "/" + sessionId + "/" + UUID.randomUUID();
    }
}
