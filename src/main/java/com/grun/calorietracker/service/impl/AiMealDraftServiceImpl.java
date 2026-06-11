package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiMealDraftConfirmItemRequestDto;
import com.grun.calorietracker.dto.AiMealDraftConfirmRequestDto;
import com.grun.calorietracker.dto.AiMealDraftConfirmResponseDto;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiMealDraftRejectRequestDto;
import com.grun.calorietracker.dto.AiPhotoMealDraftRequestDto;
import com.grun.calorietracker.dto.AiRequestHistoryDto;
import com.grun.calorietracker.dto.AiVoiceFoodDraftRequestDto;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AiMealDraftProviderClient;
import com.grun.calorietracker.service.AiMealDraftResponseValidator;
import com.grun.calorietracker.service.AiMealDraftSafetyService;
import com.grun.calorietracker.service.AiMealDraftService;
import com.grun.calorietracker.service.AiProviderConfigurationValidator;
import com.grun.calorietracker.service.FoodLogsService;
import com.grun.calorietracker.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AiMealDraftServiceImpl implements AiMealDraftService {

    private final AiProperties properties;
    private final List<AiMealDraftProviderClient> providerClients;
    private final AiRequestHistoryRepository aiRequestHistoryRepository;
    private final UserRepository userRepository;
    private final FoodItemRepository foodItemRepository;
    private final SubscriptionService subscriptionService;
    private final FoodLogsService foodLogsService;
    private final ObjectMapper objectMapper;
    private final AiProviderConfigurationValidator providerConfigurationValidator;
    private final AiMealDraftResponseValidator responseValidator;
    private final AiMealDraftSafetyService safetyService;

    @Override
    public AiMealDraftResponseDto createVoiceFoodDraft(String email, AiVoiceFoodDraftRequestDto request) {
        safetyService.validateVoiceRequest(request);
        return createDraft(email, AiRequestType.VOICE_FOOD_LOG, request, () -> activeProvider().createVoiceFoodDraft(request));
    }

    @Override
    public AiMealDraftResponseDto createPhotoMealDraft(String email, AiPhotoMealDraftRequestDto request) {
        safetyService.validatePhotoRequest(request);
        return createDraft(email, AiRequestType.PHOTO_MEAL_LOG, request, () -> activeProvider().createPhotoMealDraft(request));
    }

    @Override
    @Transactional
    public AiMealDraftConfirmResponseDto confirmDraft(String email, Long requestId, AiMealDraftConfirmRequestDto request) {
        UserEntity user = getUser(email);
        AiRequestHistoryEntity history = getOwnedDraft(requestId, user);
        List<FoodLogsDto> createdLogs = request.getItems().stream()
                .map(this::toFoodLogDto)
                .map(dto -> foodLogsService.addFoodLog(dto, email))
                .toList();
        history.setStatus(AiRequestStatus.CONFIRMED);
        history.setConfirmationPayload(writeJson(createdLogs));
        history.setCorrectionSummary(writeJson(toCorrectionSummary(history.getOutputPayload(), request, createdLogs)));
        history.setConfirmedAt(LocalDateTime.now());
        aiRequestHistoryRepository.save(history);

        AiMealDraftConfirmResponseDto response = new AiMealDraftConfirmResponseDto();
        response.setRequestId(history.getId());
        response.setStatus(history.getStatus());
        response.setCreatedLogs(createdLogs);
        return response;
    }

    @Override
    @Transactional
    public AiRequestHistoryDto rejectDraft(String email, Long requestId, AiMealDraftRejectRequestDto request) {
        UserEntity user = getUser(email);
        AiRequestHistoryEntity history = getOwnedDraft(requestId, user);
        history.setStatus(AiRequestStatus.REJECTED);
        if (request != null) {
            history.setRejectionReason(request.getReason());
            history.setRejectionFeedback(normalizeFeedback(request.getFeedback()));
        }
        history.setRejectedAt(LocalDateTime.now());
        return toHistoryDto(aiRequestHistoryRepository.save(history));
    }

    @Override
    public List<AiRequestHistoryDto> listHistory(String email, int limit) {
        UserEntity user = getUser(email);
        int safeLimit = Math.max(1, Math.min(limit, properties.getMaxHistoryLimit()));
        return aiRequestHistoryRepository.findByUserOrderByCreatedAtDesc(user, PageRequest.of(0, safeLimit))
                .stream()
                .map(this::toHistoryDto)
                .toList();
    }

    private AiMealDraftResponseDto createDraft(String email,
                                               AiRequestType requestType,
                                               Object request,
                                               DraftSupplier supplier) {
        providerConfigurationValidator.validateConfiguredForDraft();
        UserEntity user = getUser(email);
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.AI_WORKOUT_PLANNER);

        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setUser(user);
        history.setRequestType(requestType);
        history.setProvider(properties.getProvider());
        history.setModel(properties.getModel());
        history.setInputPayload(writeJson(toPrivacySafeInputPayload(requestType, request)));
        history.setCreatedAt(LocalDateTime.now());
        history.setQuotaConsumed(false);

        long startedAt = System.nanoTime();
        try {
            AiMealDraftResponseDto response = responseValidator.validateAndNormalize(
                    supplier.get(),
                    requestType,
                    properties.getProvider(),
                    properties.getModel()
            );
            response.setSafety(safetyService.reviewProviderResponse(response, requestType));
            matchFoodCatalog(response, user);
            SubscriptionDto quota = subscriptionService.consumeAiQuota(email);
            response.setAiRemainingThisPeriod(quota.getAiRemainingThisPeriod());
            history.setStatus(AiRequestStatus.DRAFT_CREATED);
            history.setOutputPayload(writeJson(response));
            history.setQuotaConsumed(true);
            history.setQuotaConsumedAmount(1);
            history.setLatencyMs(elapsedMs(startedAt));
            AiRequestHistoryEntity saved = aiRequestHistoryRepository.save(history);
            response.setRequestId(saved.getId());
            return response;
        } catch (RuntimeException ex) {
            history.setStatus(AiRequestStatus.FAILED);
            history.setErrorMessage(ex.getMessage());
            history.setLatencyMs(elapsedMs(startedAt));
            aiRequestHistoryRepository.save(history);
            throw ex;
        }
    }

    private AiMealDraftProviderClient activeProvider() {
        Map<AiProvider, AiMealDraftProviderClient> clients = new EnumMap<>(AiProvider.class);
        for (AiMealDraftProviderClient client : providerClients) {
            clients.put(client.provider(), client);
        }
        AiMealDraftProviderClient client = clients.get(properties.getProvider());
        if (client == null) {
            throw new IllegalArgumentException("AI provider is not configured: " + properties.getProvider());
        }
        return client;
    }

    private void matchFoodCatalog(AiMealDraftResponseDto response, UserEntity user) {
        if (response.getItems() == null) {
            return;
        }
        for (var item : response.getItems()) {
            if (item.getMatchedFoodItemId() != null) {
                item.setReviewRequired(requiresReview(item.getConfidence()));
                item.setMatchReason(Boolean.TRUE.equals(item.getReviewRequired()) ? "LOW_CONFIDENCE" : "PROVIDER_MATCHED");
                continue;
            }
            if (item.getName() == null || item.getName().isBlank()) {
                item.setReviewRequired(true);
                item.setMatchReason("MISSING_NAME");
                continue;
            }
            List<com.grun.calorietracker.entity.FoodItemEntity> candidates =
                    foodItemRepository.findVisibleAiMatchCandidates(item.getName().trim(), user, PageRequest.of(0, 1));
            if (candidates.isEmpty()) {
                item.setReviewRequired(true);
                item.setMatchReason("NO_CATALOG_MATCH");
                continue;
            }
            var candidate = candidates.get(0);
            item.setMatchedFoodItemId(candidate.getId());
            if (candidate.getVerificationStatus() == VerificationStatus.VERIFIED && !requiresReview(item.getConfidence())) {
                item.setReviewRequired(false);
                item.setMatchReason("VERIFIED_CATALOG_MATCH");
            } else {
                item.setReviewRequired(true);
                item.setMatchReason("CATALOG_MATCH_REQUIRES_REVIEW");
            }
        }
    }

    private boolean requiresReview(Double confidence) {
        return confidence == null || confidence < 0.75;
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private AiRequestHistoryEntity getOwnedDraft(Long requestId, UserEntity user) {
        AiRequestHistoryEntity history = aiRequestHistoryRepository.findByIdAndUser(requestId, user)
                .orElseThrow(() -> new IllegalArgumentException("AI meal draft was not found."));
        if (history.getStatus() != AiRequestStatus.DRAFT_CREATED) {
            throw new IllegalArgumentException("AI meal draft is not open for confirmation.");
        }
        return history;
    }

    private FoodLogsDto toFoodLogDto(AiMealDraftConfirmItemRequestDto item) {
        FoodLogsDto dto = new FoodLogsDto();
        dto.setFoodItemId(item.getFoodItemId());
        dto.setPortionSize(item.getPortionSize());
        dto.setPortionUnit(item.getPortionUnit());
        dto.setMealType(item.getMealType());
        dto.setLogDate(item.getLogDate());
        return dto;
    }

    private AiRequestHistoryDto toHistoryDto(AiRequestHistoryEntity entity) {
        AiRequestHistoryDto dto = new AiRequestHistoryDto();
        dto.setId(entity.getId());
        dto.setRequestType(entity.getRequestType());
        dto.setProvider(entity.getProvider());
        dto.setModel(entity.getModel());
        dto.setStatus(entity.getStatus());
        dto.setQuotaConsumed(entity.getQuotaConsumed());
        dto.setLatencyMs(entity.getLatencyMs());
        dto.setTotalTokens(entity.getTotalTokens());
        dto.setEstimatedCost(entity.getEstimatedCost());
        dto.setCostCurrency(entity.getCostCurrency());
        dto.setRejectionReason(entity.getRejectionReason());
        dto.setHasRejectionFeedback(entity.getRejectionFeedback() != null && !entity.getRejectionFeedback().isBlank());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("AI payload could not be serialized.");
        }
    }

    private Map<String, Object> toCorrectionSummary(String originalOutputPayload,
                                                     AiMealDraftConfirmRequestDto request,
                                                     List<FoodLogsDto> createdLogs) {
        AiMealDraftResponseDto originalDraft = readOriginalDraft(originalOutputPayload);
        List<Long> suggestedFoodIds = originalDraft == null || originalDraft.getItems() == null
                ? List.of()
                : originalDraft.getItems().stream().map(item -> item.getMatchedFoodItemId()).toList();
        List<Double> suggestedQuantities = originalDraft == null || originalDraft.getItems() == null
                ? List.of()
                : originalDraft.getItems().stream().map(item -> item.getQuantity()).toList();
        List<Long> confirmedFoodIds = request.getItems().stream().map(AiMealDraftConfirmItemRequestDto::getFoodItemId).toList();
        List<Double> confirmedQuantities = request.getItems().stream().map(AiMealDraftConfirmItemRequestDto::getPortionSize).toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("suggestedItemCount", suggestedFoodIds.size());
        summary.put("confirmedItemCount", request.getItems().size());
        summary.put("createdLogCount", createdLogs.size());
        summary.put("itemCountChanged", suggestedFoodIds.size() != request.getItems().size());
        summary.put("foodItemsChanged", !Objects.equals(suggestedFoodIds, confirmedFoodIds));
        summary.put("portionsChanged", portionsChanged(suggestedQuantities, confirmedQuantities));
        summary.put("mealTypeChanged", mealTypeChanged(originalDraft, request));
        summary.put("logDateChanged", logDateChanged(originalDraft, request));
        return summary;
    }

    private AiMealDraftResponseDto readOriginalDraft(String originalOutputPayload) {
        if (originalOutputPayload == null || originalOutputPayload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(originalOutputPayload, AiMealDraftResponseDto.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private boolean portionsChanged(List<Double> suggestedQuantities, List<Double> confirmedQuantities) {
        if (suggestedQuantities.size() != confirmedQuantities.size()) {
            return true;
        }
        for (int index = 0; index < suggestedQuantities.size(); index++) {
            Double suggested = suggestedQuantities.get(index);
            Double confirmed = confirmedQuantities.get(index);
            if (suggested == null || confirmed == null || Math.abs(suggested - confirmed) > 0.01) {
                return true;
            }
        }
        return false;
    }

    private boolean mealTypeChanged(AiMealDraftResponseDto originalDraft, AiMealDraftConfirmRequestDto request) {
        if (originalDraft == null || originalDraft.getSuggestedMealType() == null) {
            return false;
        }
        return request.getItems().stream()
                .map(AiMealDraftConfirmItemRequestDto::getMealType)
                .filter(Objects::nonNull)
                .anyMatch(mealType -> !mealType.equalsIgnoreCase(originalDraft.getSuggestedMealType()));
    }

    private boolean logDateChanged(AiMealDraftResponseDto originalDraft, AiMealDraftConfirmRequestDto request) {
        if (originalDraft == null || originalDraft.getSuggestedLogDate() == null) {
            return false;
        }
        return request.getItems().stream()
                .map(AiMealDraftConfirmItemRequestDto::getLogDate)
                .filter(Objects::nonNull)
                .anyMatch(logDate -> !logDate.equals(originalDraft.getSuggestedLogDate()));
    }

    private Map<String, Object> toPrivacySafeInputPayload(AiRequestType requestType, Object request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestType", requestType);
        if (request instanceof AiVoiceFoodDraftRequestDto voiceRequest) {
            payload.put("transcriptLength", voiceRequest.getTranscript() == null ? 0 : voiceRequest.getTranscript().length());
            payload.put("locale", voiceRequest.getLocale());
            payload.put("mealType", voiceRequest.getMealType());
            payload.put("logDate", voiceRequest.getLogDate());
            return payload;
        }
        if (request instanceof AiPhotoMealDraftRequestDto photoRequest) {
            payload.put("imageReferenceType", imageReferenceType(photoRequest.getImageReference()));
            payload.put("imageReferenceLength", photoRequest.getImageReference() == null ? 0 : photoRequest.getImageReference().length());
            payload.put("hasUserNote", photoRequest.getUserNote() != null && !photoRequest.getUserNote().isBlank());
            payload.put("userNoteLength", photoRequest.getUserNote() == null ? 0 : photoRequest.getUserNote().length());
            payload.put("mealType", photoRequest.getMealType());
            payload.put("logDate", photoRequest.getLogDate());
            return payload;
        }
        payload.put("inputClass", request == null ? null : request.getClass().getSimpleName());
        return payload;
    }

    private String imageReferenceType(String imageReference) {
        if (imageReference == null || imageReference.isBlank()) {
            return "EMPTY";
        }
        String value = imageReference.trim().toLowerCase();
        if (value.startsWith("https://")) {
            return "HTTPS";
        }
        if (value.startsWith("s3://")) {
            return "S3";
        }
        return "OTHER";
    }

    private String normalizeFeedback(String feedback) {
        if (feedback == null || feedback.isBlank()) {
            return null;
        }
        return feedback.trim();
    }

    @FunctionalInterface
    private interface DraftSupplier {
        AiMealDraftResponseDto get();
    }
}
