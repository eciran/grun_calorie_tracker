package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.dto.FoodProductEvidenceResubmitRequestDto;
import com.grun.calorietracker.dto.FoodProductOcrExtractionDto;
import com.grun.calorietracker.dto.FoodProductReviewSubmitRequestDto;
import com.grun.calorietracker.dto.FoodProductReviewSubmitResponseDto;
import com.grun.calorietracker.dto.MyFoodProductReviewCaseDto;
import com.grun.calorietracker.dto.MyFoodProductReviewCasePageDto;
import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.entity.FoodProductReviewCaseExtractionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodProductAssetUploadState;
import com.grun.calorietracker.enums.FoodProductReviewCaseSource;
import com.grun.calorietracker.enums.FoodProductReviewCaseStatus;
import com.grun.calorietracker.enums.FoodProductUploadSessionStatus;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import com.grun.calorietracker.repository.FoodProductReviewCaseExtractionRepository;
import com.grun.calorietracker.repository.FoodProductReviewCaseRepository;
import com.grun.calorietracker.repository.FoodProductUploadSessionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.FoodProductReviewCaseService;
import com.grun.calorietracker.service.FoodProductReviewSubmissionService;
import com.grun.calorietracker.service.model.FoodProductReviewCaseCommand;
import com.grun.calorietracker.service.support.ProductIntakeRolloutPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "grun.food-contribution-storage", name = "provider", havingValue = "S3")
public class FoodProductReviewSubmissionServiceImpl implements FoodProductReviewSubmissionService {
    private final UserRepository users;
    private final FoodProductUploadSessionRepository sessions;
    private final FoodProductReviewCaseAssetRepository assets;
    private final FoodProductReviewCaseRepository reviewCases;
    private final FoodProductReviewCaseExtractionRepository extractions;
    private final FoodProductReviewCaseService cases;
    private final ObjectMapper json;
    private final ProductIntakeRolloutPolicy rolloutPolicy;
    private final FoodContributionStorageProperties storageProperties;

    @Override
    @Transactional
    public synchronized FoodProductReviewSubmitResponseDto submit(
            String email, String sessionId, FoodProductReviewSubmitRequestDto request) {
        UserEntity user = user(email);
        rolloutPolicy.requireAvailable(user);
        var session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Upload session was not found."));
        requireOwner(session.getCreatedBy(), user);
        if (session.getStatus() != FoodProductUploadSessionStatus.FINALIZED) {
            throw new RequestConflictException("Evidence must be finalized.");
        }
        var evidence = assets.findAllByUploadSessionIdOrderByAssetTypeAsc(sessionId);
        requireVerifiedEvidence(evidence);
        var review = cases.finalizeCase(new FoodProductReviewCaseCommand(
                request.idempotencyKey(), FoodProductReviewCaseSource.USER_OCR, sessionId, user,
                request.barcode(), request.marketRegion(), null, request.productName(), request.brand(),
                request.calories(), request.protein(), request.fat(), request.carbs(), request.fiber(),
                request.sugar(), request.sodium(), request.nutritionBasis(), request.riskLevel(), 1,
                encode(request.submittedFields()), encode(request.fieldConfidence()),
                encode(request.correctionSummary()), request.consentVersion(),
                request.temporaryEvidenceAllowed(), request.publicMediaAllowed()));
        requireOwner(review.getSubmittedBy(), user);
        if (!sessionId.equals(review.getSourceReference())) {
            throw new RequestConflictException("Idempotency key is already bound to another submission.");
        }
        evidence.forEach(asset -> {
            if (asset.getReviewCase() != null && !asset.getReviewCase().getId().equals(review.getId())) {
                throw new RequestConflictException("Evidence is already attached.");
            }
            asset.setReviewCase(review);
        });
        assets.saveAll(evidence);
        saveExtraction(review, request.ocrExtraction());
        return response(review, sessionId);
    }

    @Override
    @Transactional
    public synchronized FoodProductReviewSubmitResponseDto resubmitEvidence(
            String email, Long caseId, String sessionId) {
        UserEntity user = user(email);
        rolloutPolicy.requireAvailable(user);
        var review = ownedCase(caseId, user);
        if (review.getStatus() != FoodProductReviewCaseStatus.NEEDS_SUBMITTER_ACTION) {
            throw new RequestConflictException("Review case is not waiting for updated evidence.");
        }
        var session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Upload session was not found."));
        requireOwner(session.getCreatedBy(), user);
        if (session.getStatus() != FoodProductUploadSessionStatus.FINALIZED) {
            throw new RequestConflictException("Evidence must be finalized.");
        }
        var evidence = assets.findAllByUploadSessionIdOrderByAssetTypeAsc(sessionId);
        requireVerifiedEvidence(evidence);
        if (evidence.stream().anyMatch(asset -> asset.getReviewCase() != null)) {
            throw new RequestConflictException("Evidence is already attached.");
        }
        assets.expireReviewCaseAssets(caseId, LocalDateTime.now());
        evidence.forEach(asset -> asset.setReviewCase(review));
        assets.saveAll(evidence);
        var submitted = cases.transition(caseId, FoodProductReviewCaseStatus.SUBMITTED,
                email, "Updated evidence submitted by user");
        return response(submitted, sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public MyFoodProductReviewCasePageDto listMine(String email, int page, int size) {
        UserEntity user = user(email);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));
        var result = reviewCases.findAllBySubmittedByIdOrderByCreatedAtDesc(
                user.getId(), PageRequest.of(safePage, safeSize));
        return new MyFoodProductReviewCasePageDto(result.getContent().stream().map(this::mine).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional
    public FoodProductReviewSubmitResponseDto withdraw(String email, Long caseId) {
        UserEntity user = user(email);
        var review = ownedCase(caseId, user);
        if (review.getStatus() != FoodProductReviewCaseStatus.SUBMITTED
                && review.getStatus() != FoodProductReviewCaseStatus.NEEDS_SUBMITTER_ACTION) {
            throw new RequestConflictException("Review case can no longer be withdrawn.");
        }
        var withdrawn = cases.transition(caseId, FoodProductReviewCaseStatus.WITHDRAWN,
                email, "Withdrawn by submitter");
        return response(withdrawn, withdrawn.getSourceReference());
    }

    private void saveExtraction(FoodProductReviewCaseEntity review, FoodProductOcrExtractionDto value) {
        if (value == null || extractions.findByReviewCaseId(review.getId()).isPresent()) return;
        FoodProductReviewCaseExtractionEntity entity = new FoodProductReviewCaseExtractionEntity();
        entity.setReviewCase(review);
        entity.setEngine(value.engine().trim());
        entity.setEngineVersion(trim(value.engineVersion()));
        entity.setParserVersion(value.parserVersion().trim());
        entity.setLocale(trim(value.locale()));
        entity.setRecognizedLinesJson(encode(value.recognizedLines()));
        entity.setParsedValuesJson(encode(value.parsedValues()));
        entity.setParserWarningsJson(encode(value.parserWarnings()));
        entity.setRawPayloadExpiresAt(LocalDateTime.now().plusDays(storageProperties.getPendingRetentionDays()));
        entity.setCreatedAt(LocalDateTime.now());
        extractions.save(entity);
    }

    private MyFoodProductReviewCaseDto mine(FoodProductReviewCaseEntity value) {
        return new MyFoodProductReviewCaseDto(value.getId(), value.getOriginalBarcode(),
                value.getFoodItem() == null ? null : value.getFoodItem().getName(), value.getMarketRegion(),
                value.getStatus(), value.getRiskLevel(), value.getReviewNote(),
                value.getCreatedAt(), value.getUpdatedAt());
    }

    private FoodProductReviewSubmitResponseDto response(FoodProductReviewCaseEntity review, String sessionId) {
        return new FoodProductReviewSubmitResponseDto(review.getId(), review.getStatus(), sessionId);
    }

    private FoodProductReviewCaseEntity ownedCase(Long caseId, UserEntity user) {
        var review = reviewCases.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Review case was not found."));
        requireOwner(review.getSubmittedBy(), user);
        return review;
    }

    private UserEntity user(String email) {
        return users.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private void requireOwner(UserEntity owner, UserEntity user) {
        if (owner == null || owner.getId() == null || !owner.getId().equals(user.getId())) {
            throw new InvalidCredentialsException("Invalid credential");
        }
    }

    private void requireVerifiedEvidence(java.util.List<com.grun.calorietracker.entity.FoodProductReviewCaseAssetEntity> evidence) {
        if (evidence.size() != 2 || evidence.stream()
                .anyMatch(asset -> asset.getUploadState() != FoodProductAssetUploadState.VERIFIED)) {
            throw new RequestConflictException("Two verified evidence assets are required.");
        }
    }

    private String encode(Object value) {
        try {
            String encoded = json.writeValueAsString(value);
            if (encoded.length() > 64_000) throw new IllegalArgumentException("Review metadata is too large.");
            return encoded;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid review metadata.", exception);
        }
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
