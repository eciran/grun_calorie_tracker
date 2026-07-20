package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.AiProviderException;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.*;
import com.grun.calorietracker.service.support.AiSafeResponseBuilder;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiPreparationGuideServiceImpl implements AiPreparationGuideService {
    private static final String SCHEMA_VERSION = "ai_preparation_guide_v1";
    private static final AiRequestType REQUEST_TYPE = AiRequestType.AI_MEAL_PREPARATION_GUIDE;
    private static final SubscriptionFeature FEATURE = SubscriptionFeature.AI_MEAL_PREPARATION_GUIDE;

    private final AiProperties properties;
    private final List<AiMealDraftProviderClient> providerClients;
    private final AiRequestHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final MealPlanItemRepository itemRepository;
    private final MealPlanPreparationGuideRepository guideRepository;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;
    private final AiProviderConfigurationValidator providerConfigurationValidator;

    @Override
    @Transactional(noRollbackFor = {AiProviderException.class, RequestConflictException.class, IllegalArgumentException.class})
    public AiPreparationGuideResponseDto generate(String email, Long planId, Long itemId,
                                                   String idempotencyKey,
                                                   AiPreparationGuideGenerateRequestDto request) {
        return create(email, planId, itemId, idempotencyKey, request, false);
    }

    @Override
    @Transactional(noRollbackFor = {AiProviderException.class, RequestConflictException.class, IllegalArgumentException.class})
    public AiPreparationGuideResponseDto regenerate(String email, Long planId, Long itemId,
                                                     String idempotencyKey,
                                                     AiPreparationGuideGenerateRequestDto request) {
        return create(email, planId, itemId, idempotencyKey, request, true);
    }

    @Override
    @Transactional(readOnly = true)
    public AiPreparationGuideResponseDto reopen(String email, Long planId, Long itemId) {
        UserEntity user = user(email);
        MealPlanItemEntity item = itemRepository.findOwned(itemId, planId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Meal-plan item was not found."));
        MealPlanPreparationGuideEntity guide = guideRepository
                .findTopByMealPlanItemOrderByVersionDesc(item)
                .orElseThrow(() -> new ResourceNotFoundException("Preparation guide was not found."));
        return readGuide(guide);
    }

    @Override
    @Transactional
    public void reject(String email, Long planId, Long itemId, Long requestId,
                       AiMealDraftRejectRequestDto request) {
        UserEntity user = user(email);
        MealPlanItemEntity item = ownedItem(itemId, planId, user);
        AiRequestHistoryEntity history = historyRepository.findByIdAndUser(requestId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Preparation guide request was not found."));
        if (history.getRequestType() != REQUEST_TYPE) {
            throw new IllegalArgumentException("AI request is not a preparation guide.");
        }
        MealPlanPreparationGuideEntity guide = guideRepository.findBySourceAiRequest(history)
                .filter(value -> Objects.equals(value.getMealPlanItem().getId(), item.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Preparation guide was not found."));
        if (history.getStatus() != AiRequestStatus.DRAFT_CREATED) {
            throw new RequestConflictException("Preparation guide is not open for rejection.");
        }
        history.setStatus(AiRequestStatus.REJECTED);
        history.setRejectionReason(request == null ? null : request.getReason());
        history.setRejectionFeedback(cleanFeedback(request == null ? null : request.getFeedback()));
        history.setRejectedAt(LocalDateTime.now());
        guide.setStatus(AiPreparationGuideStatus.REJECTED);
        historyRepository.save(history);
        guideRepository.save(guide);
    }

    private AiPreparationGuideResponseDto create(String email, Long planId, Long itemId,
                                                  String idempotencyKey,
                                                  AiPreparationGuideGenerateRequestDto request,
                                                  boolean regenerate) {
        String key = normalizeKey(idempotencyKey);
        providerConfigurationValidator.validateConfiguredForDraft();
        subscriptionService.assertFeatureAccess(email, FEATURE);
        UserEntity user = user(email);
        MealPlanItemEntity item = ownedItem(itemId, planId, user);

        AiPreparationGuideResponseDto previous = existing(user, key, planId, itemId);
        if (previous != null) {
            return previous;
        }
        Optional<MealPlanPreparationGuideEntity> latest = guideRepository
                .findTopByMealPlanItemOrderByVersionDesc(item);
        if (!regenerate && latest.isPresent()) {
            return readGuide(latest.get());
        }
        int version = latest.map(value -> value.getVersion() + 1).orElse(1);
        int creditCost = subscriptionService.resolveAiCreditCost(email, FEATURE);
        AiPreparationGuideProviderRequestDto providerRequest = providerRequest(item, request);

        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setUser(user);
        history.setRequestType(REQUEST_TYPE);
        history.setProvider(properties.getProvider());
        history.setModel(properties.getModel());
        history.setPromptVersion(properties.getPromptVersion());
        history.setStatus(AiRequestStatus.PROCESSING);
        history.setIdempotencyKey(key);
        history.setInputPayload(json(Map.of(
                "mealPlanId", planId,
                "mealPlanItemId", itemId,
                "guideVersion", version,
                "language", providerRequest.getLanguage() == null ? "default" : providerRequest.getLanguage(),
                "creditCost", creditCost)));
        history.setCreatedAt(LocalDateTime.now());
        history.setQuotaConsumed(false);
        history.setQuotaConsumedAmount(0);
        try {
            history = historyRepository.save(history);
        } catch (DataIntegrityViolationException ex) {
            AiPreparationGuideResponseDto concurrent = existing(user, key, planId, itemId);
            if (concurrent != null) {
                return concurrent;
            }
            throw new RequestConflictException("A preparation-guide request with this key is already processing.");
        }

        long startedAt = System.nanoTime();
        boolean charged = false;
        try {
            AiPreparationGuideResponseDto response = provider().createPreparationGuide(providerRequest);
            normalizeAndValidate(response, item, planId, version, creditCost);
            SubscriptionDto quota = subscriptionService.consumeAiQuota(email, creditCost);
            charged = true;
            response.setAiRemainingThisPeriod(quota.getAiRemainingThisPeriod());
            copyUsage(response, history);
            history.setStatus(AiRequestStatus.DRAFT_CREATED);
            history.setQuotaConsumed(true);
            history.setQuotaConsumedAmount(creditCost);
            history.setLatencyMs(elapsed(startedAt));
            history = historyRepository.save(history);

            MealPlanPreparationGuideEntity guide = new MealPlanPreparationGuideEntity();
            guide.setMealPlanItem(item);
            guide.setSourceAiRequest(history);
            guide.setVersion(version);
            guide.setSchemaVersion(SCHEMA_VERSION);
            guide.setStatus(AiPreparationGuideStatus.ACTIVE);
            guide.setCreatedAt(LocalDateTime.now());
            response.setRequestId(history.getId());
            response.setCreatedAt(guide.getCreatedAt());
            guide.setGuidePayload(json(response));
            guide = guideRepository.save(guide);
            response.setGuideId(guide.getId());
            history.setOutputPayload(json(response));
            historyRepository.save(history);
            return response;
        } catch (RuntimeException ex) {
            if (charged) {
                refund(user, creditCost);
            }
            history.setStatus(AiRequestStatus.FAILED);
            history.setErrorMessage(safeTechnicalMessage(ex));
            history.setOutputPayload(json(AiSafeResponseBuilder.failurePayload(REQUEST_TYPE, true)));
            history.setQuotaConsumed(false);
            history.setQuotaConsumedAmount(0);
            history.setLatencyMs(elapsed(startedAt));
            historyRepository.save(history);
            if (ex instanceof RequestConflictException) {
                throw ex;
            }
            throw new AiProviderException("AI preparation guide could not produce a usable result.");
        }
    }

    private void normalizeAndValidate(AiPreparationGuideResponseDto response,
                                      MealPlanItemEntity item, Long planId,
                                      int version, int creditCost) {
        if (response == null) {
            throw new AiProviderException("AI preparation guide response was empty.");
        }
        response.setSchemaVersion(SCHEMA_VERSION);
        response.setRequestType(REQUEST_TYPE);
        response.setStatus(AiRequestStatus.DRAFT_CREATED);
        response.setProvider(properties.getProvider());
        response.setModel(properties.getModel());
        response.setMealPlanId(planId);
        response.setMealPlanItemId(item.getId());
        response.setVersion(version);
        response.setItemName(displayName(item));
        response.setPlannedQuantity(item.getPortionSize());
        response.setPlannedUnit(item.getPortionUnit());
        response.setPlannedNutrition(nutrition(item));
        response.setCreditCost(creditCost);
        response.setPreparationMinutes(minutes(response.getPreparationMinutes()));
        response.setCookingMinutes(minutes(response.getCookingMinutes()));
        response.setEquipment(cleanList(response.getEquipment(), 20, 100));
        response.setFoodSafetyNotes(cleanList(response.getFoodSafetyNotes(), 15, 300));
        response.setStorageInstructions(cleanList(response.getStorageInstructions(), 15, 300));
        response.setNutritionImpactWarnings(cleanList(response.getNutritionImpactWarnings(), 15, 300));
        response.setAssumptions(cleanList(response.getAssumptions(), 15, 300));
        if (response.getIngredients() == null || response.getIngredients().isEmpty()
                || response.getIngredients().size() > 40) {
            throw new AiProviderException("Preparation guide ingredients are invalid.");
        }
        for (AiPreparationGuideIngredientDto ingredient : response.getIngredients()) {
            ingredient.setName(text(ingredient.getName(), 120));
            if (ingredient.getQuantity() == null || ingredient.getQuantity() <= 0
                    || ingredient.getQuantity() > 10000 || ingredient.getUnit() == null) {
                throw new AiProviderException("Preparation guide ingredient quantity is invalid.");
            }
            ingredient.setOptional(Boolean.TRUE.equals(ingredient.getOptional()));
            ingredient.setChangesPlannedNutrition(Boolean.TRUE.equals(ingredient.getChangesPlannedNutrition()));
        }
        boolean nutritionChangingIngredient = response.getIngredients().stream()
                .anyMatch(value -> Boolean.TRUE.equals(value.getChangesPlannedNutrition()));
        if (nutritionChangingIngredient && response.getNutritionImpactWarnings().isEmpty()) {
            throw new AiProviderException("Nutrition-changing ingredient requires an impact warning.");
        }
        if (response.getSteps() == null || response.getSteps().isEmpty() || response.getSteps().size() > 30) {
            throw new AiProviderException("Preparation guide steps are invalid.");
        }
        for (int i = 0; i < response.getSteps().size(); i++) {
            AiPreparationGuideStepDto step = response.getSteps().get(i);
            step.setStepNumber(i + 1);
            step.setInstruction(text(step.getInstruction(), 600));
            step.setDurationMinutes(minutes(step.getDurationMinutes()));
            if (step.getTemperatureCelsius() != null
                    && (step.getTemperatureCelsius() < -30 || step.getTemperatureCelsius() > 350)) {
                throw new AiProviderException("Preparation guide temperature is invalid.");
            }
        }
        if (response.getSubstitutions() == null) response.setSubstitutions(new ArrayList<>());
        if (response.getSubstitutions().size() > 20) throw new AiProviderException("Too many substitutions.");
        for (AiPreparationGuideSubstitutionDto substitution : response.getSubstitutions()) {
            substitution.setOriginalIngredient(text(substitution.getOriginalIngredient(), 120));
            substitution.setSubstitute(text(substitution.getSubstitute(), 120));
            boolean changes = Boolean.TRUE.equals(substitution.getChangesPlannedNutrition());
            substitution.setChangesPlannedNutrition(changes);
            if (changes && (substitution.getNutritionImpactWarning() == null
                    || substitution.getNutritionImpactWarning().isBlank())) {
                throw new AiProviderException("Nutrition-changing substitution requires a warning.");
            }
            if (substitution.getNutritionImpactWarning() != null) {
                substitution.setNutritionImpactWarning(text(substitution.getNutritionImpactWarning(), 300));
            }
        }
        if (response.getQualityScore() == null || response.getQualityScore() < 0 || response.getQualityScore() > 100
                || response.getConfidence() == null || response.getConfidence() < 0 || response.getConfidence() > 1
                || !Set.of("LOW", "MEDIUM", "HIGH").contains(response.getEstimatedUncertainty())) {
            throw new AiProviderException("Preparation guide quality metadata is invalid.");
        }
        response.setReviewRequired(true);
    }

    private AiPreparationGuideProviderRequestDto providerRequest(MealPlanItemEntity item,
                                                                  AiPreparationGuideGenerateRequestDto request) {
        AiPreparationGuideProviderRequestDto dto = new AiPreparationGuideProviderRequestDto();
        dto.setMealPlanId(item.getMealPlan().getId());
        dto.setMealPlanItemId(item.getId());
        dto.setPlanDate(item.getPlanDate());
        dto.setMealType(item.getMealType());
        dto.setItemName(displayName(item));
        dto.setItemDescription(item.getSnapshotDescription());
        dto.setPlannedQuantity(item.getPortionSize());
        dto.setPlannedUnit(item.getPortionUnit());
        dto.setPlannedNutrition(nutrition(item));
        dto.setShortPreparationState(item.getShortPreparationState());
        dto.setWorkoutRelation(item.getWorkoutRelation());
        dto.setAllergens(readStringList(item.getAllergensPayload()));
        dto.setWarnings(readStringList(item.getWarningsPayload()));
        dto.setLanguage(request == null ? null : cleanLanguage(request.getLanguage()));
        return dto;
    }

    private AiPreparationGuideResponseDto existing(UserEntity user, String key, Long planId, Long itemId) {
        return historyRepository.findByUserAndRequestTypeAndIdempotencyKey(user, REQUEST_TYPE, key)
                .map(history -> {
                    if (history.getStatus() == AiRequestStatus.PROCESSING) {
                        throw new RequestConflictException("A preparation-guide request with this key is already processing.");
                    }
                    if (history.getStatus() == AiRequestStatus.FAILED) {
                        throw new IllegalArgumentException("This idempotency key was already used by a failed request.");
                    }
                    MealPlanPreparationGuideEntity guide = guideRepository.findBySourceAiRequest(history)
                            .orElseThrow(() -> new IllegalStateException("Stored preparation guide is unavailable."));
                    if (!Objects.equals(guide.getMealPlanItem().getId(), itemId)
                            || !Objects.equals(guide.getMealPlanItem().getMealPlan().getId(), planId)) {
                        throw new RequestConflictException("Idempotency key belongs to another meal-plan item.");
                    }
                    return readGuide(guide);
                }).orElse(null);
    }

    private AiPreparationGuideResponseDto readGuide(MealPlanPreparationGuideEntity guide) {
        try {
            AiPreparationGuideResponseDto response = objectMapper.readValue(
                    guide.getGuidePayload(), AiPreparationGuideResponseDto.class);
            response.setGuideId(guide.getId());
            response.setRequestId(guide.getSourceAiRequest().getId());
            response.setStatus(guide.getSourceAiRequest().getStatus());
            response.setCreatedAt(guide.getCreatedAt());
            return response;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored preparation guide is unavailable.");
        }
    }

    private MealPlanItemEntity ownedItem(Long itemId, Long planId, UserEntity user) {
        return itemRepository.findOwnedForUpdate(itemId, planId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Meal-plan item was not found."));
    }

    private String displayName(MealPlanItemEntity item) {
        String value = item.getSnapshotName();
        if ((value == null || value.isBlank()) && item.getFoodItem() != null) value = item.getFoodItem().getName();
        if ((value == null || value.isBlank()) && item.getRecipe() != null) value = item.getRecipe().getName();
        value = FoodProductNormalizationRules.normalizeProductDisplayName(value);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Meal-plan item name is missing.");
        return value;
    }

    private MealPlanNutritionSnapshotDto nutrition(MealPlanItemEntity item) {
        return new MealPlanNutritionSnapshotDto(item.getSnapshotCalories(), item.getSnapshotProtein(),
                item.getSnapshotCarbs(), item.getSnapshotFat(), item.getSnapshotFiber(), item.getSnapshotSugar(),
                item.getSnapshotSaturatedFat(), item.getSnapshotSodium(), item.getSnapshotPotassium(),
                item.getSnapshotCholesterol(), item.getSnapshotCalcium(), item.getSnapshotIron(),
                item.getSnapshotMagnesium(), item.getSnapshotZinc(), item.getSnapshotVitaminA(),
                item.getSnapshotVitaminC(), item.getSnapshotVitaminD(), item.getSnapshotVitaminE(),
                item.getSnapshotVitaminB12());
    }

    private List<String> readStringList(String payload) {
        if (payload == null || payload.isBlank()) return new ArrayList<>();
        try { return cleanList(objectMapper.readValue(payload, new TypeReference<List<String>>() {}), 30, 200); }
        catch (JsonProcessingException ex) { return new ArrayList<>(); }
    }

    private List<String> cleanList(List<String> values, int max, int length) {
        if (values == null) return new ArrayList<>();
        if (values.size() > max) throw new AiProviderException("Preparation guide list is too large.");
        return values.stream().filter(Objects::nonNull).map(value -> text(value, length)).distinct().toList();
    }

    private String text(String value, int max) {
        if (value == null) throw new AiProviderException("Preparation guide text is missing.");
        String cleaned = value.trim().replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ");
        if (cleaned.isBlank() || cleaned.length() > max) throw new AiProviderException("Preparation guide text is invalid.");
        return cleaned;
    }

    private Integer minutes(Integer value) {
        if (value == null) return null;
        if (value < 0 || value > 1440) throw new AiProviderException("Preparation guide duration is invalid.");
        return value;
    }

    private String cleanLanguage(String value) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.trim();
        if (!cleaned.matches("[A-Za-z]{2,3}([_-][A-Za-z]{2})?")) throw new IllegalArgumentException("Language is invalid.");
        return cleaned;
    }

    private String cleanFeedback(String value) {
        if (value == null || value.isBlank()) return null;
        return text(value, 500);
    }

    private String normalizeKey(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Idempotency-Key header is required.");
        String key = value.trim();
        if (key.length() < 8 || key.length() > 100 || !key.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("Idempotency-Key must contain 8-100 safe characters.");
        }
        return key;
    }

    private AiMealDraftProviderClient provider() {
        return providerClients.stream().filter(client -> client.provider() == properties.getProvider()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Configured AI provider is not available."));
    }

    private UserEntity user(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private void copyUsage(AiUsageMetadataCarrier response, AiRequestHistoryEntity history) {
        history.setPromptTokens(response.getPromptTokens());
        history.setCompletionTokens(response.getCompletionTokens());
        Integer total = response.getTotalTokens();
        if (total == null && (response.getPromptTokens() != null || response.getCompletionTokens() != null)) {
            total = (response.getPromptTokens() == null ? 0 : response.getPromptTokens())
                    + (response.getCompletionTokens() == null ? 0 : response.getCompletionTokens());
        }
        history.setTotalTokens(total);
        history.setEstimatedCost(response.getEstimatedCost());
        history.setCostCurrency(response.getCostCurrency());
    }

    private void refund(UserEntity user, int amount) {
        try { subscriptionService.refundConsumedAiQuota(user.getId(), amount); }
        catch (RuntimeException ignored) { }
    }

    private String safeTechnicalMessage(RuntimeException ex) {
        String value = ex.getMessage();
        return value == null ? ex.getClass().getSimpleName() : value.substring(0, Math.min(value.length(), 1000));
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Preparation guide payload could not be serialized."); }
    }

    private long elapsed(long startedAt) { return (System.nanoTime() - startedAt) / 1_000_000; }
}