package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiMealDraftAlternativeCandidateDto;
import com.grun.calorietracker.dto.AiMealDraftConfirmItemRequestDto;
import com.grun.calorietracker.dto.AiMealDraftItemDto;
import com.grun.calorietracker.dto.AiMealDraftConfirmRequestDto;
import com.grun.calorietracker.dto.AiMealDraftConfirmResponseDto;
import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiMealDraftRejectRequestDto;
import com.grun.calorietracker.dto.AiPhotoMealDraftRequestDto;
import com.grun.calorietracker.dto.AiRequestHistoryDto;
import com.grun.calorietracker.dto.AiVoiceFoodDraftRequestDto;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.dto.RecipeNutritionDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.dto.AiUsageMetadataCarrier;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.enums.FoodLogSource;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AiMealDraftProviderClient;
import com.grun.calorietracker.service.AiMealDraftResponseValidator;
import com.grun.calorietracker.service.AiMealDraftSafetyService;
import com.grun.calorietracker.service.AiMealDraftService;
import com.grun.calorietracker.service.AiProviderConfigurationValidator;
import com.grun.calorietracker.service.FoodLogsService;
import com.grun.calorietracker.service.support.AiSafeResponseBuilder;
import com.grun.calorietracker.service.support.AiIdempotencySupport;
import com.grun.calorietracker.service.support.AiUxContractFactory;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import com.grun.calorietracker.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final SubscriptionService subscriptionService;
    private final FoodLogsService foodLogsService;
    private final ObjectMapper objectMapper;
    private final AiProviderConfigurationValidator providerConfigurationValidator;
    private final AiMealDraftResponseValidator responseValidator;
    private static final String AI_REJECTION_ALERT_TYPE = "ai_rejection_alert";
    private static final int MAX_CONFIRM_ITEMS = 50;
    private static final double MAX_CONFIRM_PORTION_SIZE = 10000.0;

    private final AiMealDraftSafetyService safetyService;
    private final NotificationRepository notificationRepository;

    @Override
    public AiMealDraftResponseDto createVoiceFoodDraft(String email, String idempotencyKey, AiVoiceFoodDraftRequestDto request) {
        safetyService.validateVoiceRequest(request);
        return createDraft(email, idempotencyKey, AiRequestType.VOICE_FOOD_LOG, request, () -> activeProvider().createVoiceFoodDraft(request));
    }

    @Override
    public AiMealDraftResponseDto createPhotoMealDraft(String email, String idempotencyKey, AiPhotoMealDraftRequestDto request) {
        safetyService.validatePhotoRequest(request);
        return createDraft(email, idempotencyKey, AiRequestType.PHOTO_MEAL_LOG, request, () -> activeProvider().createPhotoMealDraft(request));
    }

    @Override
    @Transactional(readOnly = true)
    public AiMealDraftResponseDto getDraft(String email, Long requestId) {
        UserEntity user = getUser(email);
        AiRequestHistoryEntity history = aiRequestHistoryRepository.findByIdAndUser(requestId, user)
                .orElseThrow(() -> new IllegalArgumentException("AI meal draft was not found."));
        if (history.getRequestType() != AiRequestType.VOICE_FOOD_LOG
                && history.getRequestType() != AiRequestType.PHOTO_MEAL_LOG) {
            throw new IllegalArgumentException("AI meal draft was not found.");
        }
        AiMealDraftResponseDto draft = readOriginalDraft(history.getOutputPayload());
        if (draft == null || draft.getItems() == null || draft.getItems().isEmpty()) {
            throw new IllegalArgumentException("AI meal draft result is unavailable.");
        }
        draft.setRequestId(history.getId());
        draft.setRequestType(history.getRequestType());
        draft.setStatus(history.getStatus());
        if (draft.getUx() == null) {
            int consumed = history.getQuotaConsumedAmount() == null ? 0 : history.getQuotaConsumedAmount();
            draft.setUx(AiUxContractFactory.history(
                    history.getStatus(),
                    true,
                    consumed,
                    Boolean.TRUE.equals(history.getQuotaConsumed()),
                    user.getPreferredLanguage()
            ));
        }
        return draft;
    }

    @Override
    @Transactional
    public AiMealDraftConfirmResponseDto confirmDraft(String email, Long requestId, AiMealDraftConfirmRequestDto request) {
        UserEntity user = getUser(email);
        AiRequestHistoryEntity history = getOwnedDraftForConfirm(requestId, user);
        if (history.getStatus() == AiRequestStatus.CONFIRMED) {
            return alreadyConfirmedResponse(history);
        }

        validateConfirmRequest(request);
        AiMealDraftResponseDto originalDraft = readOriginalDraft(history.getOutputPayload());
        List<FoodLogsDto> createdLogs = new ArrayList<>();
        for (int index = 0; index < request.getItems().size(); index++) {
            AiMealDraftConfirmItemRequestDto confirmedItem = request.getItems().get(index);
            AiMealDraftItemDto originalItem = originalItemFor(originalDraft, index, confirmedItem);
            createdLogs.add(createFoodLogFromConfirmedItem(confirmedItem, originalItem, history, email));
        }
        history.setStatus(AiRequestStatus.CONFIRMED);
        history.setConfirmationPayload(writeJson(createdLogs));
        history.setCorrectionSummary(writeJson(toCorrectionSummary(history.getOutputPayload(), request, createdLogs)));
        history.setConfirmedAt(LocalDateTime.now());
        aiRequestHistoryRepository.save(history);

        AiMealDraftConfirmResponseDto response = new AiMealDraftConfirmResponseDto();
        response.setRequestId(history.getId());
        response.setStatus(history.getStatus());
        response.setCreatedLogs(createdLogs);
        response.setAlreadyConfirmed(false);
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
        AiRequestHistoryEntity saved = aiRequestHistoryRepository.save(history);
        notifyAdminsAboutRejectedDraft(saved);
        return toHistoryDto(saved);
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
                                               String idempotencyKey,
                                               AiRequestType requestType,
                                               Object request,
                                               DraftSupplier supplier) {
        providerConfigurationValidator.validateConfiguredForDraft();
        UserEntity user = getUser(email);
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.AI_MEAL_DRAFTS);
        enrichRequestContext(request, user);
        String key = AiIdempotencySupport.normalizeKey(idempotencyKey);
        AiMealDraftResponseDto previous = existingDraft(user, requestType, key);
        if (previous != null) {
            return previous;
        }

        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setUser(user);
        history.setRequestType(requestType);
        history.setProvider(properties.getProvider());
        history.setModel(properties.getModel());
        history.setPromptVersion(properties.getPromptVersion());
        history.setStatus(AiRequestStatus.PROCESSING);
        history.setIdempotencyKey(key);
        history.setInputPayload(writeJson(toPrivacySafeInputPayload(requestType, request)));
        history.setCreatedAt(LocalDateTime.now());
        history.setQuotaConsumed(false);

        try {
            AiRequestHistoryEntity reserved = aiRequestHistoryRepository.save(history);
            if (reserved != null) {
                history = reserved;
            }
        } catch (DataIntegrityViolationException ex) {
            AiMealDraftResponseDto concurrent = existingDraft(user, requestType, key);
            if (concurrent != null) {
                return concurrent;
            }
            throw new RequestConflictException(
                    "An AI meal-draft request with this key is already processing.");
        }

        int creditCost = subscriptionService.resolveAiCreditCost(email, SubscriptionFeature.AI_MEAL_DRAFTS);
        long startedAt = System.nanoTime();
        boolean charged = false;
        try {
            SubscriptionDto quota = subscriptionService.consumeAiQuota(email, creditCost);
            charged = true;
            AiMealDraftResponseDto response = responseValidator.validateAndNormalize(
                    supplier.get(),
                    requestType,
                    properties.getProvider(),
                    properties.getModel()
            );
            response.setSafety(safetyService.reviewProviderResponse(response, requestType));
            normalizeItemsAsAiSnapshot(response);
            response.setAiRemainingThisPeriod(quota.getAiRemainingThisPeriod());
            response.setUx(AiUxContractFactory.success(
                    AiRequestStatus.DRAFT_CREATED,
                    true,
                    creditCost,
                    quota,
                    user.getPreferredLanguage()
            ));
            copyUsageMetadata(response, history);
            history.setStatus(AiRequestStatus.DRAFT_CREATED);
            history.setOutputPayload(writeJson(response));
            history.setQuotaConsumed(true);
            history.setQuotaConsumedAmount(creditCost);
            history.setLatencyMs(elapsedMs(startedAt));
            AiRequestHistoryEntity saved = aiRequestHistoryRepository.save(history);
            response.setRequestId(saved.getId());
            return response;
        } catch (RuntimeException ex) {
            boolean refunded = !charged || refundConsumedQuota(user, creditCost);
            history.setStatus(AiRequestStatus.FAILED);
            history.setErrorMessage(ex.getMessage());
            history.setOutputPayload(writeJson(AiSafeResponseBuilder.failurePayload(requestType, true, creditCost, !refunded, user.getPreferredLanguage())));
            history.setQuotaConsumed(!refunded);
            history.setQuotaConsumedAmount(refunded ? 0 : creditCost);
            history.setLatencyMs(elapsedMs(startedAt));
            aiRequestHistoryRepository.save(history);
            throw ex;
        }
    }

    private AiMealDraftResponseDto existingDraft(
            UserEntity user, AiRequestType requestType, String idempotencyKey) {
        return aiRequestHistoryRepository.findByUserAndRequestTypeAndIdempotencyKey(
                        user, requestType, idempotencyKey)
                .map(history -> AiIdempotencySupport.replayOrReject(
                        history,
                        stored -> {
                            AiMealDraftResponseDto draft = readOriginalDraft(stored.getOutputPayload());
                            draft.setRequestId(stored.getId());
                            draft.setRequestType(stored.getRequestType());
                            draft.setStatus(stored.getStatus());
                            if (draft.getUx() == null) {
                                int consumed = stored.getQuotaConsumedAmount() == null
                                        ? 0 : stored.getQuotaConsumedAmount();
                                draft.setUx(AiUxContractFactory.history(
                                        stored.getStatus(),
                                        true,
                                        consumed,
                                        Boolean.TRUE.equals(stored.getQuotaConsumed()),
                                        user.getPreferredLanguage()
                                ));
                            }
                            return draft;
                        },
                        "AI meal-draft"
                )).orElse(null);
    }
    private void normalizeItemsAsAiSnapshot(AiMealDraftResponseDto response) {
        if (response.getItems() == null) {
            return;
        }
        for (var item : response.getItems()) {
            item.setMatchedFoodItemId(null);
            item.setReviewRequired(true);
            if (item.getMatchReason() == null || item.getMatchReason().isBlank()
                    || item.getMatchReason().contains("CATALOG")
                    || item.getMatchReason().contains("MATCH")) {
                item.setMatchReason("AI_SNAPSHOT");
            }
        }
    }
    private void enrichRequestContext(Object request, UserEntity user) {
        Map<String, Object> context = toUserContext(user);
        if (request instanceof AiVoiceFoodDraftRequestDto voiceRequest) {
            voiceRequest.setLocale(resolveOutputLocale(voiceRequest.getLocale(), user));
            voiceRequest.setUserContext(context);
        } else if (request instanceof AiPhotoMealDraftRequestDto photoRequest) {
            photoRequest.setLocale(resolveOutputLocale(photoRequest.getLocale(), user));
            photoRequest.setUserContext(context);
        }
    }

    private String resolveOutputLocale(String requestedLocale, UserEntity user) {
        String normalized = requestedLocale == null
                ? ""
                : requestedLocale.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.equals("tr") || normalized.startsWith("tr-") || normalized.startsWith("tr_")) {
            return "tr";
        }
        if (normalized.equals("en") || normalized.startsWith("en-") || normalized.startsWith("en_")) {
            return "en";
        }
        return user.getPreferredLanguage() != null && user.getPreferredLanguage().name().equals("TR")
                ? "tr"
                : "en";
    }

    private Map<String, Object> toUserContext(UserEntity user) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("age", user.getAge());
        context.put("gender", user.getGender());
        context.put("heightCm", user.getHeight());
        context.put("weightKg", user.getWeight());
        context.put("bodyFatPercentage", user.getBodyFatPercentage());
        context.put("bmi", user.getBmi());
        context.put("marketRegion", user.getMarketRegion());
        context.put("preferredLanguage", user.getPreferredLanguage());
        context.put("timeZone", user.getTimeZone());
        context.put("unitPreference", user.getUnitPreference());
        return context;
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
    private AiRequestHistoryEntity getOwnedDraftForConfirm(Long requestId, UserEntity user) {
        if (requestId == null || requestId <= 0) {
            throwConfirmValidation("INVALID_REQUEST_ID");
        }
        AiRequestHistoryEntity history = aiRequestHistoryRepository.findByIdAndUser(requestId, user)
                .orElseThrow(() -> new IllegalArgumentException("DRAFT_NOT_FOUND"));
        if (history.getStatus() == AiRequestStatus.CONFIRMED) {
            return history;
        }
        if (history.getStatus() != AiRequestStatus.DRAFT_CREATED) {
            throwConfirmValidation("DRAFT_NOT_CONFIRMABLE");
        }
        return history;
    }

    private AiMealDraftConfirmResponseDto alreadyConfirmedResponse(AiRequestHistoryEntity history) {
        AiMealDraftConfirmResponseDto response = new AiMealDraftConfirmResponseDto();
        response.setRequestId(history.getId());
        response.setStatus(AiRequestStatus.CONFIRMED);
        response.setCreatedLogs(readConfirmedLogs(history.getConfirmationPayload()));
        response.setAlreadyConfirmed(true);
        return response;
    }

    private List<FoodLogsDto> readConfirmedLogs(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<List<FoodLogsDto>>() { });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private void validateConfirmRequest(AiMealDraftConfirmRequestDto request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throwConfirmValidation("MEAL_DRAFT_ITEMS_REQUIRED");
        }
        if (request.getItems().size() > MAX_CONFIRM_ITEMS) {
            throwConfirmValidation("TOO_MANY_ITEMS");
        }
        String mealType = null;
        LocalDateTime logDate = null;
        for (AiMealDraftConfirmItemRequestDto item : request.getItems()) {
            validateConfirmItem(item);
            String currentMealType = item.getMealType().trim().toUpperCase();
            if (mealType == null) {
                mealType = currentMealType;
            } else if (!mealType.equals(currentMealType)) {
                throwConfirmValidation("INCONSISTENT_MEAL_CONTEXT");
            }
            if (logDate == null) {
                logDate = item.getLogDate();
            } else if (!logDate.equals(item.getLogDate())) {
                throwConfirmValidation("INCONSISTENT_MEAL_CONTEXT");
            }
        }
    }

    private void validateConfirmItem(AiMealDraftConfirmItemRequestDto item) {
        if (item == null) {
            throwConfirmValidation("INVALID_MEAL_DRAFT_ITEM");
        }
        if (item.getMealType() == null || item.getMealType().isBlank()) {
            throwConfirmValidation("MEAL_TYPE_REQUIRED");
        }
        if (!item.getMealType().matches("(?i)BREAKFAST|LUNCH|DINNER|SNACK")) {
            throwConfirmValidation("MEAL_TYPE_INVALID");
        }
        if (item.getLogDate() == null) {
            throwConfirmValidation("LOG_DATE_REQUIRED");
        }
        if (item.getPortionSize() == null || !Double.isFinite(item.getPortionSize())
                || item.getPortionSize() <= 0 || item.getPortionSize() > MAX_CONFIRM_PORTION_SIZE) {
            throwConfirmValidation("PORTION_SIZE_INVALID");
        }
        if (item.getPortionUnit() == null) {
            throwConfirmValidation("PORTION_UNIT_REQUIRED");
        }
        if (item.getSourceItemIndex() != null && item.getSourceItemIndex() < 0) {
            throwConfirmValidation("SOURCE_ITEM_INDEX_INVALID");
        }
        if (item.getAlternativeCandidateIndex() != null
                && (item.getAlternativeCandidateIndex() < 0 || item.getAlternativeCandidateIndex() > 1)) {
            throwConfirmValidation("ALTERNATIVE_CANDIDATE_INVALID");
        }
        boolean hasCatalogSource = item.getFoodItemId() != null;
        boolean hasEstimateSource = normalizeFoodDisplayName(item.getEstimatedFoodName()) != null;
        if (hasCatalogSource == hasEstimateSource) {
            throwConfirmValidation("ITEM_SOURCE_INVALID");
        }
        if (hasCatalogSource && item.getFoodItemId() <= 0) {
            throwConfirmValidation("FOOD_ITEM_NOT_FOUND");
        }
        if (hasCatalogSource && item.getAlternativeCandidateIndex() != null) {
            throwConfirmValidation("ITEM_SOURCE_INVALID");
        }
        if (hasEstimateSource) {
            if (normalizeFoodDisplayName(item.getEstimatedFoodName()).length() > 200) {
                throwConfirmValidation("ESTIMATED_FOOD_NAME_INVALID");
            }
            if (item.getAlternativeCandidateIndex() == null) {
                validateEstimatedMacro(item.getEstimatedCalories());
                validateEstimatedMacro(item.getEstimatedProtein());
                validateEstimatedMacro(item.getEstimatedCarbs());
                validateEstimatedMacro(item.getEstimatedFat());
                validateOptionalNutrition(item.getEstimatedNutrition());
            }
        }
        if (item.getConfidence() != null && (!Double.isFinite(item.getConfidence())
                || item.getConfidence() < 0 || item.getConfidence() > 1)) {
            throwConfirmValidation("CONFIDENCE_INVALID");
        }
    }

    private void validateEstimatedMacro(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0) {
            throwConfirmValidation("ESTIMATED_NUTRITION_INVALID");
        }
    }

    private void throwConfirmValidation(String code) {
        throw new IllegalArgumentException(code);
    }

    private FoodLogsDto createFoodLogFromConfirmedItem(AiMealDraftConfirmItemRequestDto item,
                                                       AiMealDraftItemDto originalItem,
                                                       AiRequestHistoryEntity history,
                                                       String email) {
        if (item.getFoodItemId() != null) {
            return foodLogsService.addFoodLog(toFoodLogDto(item, history), email);
        }
        RecipeNutritionDto nutrition = resolveConfirmedNutrition(item, originalItem);
        validateAiEstimateConfirmation(item, nutrition);
        return foodLogsService.addAiEstimateFoodLog(toAiEstimateFoodLogDto(item, nutrition, history, originalItem), email);
    }

    private FoodLogsDto toAiEstimateFoodLogDto(AiMealDraftConfirmItemRequestDto item,
                                               RecipeNutritionDto nutrition,
                                               AiRequestHistoryEntity history,
                                               AiMealDraftItemDto originalItem) {
        FoodLogsDto dto = new FoodLogsDto();
        dto.setDisplayName(normalizeFoodDisplayName(item.getEstimatedFoodName()));
        dto.setPortionSize(item.getPortionSize());
        dto.setPortionUnit(item.getPortionUnit());
        dto.setNormalizedPortionGrams(resolveNormalizedPortionGrams(item, originalItem));
        dto.setSnapshotCalories(nutrition.getCalories());
        dto.setSnapshotProtein(nutrition.getProtein());
        dto.setSnapshotCarbs(nutrition.getCarbs());
        dto.setSnapshotFat(nutrition.getFat());
        dto.setSnapshotFiber(nutrition.getFiber());
        dto.setSnapshotSugar(nutrition.getSugar());
        dto.setSnapshotSaturatedFat(nutrition.getSaturatedFat());
        dto.setSnapshotSodium(nutrition.getSodium());
        dto.setSnapshotPotassium(nutrition.getPotassium());
        dto.setSnapshotCholesterol(nutrition.getCholesterol());
        dto.setSnapshotCalcium(nutrition.getCalcium());
        dto.setSnapshotIron(nutrition.getIron());
        dto.setSnapshotMagnesium(nutrition.getMagnesium());
        dto.setSnapshotZinc(nutrition.getZinc());
        dto.setSnapshotVitaminA(nutrition.getVitaminA());
        dto.setSnapshotVitaminC(nutrition.getVitaminC());
        dto.setSnapshotVitaminD(nutrition.getVitaminD());
        dto.setSnapshotVitaminE(nutrition.getVitaminE());
        dto.setSnapshotVitaminB12(nutrition.getVitaminB12());
        dto.setMealType(item.getMealType());
        dto.setLogDate(item.getLogDate());
        dto.setSource(resolveAiEstimateSource(history.getRequestType()));
        dto.setAiRequestId(history.getId());
        dto.setAiConfidence(item.getAlternativeCandidateIndex() == null || originalItem == null
                ? item.getConfidence()
                : originalItem.getConfidence());
        return dto;
    }

    private Double resolveNormalizedPortionGrams(AiMealDraftConfirmItemRequestDto item,
                                                 AiMealDraftItemDto originalItem) {
        if (item.getPortionUnit() == com.grun.calorietracker.enums.FoodPortionUnit.GRAM) {
            return item.getPortionSize();
        }
        if (originalItem != null && originalItem.getEstimatedTotalWeightGrams() != null
                && originalItem.getEstimatedTotalWeightGrams() > 0) {
            return originalItem.getEstimatedTotalWeightGrams() * portionScale(item, originalItem);
        }
        return null;
    }
    private void validateAiEstimateConfirmation(AiMealDraftConfirmItemRequestDto item,
                                                RecipeNutritionDto nutrition) {
        if (item.getEstimatedFoodName() == null || item.getEstimatedFoodName().isBlank()) {
            throw new IllegalArgumentException("Estimated food name is required when no catalog food item is selected.");
        }
        validateRequiredNutrition(nutrition);
        validateOptionalNutrition(nutrition);
        if (item.getConfidence() != null && (item.getConfidence() < 0 || item.getConfidence() > 1)) {
            throw new IllegalArgumentException("AI confidence must be between 0 and 1.");
        }
    }

    private RecipeNutritionDto resolveConfirmedNutrition(AiMealDraftConfirmItemRequestDto item,
                                                         AiMealDraftItemDto originalItem) {
        RecipeNutritionDto originalNutrition = nutritionFromOriginal(originalItem);
        if (item.getAlternativeCandidateIndex() != null) {
            if (originalNutrition == null) {
                throwConfirmValidation("ALTERNATIVE_NUTRITION_UNAVAILABLE");
            }
            return copyAndScaleNutrition(originalNutrition, portionScale(item, originalItem));
        }
        if (item.getEstimatedNutrition() != null) {
            return copyAndScaleNutrition(item.getEstimatedNutrition(), 1.0);
        }
        if (originalNutrition != null) {
            return copyAndScaleNutrition(originalNutrition, portionScale(item, originalItem));
        }
        RecipeNutritionDto legacy = new RecipeNutritionDto();
        legacy.setCalories(item.getEstimatedCalories());
        legacy.setProtein(item.getEstimatedProtein());
        legacy.setCarbs(item.getEstimatedCarbs());
        legacy.setFat(item.getEstimatedFat());
        return legacy;
    }

    private RecipeNutritionDto nutritionFromOriginal(AiMealDraftItemDto originalItem) {
        if (originalItem == null) {
            return null;
        }
        if (originalItem.getEstimatedNutrition() != null) {
            return originalItem.getEstimatedNutrition();
        }
        if (originalItem.getEstimatedCalories() == null
                && originalItem.getEstimatedProtein() == null
                && originalItem.getEstimatedCarbs() == null
                && originalItem.getEstimatedFat() == null) {
            return null;
        }
        RecipeNutritionDto nutrition = new RecipeNutritionDto();
        nutrition.setCalories(originalItem.getEstimatedCalories());
        nutrition.setProtein(originalItem.getEstimatedProtein());
        nutrition.setCarbs(originalItem.getEstimatedCarbs());
        nutrition.setFat(originalItem.getEstimatedFat());
        return nutrition;
    }

    private double portionScale(AiMealDraftConfirmItemRequestDto item, AiMealDraftItemDto originalItem) {
        if (originalItem == null || item.getPortionSize() == null || item.getPortionSize() <= 0
                || item.getPortionUnit() == null) {
            return 1.0;
        }
        if (originalItem.getQuantity() != null && originalItem.getQuantity() > 0
                && equivalentUnit(originalItem.getUnit(), item.getPortionUnit().name())) {
            return item.getPortionSize() / originalItem.getQuantity();
        }
        if (item.getPortionUnit() == com.grun.calorietracker.enums.FoodPortionUnit.GRAM
                && originalItem.getEstimatedTotalWeightGrams() != null
                && originalItem.getEstimatedTotalWeightGrams() > 0) {
            return item.getPortionSize() / originalItem.getEstimatedTotalWeightGrams();
        }
        if (item.getPortionUnit() == com.grun.calorietracker.enums.FoodPortionUnit.PIECE
                && originalItem.getDetectedPieceCount() != null
                && originalItem.getDetectedPieceCount() > 0) {
            return item.getPortionSize() / originalItem.getDetectedPieceCount();
        }
        return 1.0;
    }

    private boolean equivalentUnit(String providerUnit, String confirmedUnit) {
        if (providerUnit == null || confirmedUnit == null) {
            return false;
        }
        String normalized = providerUnit.trim().toUpperCase().replace(" ", "_");
        normalized = switch (normalized) {
            case "G", "GRAMS" -> "GRAM";
            case "ML", "MILLILITERS", "MILLILITRES" -> "MILLILITER";
            case "SERVINGS" -> "SERVING";
            case "PIECES", "PCS" -> "PIECE";
            case "SLICES" -> "SLICE";
            case "TBSP" -> "TABLESPOON";
            case "TSP" -> "TEASPOON";
            default -> normalized;
        };
        return normalized.equals(confirmedUnit);
    }

    private RecipeNutritionDto copyAndScaleNutrition(RecipeNutritionDto source, double scale) {
        return new RecipeNutritionDto(
                scale(source.getCalories(), scale),
                scale(source.getProtein(), scale),
                scale(source.getCarbs(), scale),
                scale(source.getFat(), scale),
                scale(source.getFiber(), scale),
                scale(source.getSugar(), scale),
                scale(source.getSaturatedFat(), scale),
                scale(source.getSodium(), scale),
                scale(source.getPotassium(), scale),
                scale(source.getCholesterol(), scale),
                scale(source.getCalcium(), scale),
                scale(source.getIron(), scale),
                scale(source.getMagnesium(), scale),
                scale(source.getZinc(), scale),
                scale(source.getVitaminA(), scale),
                scale(source.getVitaminC(), scale),
                scale(source.getVitaminD(), scale),
                scale(source.getVitaminE(), scale),
                scale(source.getVitaminB12(), scale)
        );
    }

    private Double scale(Double value, double multiplier) {
        return value == null ? null : value * multiplier;
    }

    private void validateRequiredNutrition(RecipeNutritionDto nutrition) {
        if (nutrition == null) {
            throw new IllegalArgumentException("Estimated nutrition must be provided for unmatched AI items.");
        }
        validateNutritionValue(nutrition.getCalories(), "calories", true);
        validateNutritionValue(nutrition.getProtein(), "protein", true);
        validateNutritionValue(nutrition.getCarbs(), "carbohydrate", true);
        validateNutritionValue(nutrition.getFat(), "fat", true);
    }

    private void validateOptionalNutrition(RecipeNutritionDto nutrition) {
        if (nutrition == null) {
            return;
        }
        validateNutritionValue(nutrition.getFiber(), "fiber", false);
        validateNutritionValue(nutrition.getSugar(), "sugar", false);
        validateNutritionValue(nutrition.getSaturatedFat(), "saturated fat", false);
        validateNutritionValue(nutrition.getSodium(), "sodium", false);
        validateNutritionValue(nutrition.getPotassium(), "potassium", false);
        validateNutritionValue(nutrition.getCholesterol(), "cholesterol", false);
        validateNutritionValue(nutrition.getCalcium(), "calcium", false);
        validateNutritionValue(nutrition.getIron(), "iron", false);
        validateNutritionValue(nutrition.getMagnesium(), "magnesium", false);
        validateNutritionValue(nutrition.getZinc(), "zinc", false);
        validateNutritionValue(nutrition.getVitaminA(), "vitamin A", false);
        validateNutritionValue(nutrition.getVitaminC(), "vitamin C", false);
        validateNutritionValue(nutrition.getVitaminD(), "vitamin D", false);
        validateNutritionValue(nutrition.getVitaminE(), "vitamin E", false);
        validateNutritionValue(nutrition.getVitaminB12(), "vitamin B12", false);
    }

    private void validateNutritionValue(Double value, String field, boolean required) {
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("Estimated " + field + " must be provided for unmatched AI items.");
            }
            return;
        }
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("Estimated " + field + " must not be negative or non-finite.");
        }
    }

    private AiMealDraftItemDto originalItemFor(AiMealDraftResponseDto originalDraft,
                                                int index,
                                                AiMealDraftConfirmItemRequestDto confirmedItem) {
        if (originalDraft == null || originalDraft.getItems() == null || confirmedItem == null) {
            return null;
        }
        String confirmedName = normalizeFoodDisplayName(confirmedItem.getEstimatedFoodName());
        if (confirmedName == null) {
            return null;
        }
        int sourceIndex = confirmedItem.getSourceItemIndex() == null
                ? index
                : confirmedItem.getSourceItemIndex();
        if (sourceIndex < 0 || sourceIndex >= originalDraft.getItems().size()) {
            throwConfirmValidation("SOURCE_ITEM_INDEX_INVALID");
        }
        AiMealDraftItemDto sourceItem = originalDraft.getItems().get(sourceIndex);
        if (confirmedItem.getAlternativeCandidateIndex() != null) {
            return selectedAlternativeAsItem(sourceItem, confirmedItem.getAlternativeCandidateIndex(), confirmedName);
        }
        if (sameFoodName(sourceItem, confirmedName)) {
            return sourceItem;
        }
        if (confirmedItem.getSourceItemIndex() != null) {
            throwConfirmValidation("SOURCE_ITEM_NAME_MISMATCH");
        }
        return originalDraft.getItems().stream()
                .filter(item -> sameFoodName(item, confirmedName))
                .findFirst()
                .orElse(null);
    }

    private AiMealDraftItemDto selectedAlternativeAsItem(AiMealDraftItemDto sourceItem,
                                                          int alternativeIndex,
                                                          String confirmedName) {
        List<AiMealDraftAlternativeCandidateDto> alternatives = sourceItem.getAlternativeCandidates();
        if (alternatives == null || alternativeIndex < 0 || alternativeIndex >= alternatives.size()) {
            throwConfirmValidation("ALTERNATIVE_CANDIDATE_INVALID");
        }
        AiMealDraftAlternativeCandidateDto alternative = alternatives.get(alternativeIndex);
        if (!Objects.equals(normalizeFoodDisplayName(alternative.getName()), confirmedName)) {
            throwConfirmValidation("ALTERNATIVE_CANDIDATE_NAME_MISMATCH");
        }
        AiMealDraftItemDto selected = new AiMealDraftItemDto();
        selected.setName(normalizeFoodDisplayName(alternative.getName()));
        selected.setQuantity(alternative.getQuantity());
        selected.setUnit(alternative.getUnit());
        selected.setDetectedPieceCount(alternative.getDetectedPieceCount());
        selected.setEstimatedTotalWeightGrams(alternative.getEstimatedTotalWeightGrams());
        selected.setEstimatedNutrition(alternative.getEstimatedNutrition());
        if (alternative.getEstimatedNutrition() != null) {
            selected.setEstimatedCalories(alternative.getEstimatedNutrition().getCalories());
            selected.setEstimatedProtein(alternative.getEstimatedNutrition().getProtein());
            selected.setEstimatedCarbs(alternative.getEstimatedNutrition().getCarbs());
            selected.setEstimatedFat(alternative.getEstimatedNutrition().getFat());
        }
        selected.setNutritionEstimateNote(alternative.getNutritionEstimateNote());
        selected.setMatchReason(alternative.getMatchReason());
        selected.setConfidence(alternative.getConfidence());
        selected.setNeedsUserPortionConfirmation(true);
        return selected;
    }

    private boolean sameFoodName(AiMealDraftItemDto item, String confirmedName) {
        return item != null && Objects.equals(normalizeFoodDisplayName(item.getName()), confirmedName);
    }

    private String normalizeFoodDisplayName(String value) {
        return FoodProductNormalizationRules.normalizeProductDisplayName(value);
    }

    private FoodLogSource resolveAiEstimateSource(AiRequestType requestType) {
        if (requestType == AiRequestType.PHOTO_MEAL_LOG) {
            return FoodLogSource.AI_PHOTO;
        }
        if (requestType == AiRequestType.VOICE_FOOD_LOG) {
            return FoodLogSource.AI_VOICE;
        }
        return FoodLogSource.AI_ESTIMATE;
    }
    private FoodLogsDto toFoodLogDto(AiMealDraftConfirmItemRequestDto item, AiRequestHistoryEntity history) {
        FoodLogsDto dto = new FoodLogsDto();
        dto.setFoodItemId(item.getFoodItemId());
        dto.setPortionSize(item.getPortionSize());
        dto.setPortionUnit(item.getPortionUnit());
        dto.setMealType(item.getMealType());
        dto.setLogDate(item.getLogDate());
        dto.setSource(resolveAiEstimateSource(history.getRequestType()));
        dto.setAiRequestId(history.getId());
        dto.setAiConfidence(item.getConfidence());
        return dto;
    }

    private AiRequestHistoryDto toHistoryDto(AiRequestHistoryEntity entity) {
        AiRequestHistoryDto dto = new AiRequestHistoryDto();
        dto.setId(entity.getId());
        dto.setRequestType(entity.getRequestType());
        dto.setProvider(entity.getProvider());
        dto.setModel(entity.getModel());
        dto.setPromptVersion(entity.getPromptVersion());
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

    private void copyUsageMetadata(AiUsageMetadataCarrier response, AiRequestHistoryEntity history) {
        if (response == null || history == null) {
            return;
        }
        history.setPromptTokens(response.getPromptTokens());
        history.setCompletionTokens(response.getCompletionTokens());
        Integer totalTokens = response.getTotalTokens();
        if (totalTokens == null && (response.getPromptTokens() != null || response.getCompletionTokens() != null)) {
            totalTokens = (response.getPromptTokens() == null ? 0 : response.getPromptTokens())
                    + (response.getCompletionTokens() == null ? 0 : response.getCompletionTokens());
        }
        history.setTotalTokens(totalTokens);
        history.setEstimatedCost(response.getEstimatedCost());
        history.setCostCurrency(response.getCostCurrency());
    }
    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private boolean refundConsumedQuota(UserEntity user, int creditCost) {
        try {
            subscriptionService.refundConsumedAiQuota(user.getId(), creditCost);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
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

    private void notifyAdminsAboutRejectedDraft(AiRequestHistoryEntity history) {
        List<UserEntity> admins = userRepository.findByRoleIn(List.of(UserRole.OWNER, UserRole.ADMIN_TECHNICAL));
        if (admins.isEmpty()) {
            return;
        }
        String reason = history.getRejectionReason() == null ? "UNSPECIFIED" : history.getRejectionReason().name();
        String feedback = history.getRejectionFeedback() == null || history.getRejectionFeedback().isBlank()
                ? "no feedback"
                : history.getRejectionFeedback();
        String userEmail = history.getUser() == null ? "unknown-user" : history.getUser().getEmail();
        String message = "AI draft rejected by user. requestId=" + history.getId()
                + ", user=" + userEmail
                + ", type=" + history.getRequestType()
                + ", reason=" + reason
                + ", feedback=" + feedback;
        LocalDateTime now = LocalDateTime.now();
        List<NotificationEntity> notifications = admins.stream().map(admin -> {
            NotificationEntity notification = new NotificationEntity();
            notification.setUser(admin);
            notification.setType(AI_REJECTION_ALERT_TYPE);
            notification.setSeverity("WARNING");
            notification.setSource("AI_OPS");
            notification.setTargetType("AI_REQUEST");
            notification.setTargetId(String.valueOf(history.getId()));
            notification.setTargetRoute("ai");
            notification.setMessage(message);
            notification.setIsRead(false);
            notification.setCreatedAt(now);
            return notification;
        }).toList();
        notificationRepository.saveAll(notifications);
    }

    @FunctionalInterface
    private interface DraftSupplier {
        AiMealDraftResponseDto get();
    }
}
