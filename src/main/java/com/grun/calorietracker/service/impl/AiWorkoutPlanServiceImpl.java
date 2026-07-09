package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiWorkoutPlanConfirmRequestDto;
import com.grun.calorietracker.dto.AiWorkoutPlanDayDto;
import com.grun.calorietracker.dto.AiWorkoutPlanDraftRequestDto;
import com.grun.calorietracker.dto.AiWorkoutPlanDraftResponseDto;
import com.grun.calorietracker.dto.AiWorkoutPlanExerciseDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.dto.AiUsageMetadataCarrier;
import com.grun.calorietracker.dto.WorkoutPlanDto;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.WorkoutPlanEntity;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.ExerciseLogMeasurementType;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.WorkoutPlanStatus;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.repository.WorkoutPlanRepository;
import com.grun.calorietracker.service.AiMealDraftProviderClient;
import com.grun.calorietracker.service.AiProviderConfigurationValidator;
import com.grun.calorietracker.service.AiWorkoutPlanService;
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
public class AiWorkoutPlanServiceImpl implements AiWorkoutPlanService {

    private static final int MAX_DAYS = 7;
    private static final int MAX_EXERCISES_PER_DAY = 20;

    private final AiProperties properties;
    private final List<AiMealDraftProviderClient> providerClients;
    private final AiRequestHistoryRepository aiRequestHistoryRepository;
    private final UserRepository userRepository;
    private final ExerciseItemRepository exerciseItemRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;
    private final AiProviderConfigurationValidator providerConfigurationValidator;

    @Override
    @Transactional
    public AiWorkoutPlanDraftResponseDto createDraft(String email, AiWorkoutPlanDraftRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("AI workout plan request is required.");
        }
        providerConfigurationValidator.validateConfiguredForDraft();
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.AI_WORKOUT_PLANNER);
        UserEntity user = getUser(email);
        request.setUserContext(toUserContext(user));
        request.setExerciseCatalogContext(toExerciseCatalogContext(request));

        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setUser(user);
        history.setRequestType(AiRequestType.AI_WORKOUT_PLAN);
        history.setProvider(properties.getProvider());
        history.setModel(properties.getModel());
        history.setInputPayload(writeJson(toPrivacySafeInputPayload(request)));
        history.setCreatedAt(LocalDateTime.now());
        history.setQuotaConsumed(false);

        long startedAt = System.nanoTime();
        SubscriptionDto quota = subscriptionService.consumeAiQuota(email);
        try {
            AiWorkoutPlanDraftResponseDto response = normalize(activeProvider().createWorkoutPlanDraft(request));
            response.setAiRemainingThisPeriod(quota.getAiRemainingThisPeriod());
            copyUsageMetadata(response, history);

            history.setStatus(AiRequestStatus.DRAFT_CREATED);
            history.setOutputPayload(writeJson(response));
            history.setQuotaConsumed(true);
            history.setQuotaConsumedAmount(1);
            history.setLatencyMs(elapsedMs(startedAt));
            AiRequestHistoryEntity saved = aiRequestHistoryRepository.save(history);
            response.setRequestId(saved.getId());
            return response;
        } catch (RuntimeException ex) {
            boolean refunded = refundConsumedQuota(user);
            history.setStatus(AiRequestStatus.FAILED);
            history.setErrorMessage(ex.getMessage());
            history.setQuotaConsumed(!refunded);
            history.setQuotaConsumedAmount(refunded ? 0 : 1);
            history.setLatencyMs(elapsedMs(startedAt));
            aiRequestHistoryRepository.save(history);
            throw ex;
        }
    }

    @Override
    @Transactional
    public WorkoutPlanDto confirmDraft(String email, Long requestId, AiWorkoutPlanConfirmRequestDto request) {
        if (request == null || request.getPlan() == null) {
            throw new IllegalArgumentException("Final workout plan payload is required.");
        }
        UserEntity user = getUser(email);
        AiRequestHistoryEntity history = aiRequestHistoryRepository.findByIdAndUser(requestId, user)
                .orElseThrow(() -> new IllegalArgumentException("AI workout plan draft was not found."));
        if (history.getRequestType() != AiRequestType.AI_WORKOUT_PLAN) {
            throw new IllegalArgumentException("AI request is not a workout plan draft.");
        }
        if (history.getStatus() != AiRequestStatus.DRAFT_CREATED) {
            throw new IllegalArgumentException("AI workout plan draft is not open for confirmation.");
        }

        AiWorkoutPlanDraftResponseDto plan = normalize(request.getPlan());
        WorkoutPlanEntity entity = new WorkoutPlanEntity();
        entity.setUser(user);
        entity.setName(plan.getName() == null || plan.getName().isBlank() ? "AI workout plan" : plan.getName().trim());
        entity.setStatus(WorkoutPlanStatus.ACTIVE);
        entity.setSourceAiRequest(history);
        entity.setPlanPayload(writeJson(plan));
        entity.setActive(true);
        entity.setCreatedAt(LocalDateTime.now());

        WorkoutPlanEntity saved = workoutPlanRepository.save(entity);
        history.setStatus(AiRequestStatus.CONFIRMED);
        history.setConfirmationPayload(writeJson(Map.of("workoutPlanId", saved.getId())));
        history.setConfirmedAt(LocalDateTime.now());
        aiRequestHistoryRepository.save(history);
        return toDto(saved);
    }

    @Override
    @Transactional
    public void rejectDraft(String email, Long requestId) {
        UserEntity user = getUser(email);
        AiRequestHistoryEntity history = aiRequestHistoryRepository.findByIdAndUser(requestId, user)
                .orElseThrow(() -> new IllegalArgumentException("AI workout plan draft was not found."));
        if (history.getRequestType() != AiRequestType.AI_WORKOUT_PLAN) {
            throw new IllegalArgumentException("AI request is not a workout plan draft.");
        }
        if (history.getStatus() != AiRequestStatus.DRAFT_CREATED) {
            throw new IllegalArgumentException("AI workout plan draft is not open for rejection.");
        }
        history.setStatus(AiRequestStatus.REJECTED);
        history.setRejectedAt(LocalDateTime.now());
        aiRequestHistoryRepository.save(history);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkoutPlanDto> listActivePlans(String email) {
        UserEntity user = getUser(email);
        return workoutPlanRepository.findByUserAndActiveTrueOrderByCreatedAtDesc(user).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkoutPlanDto getPlan(String email, Long planId) {
        UserEntity user = getUser(email);
        return workoutPlanRepository.findByIdAndUser(planId, user)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Workout plan not found"));
    }

    @Override
    @Transactional
    public void archivePlan(String email, Long planId) {
        UserEntity user = getUser(email);
        WorkoutPlanEntity entity = workoutPlanRepository.findByIdAndUser(planId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Workout plan not found"));
        entity.setActive(false);
        entity.setStatus(WorkoutPlanStatus.ARCHIVED);
        entity.setUpdatedAt(LocalDateTime.now());
        workoutPlanRepository.save(entity);
    }
    private AiWorkoutPlanDraftResponseDto normalize(AiWorkoutPlanDraftResponseDto response) {
        if (response == null) {
            throw new IllegalArgumentException("AI workout provider returned an empty response.");
        }
        response.setRequestType(AiRequestType.AI_WORKOUT_PLAN);
        response.setProvider(properties.getProvider());
        response.setModel(properties.getModel());
        response.setStatus(AiRequestStatus.DRAFT_CREATED);
        response.setReviewRequired(true);
        if (response.getName() == null || response.getName().isBlank()) {
            response.setName("AI workout plan");
        }
        if (response.getName().trim().length() > 160) {
            throw new IllegalArgumentException("AI workout provider returned a plan name that is too long.");
        }
        if (response.getDays() == null || response.getDays().isEmpty() || response.getDays().size() > MAX_DAYS) {
            throw new IllegalArgumentException("AI workout provider returned an invalid number of plan days.");
        }
        for (AiWorkoutPlanDayDto day : response.getDays()) {
            validateDay(day);
        }
        normalizeQuality(response);
        if (response.getWarnings() == null) {
            response.setWarnings(List.of());
        }
        return response;
    }

    private void normalizeQuality(AiWorkoutPlanDraftResponseDto response) {
        response.setSchemaVersion("ai_response_v2");
        if (response.getReviewReasons() == null) {
            response.setReviewReasons(List.of());
        }
        if (response.getConfidence() == null) {
            response.setConfidence(hasReviewRequiredExercise(response) ? 0.7 : 0.85);
        }
        if (response.getQualityScore() == null) {
            response.setQualityScore((int) Math.round(response.getConfidence() * 100));
        }
        if (response.getEstimatedUncertainty() == null || response.getEstimatedUncertainty().isBlank()) {
            response.setEstimatedUncertainty(response.getConfidence() < 0.75 ? "MEDIUM" : "LOW");
        }
    }

    private boolean hasReviewRequiredExercise(AiWorkoutPlanDraftResponseDto response) {
        return response.getDays() != null && response.getDays().stream()
                .filter(day -> day.getExercises() != null)
                .flatMap(day -> day.getExercises().stream())
                .anyMatch(exercise -> Boolean.TRUE.equals(exercise.getReviewRequired()) || exercise.getExerciseItemId() == null);
    }
    private void validateDay(AiWorkoutPlanDayDto day) {
        if (day == null || day.getDayLabel() == null || day.getDayLabel().isBlank()) {
            throw new IllegalArgumentException("AI workout provider returned a day without a label.");
        }
        if (day.getEstimatedDurationMinutes() == null || day.getEstimatedDurationMinutes() <= 0) {
            throw new IllegalArgumentException("AI workout provider returned a day without an estimated duration.");
        }
        if (isBlank(day.getWarmup()) || isBlank(day.getCooldown())) {
            throw new IllegalArgumentException("AI workout provider returned a day without warm-up or cool-down guidance.");
        }
        if (day.getExercises() == null || day.getExercises().isEmpty() || day.getExercises().size() > MAX_EXERCISES_PER_DAY) {
            throw new IllegalArgumentException("AI workout provider returned an invalid number of exercises.");
        }
        for (AiWorkoutPlanExerciseDto exercise : day.getExercises()) {
            normalizeExercise(exercise, day);
            validateExercise(exercise);
        }
    }

    private void normalizeExercise(AiWorkoutPlanExerciseDto exercise, AiWorkoutPlanDayDto day) {
        if (exercise == null) {
            return;
        }
        if (exercise.getExerciseItemId() != null && exercise.getExerciseItemId() <= 0) {
            exercise.setExerciseItemId(null);
        }
        if (exercise.getMeasurementType() == ExerciseLogMeasurementType.DURATION
                && (exercise.getDurationMinutes() == null || exercise.getDurationMinutes() <= 0)) {
            int fallbackDuration = Math.max(1, day.getEstimatedDurationMinutes() / Math.max(1, day.getExercises().size()));
            exercise.setDurationMinutes(fallbackDuration);
            exercise.setReviewRequired(true);
            if (isBlank(exercise.getSafetyNote())) {
                exercise.setSafetyNote("Duration was estimated by GRun because the AI provider omitted it; review before following this plan.");
            }
        }
    }

    private void validateExercise(AiWorkoutPlanExerciseDto exercise) {
        if (exercise == null || exercise.getName() == null || exercise.getName().isBlank()) {
            throw new IllegalArgumentException("AI workout provider returned an exercise without a name.");
        }
        if (exercise.getMeasurementType() == null) {
            throw new IllegalArgumentException("AI workout provider returned an exercise without a measurement type.");
        }
        validateExercisePrescription(exercise);
        if (isBlank(exercise.getExecutionInstructions()) || isBlank(exercise.getSafetyNote()) || isBlank(exercise.getRationale())) {
            throw new IllegalArgumentException("AI workout provider returned an exercise without detailed instructions, rationale, or safety notes.");
        }
        if (exercise.getFormCues() == null || exercise.getFormCues().stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isBlank()).count() < 2) {
            throw new IllegalArgumentException("AI workout provider returned an exercise without enough form cues.");
        }
        if (exercise.getCommonMistakes() == null || exercise.getCommonMistakes().stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isBlank()).findAny().isEmpty()) {
            throw new IllegalArgumentException("AI workout provider returned an exercise without common mistakes.");
        }
        if (exercise.getExerciseItemId() != null) {
            ExerciseItemEntity item = exerciseItemRepository.findById(exercise.getExerciseItemId())
                    .orElseThrow(() -> new IllegalArgumentException("AI workout provider referenced an unknown exercise item."));
            if (!Boolean.TRUE.equals(item.getActive()) || !Boolean.TRUE.equals(item.getAiEligible())) {
                throw new IllegalArgumentException("AI workout provider referenced an inactive or non-AI exercise item.");
            }
            if (!allowedMeasurement(item, exercise.getMeasurementType())) {
                throw new IllegalArgumentException("AI workout provider used a measurement type not allowed for the exercise item.");
            }
        }
    }

    private void validateExercisePrescription(AiWorkoutPlanExerciseDto exercise) {
        switch (exercise.getMeasurementType()) {
            case SETS_REPS, WEIGHT_REPS -> {
                if (exercise.getSetCount() == null || exercise.getSetCount() <= 0 || exercise.getReps() == null || exercise.getReps() <= 0) {
                    throw new IllegalArgumentException("AI workout provider returned a strength exercise without sets and reps.");
                }
            }
            case REPS -> {
                if (exercise.getReps() == null || exercise.getReps() <= 0) {
                    throw new IllegalArgumentException("AI workout provider returned a reps-based exercise without reps.");
                }
            }
            case DURATION -> {
                if (exercise.getDurationMinutes() == null || exercise.getDurationMinutes() <= 0) {
                    throw new IllegalArgumentException("AI workout provider returned a duration exercise without duration.");
                }
            }
            case DISTANCE -> {
                if (exercise.getDistanceKm() == null || exercise.getDistanceKm() <= 0) {
                    throw new IllegalArgumentException("AI workout provider returned a distance exercise without distance.");
                }
            }
            case MIXED -> {
                if ((exercise.getSetCount() == null || exercise.getSetCount() <= 0)
                        && (exercise.getReps() == null || exercise.getReps() <= 0)
                        && (exercise.getDurationMinutes() == null || exercise.getDurationMinutes() <= 0)
                        && (exercise.getDistanceKm() == null || exercise.getDistanceKm() <= 0)) {
                    throw new IllegalArgumentException("AI workout provider returned a mixed exercise without a measurable prescription.");
                }
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
    private boolean allowedMeasurement(ExerciseItemEntity item, ExerciseLogMeasurementType measurementType) {
        if (item.getAllowedMeasurementTypes() == null || item.getAllowedMeasurementTypes().isBlank()) {
            return true;
        }
        return List.of(item.getAllowedMeasurementTypes().split(",")).stream()
                .map(String::trim)
                .anyMatch(value -> value.equalsIgnoreCase(measurementType.name()));
    }

    private WorkoutPlanDto toDto(WorkoutPlanEntity entity) {
        WorkoutPlanDto dto = new WorkoutPlanDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setStatus(entity.getStatus());
        dto.setSourceAiRequestId(entity.getSourceAiRequest() == null ? null : entity.getSourceAiRequest().getId());
        dto.setPlan(readPlan(entity.getPlanPayload()));
        dto.setActive(entity.getActive());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private AiWorkoutPlanDraftResponseDto readPlan(String payload) {
        try {
            return objectMapper.readValue(payload, AiWorkoutPlanDraftResponseDto.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored workout plan payload could not be read.");
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

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private Map<String, Object> toUserContext(UserEntity user) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("age", user.getAge());
        context.put("gender", user.getGender());
        context.put("heightCm", user.getHeight());
        context.put("weightKg", user.getWeight());
        context.put("bodyFatPercentage", user.getBodyFatPercentage());
        context.put("preferredLanguage", user.getPreferredLanguage());
        return context;
    }

    private List<Map<String, Object>> toExerciseCatalogContext(AiWorkoutPlanDraftRequestDto request) {
        List<Long> excluded = request.getExcludedExerciseItemIds() == null ? List.of() : request.getExcludedExerciseItemIds();
        return exerciseItemRepository.findAll(PageRequest.of(0, 80)).getContent().stream()
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .filter(item -> Boolean.TRUE.equals(item.getAiEligible()))
                .filter(item -> item.getId() == null || !excluded.contains(item.getId()))
                .limit(30)
                .map(this::toExerciseCatalogItem)
                .toList();
    }

    private Map<String, Object> toExerciseCatalogItem(ExerciseItemEntity item) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("id", item.getId());
        context.put("name", item.getName());
        context.put("primaryMuscleGroup", item.getPrimaryMuscleGroup());
        context.put("equipment", item.getEquipment());
        context.put("difficulty", item.getDifficulty());
        context.put("defaultMeasurementType", item.getDefaultMeasurementType());
        context.put("allowedMeasurementTypes", item.getAllowedMeasurementTypes());
        context.put("safetyNotes", item.getSafetyNotes());
        return context;
    }
    private Map<String, Object> toPrivacySafeInputPayload(AiWorkoutPlanDraftRequestDto request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestType", AiRequestType.AI_WORKOUT_PLAN);
        payload.put("goalLength", request.getGoal() == null ? 0 : request.getGoal().length());
        payload.put("level", request.getLevel());
        payload.put("daysPerWeek", request.getDaysPerWeek());
        payload.put("minutesPerSession", request.getMinutesPerSession());
        payload.put("equipmentCount", request.getEquipment() == null ? 0 : request.getEquipment().size());
        payload.put("focusAreaCount", request.getFocusAreas() == null ? 0 : request.getFocusAreas().size());
        payload.put("excludedExerciseCount", request.getExcludedExerciseItemIds() == null ? 0 : request.getExcludedExerciseItemIds().size());
        payload.put("hasLimitationNotes", request.getLimitationNotes() != null && !request.getLimitationNotes().isBlank());
        payload.put("language", request.getLanguage());
        return payload;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("AI workout payload could not be serialized.");
        }
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

    private boolean refundConsumedQuota(UserEntity user) {
        try {
            subscriptionService.refundConsumedAiQuota(user.getId(), 1);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}

