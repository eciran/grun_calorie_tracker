package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AiWorkoutPlanConfirmRequestDto;
import com.grun.calorietracker.dto.AiMealDraftRejectRequestDto;
import com.grun.calorietracker.dto.AiWorkoutPlanCreditEstimateDto;
import com.grun.calorietracker.dto.AiWorkoutPlanDayDto;
import com.grun.calorietracker.dto.AiWorkoutPlanDraftRequestDto;
import com.grun.calorietracker.dto.AiWorkoutPlanDraftResponseDto;
import com.grun.calorietracker.dto.AiWorkoutPlanExerciseDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.dto.AiUsageMetadataCarrier;
import com.grun.calorietracker.dto.AiCreditCostEstimateDto;
import com.grun.calorietracker.dto.WorkoutPlanDto;
import com.grun.calorietracker.dto.WorkoutPlanScheduleSessionRequestDto;
import com.grun.calorietracker.dto.WorkoutPlanScheduleUpdateRequestDto;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.WorkoutPlanEntity;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.enums.ExerciseLogMeasurementType;
import com.grun.calorietracker.enums.ExerciseTechniqueReviewStatus;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.WorkoutPlanStatus;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.repository.WorkoutPlanRepository;
import com.grun.calorietracker.service.AiMealDraftProviderClient;
import com.grun.calorietracker.service.AiProviderConfigurationValidator;
import com.grun.calorietracker.service.AiWorkoutPlanService;
import com.grun.calorietracker.service.SubscriptionService;
import com.grun.calorietracker.service.AiCreditPricingService;
import com.grun.calorietracker.service.support.AiSafeResponseBuilder;
import com.grun.calorietracker.service.support.AiIdempotencySupport;
import com.grun.calorietracker.service.support.AiUxContractFactory;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    private final AiCreditPricingService aiCreditPricingService;
    private final ObjectMapper objectMapper;
    private final AiProviderConfigurationValidator providerConfigurationValidator;
    private final UserTimeZoneSupport userTimeZoneSupport;

    @Override
    public AiWorkoutPlanCreditEstimateDto estimateCreditCost(
            String email, int daysPerWeek, int minutesPerSession) {
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.AI_WORKOUT_PLANNER);
        AiCreditCostEstimateDto estimate = aiCreditPricingService.estimateWorkout(
                daysPerWeek, minutesPerSession);
        return new AiWorkoutPlanCreditEstimateDto(
                daysPerWeek,
                minutesPerSession,
                estimate.getComplexityUnits(),
                estimate.getBaseCreditCost(),
                estimate.getIncludedUnits(),
                estimate.getUnitsPerAdditionalCredit(),
                estimate.getAdditionalCredits(),
                estimate.getTotalCreditCost());
    }

    @Override
    public AiWorkoutPlanDraftResponseDto createDraft(
            String email, String idempotencyKey, AiWorkoutPlanDraftRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("AI workout plan request is required.");
        }
        providerConfigurationValidator.validateConfiguredForDraft();
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.AI_WORKOUT_PLANNER);
        UserEntity user = getUser(email);
        request.setUserContext(toUserContext(user));
        request.setExerciseCatalogContext(toExerciseCatalogContext(request));
        String key = AiIdempotencySupport.normalizeKey(idempotencyKey);
        AiWorkoutPlanDraftResponseDto previous = existingDraft(user, key);
        if (previous != null) {
            return previous;
        }

        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setUser(user);
        history.setRequestType(AiRequestType.AI_WORKOUT_PLAN);
        history.setProvider(properties.getProvider());
        history.setModel(properties.getModel());
        history.setPromptVersion(properties.getPromptVersion());
        history.setStatus(AiRequestStatus.PROCESSING);
        history.setIdempotencyKey(key);
        history.setInputPayload(writeJson(toPrivacySafeInputPayload(request)));
        history.setCreatedAt(LocalDateTime.now());
        history.setQuotaConsumed(false);
        history.setQuotaConsumedAmount(0);

        try {
            AiRequestHistoryEntity reserved = aiRequestHistoryRepository.save(history);
            if (reserved != null) {
                history = reserved;
            }
        } catch (DataIntegrityViolationException ex) {
            AiWorkoutPlanDraftResponseDto concurrent = existingDraft(user, key);
            if (concurrent != null) {
                return concurrent;
            }
            throw new RequestConflictException(
                    "An AI workout-plan request with this key is already processing.");
        }

        int creditCost = aiCreditPricingService.estimateWorkout(
                request.getDaysPerWeek(), request.getMinutesPerSession())
                .getTotalCreditCost();
        long startedAt = System.nanoTime();
        boolean charged = false;
        try {
            SubscriptionDto quota = subscriptionService.consumeAiQuota(email, creditCost);
            charged = true;
            AiWorkoutPlanDraftResponseDto response = normalize(activeProvider().createWorkoutPlanDraft(request));
            response.setQuotaConsumedAmount(creditCost);
            response.setAiBaseRemainingThisPeriod(quota.getAiBaseRemainingThisPeriod());
            response.setAiAddonRemainingThisPeriod(quota.getAiAddonRemainingThisPeriod());
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
            history.setOutputPayload(writeJson(AiSafeResponseBuilder.failurePayload(
                    AiRequestType.AI_WORKOUT_PLAN,
                    true,
                    creditCost,
                    !refunded,
                    user.getPreferredLanguage())));
            history.setQuotaConsumed(!refunded);
            history.setQuotaConsumedAmount(refunded ? 0 : creditCost);
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
        plan.getDays().forEach(day -> {
            day.setScheduledDate(null);
            day.setScheduledStartTime(null);
            day.setSessionIntensity(null);
        });
        LocalDateTime now = LocalDateTime.now();
        List<WorkoutPlanEntity> previousActivePlans =
                workoutPlanRepository.findByUserAndActiveTrueOrderByCreatedAtDesc(user);
        previousActivePlans.forEach(previous -> {
            previous.setActive(false);
            previous.setStatus(WorkoutPlanStatus.ARCHIVED);
            previous.setUpdatedAt(now);
        });
        workoutPlanRepository.saveAll(previousActivePlans);

        WorkoutPlanEntity entity = new WorkoutPlanEntity();
        entity.setUser(user);
        entity.setName(plan.getName() == null || plan.getName().isBlank() ? "AI workout plan" : plan.getName().trim());
        entity.setStatus(WorkoutPlanStatus.ACTIVE);
        entity.setSourceAiRequest(history);
        entity.setPlanPayload(writeJson(plan));
        entity.setActive(true);
        entity.setCreatedAt(now);

        WorkoutPlanEntity saved = workoutPlanRepository.save(entity);
        history.setStatus(AiRequestStatus.CONFIRMED);
        history.setConfirmationPayload(writeJson(Map.of("workoutPlanId", saved.getId())));
        history.setConfirmedAt(LocalDateTime.now());
        aiRequestHistoryRepository.save(history);
        return toDto(saved);
    }

    @Override
    @Transactional
    public void rejectDraft(String email, Long requestId, AiMealDraftRejectRequestDto request) {
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
    public List<WorkoutPlanDto> listAllPlans(String email) {
        UserEntity user = getUser(email);
        return workoutPlanRepository.findByUserOrderByCreatedAtDesc(user).stream()
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
    public WorkoutPlanDto updateSchedule(
            String email, Long planId, WorkoutPlanScheduleUpdateRequestDto request) {
        UserEntity user = getUser(email);
        WorkoutPlanEntity entity = workoutPlanRepository.findByIdAndUser(planId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Workout plan not found"));
        if (!Boolean.TRUE.equals(entity.getActive()) || entity.getStatus() != WorkoutPlanStatus.ACTIVE) {
            throw new IllegalArgumentException("Only an active workout plan can be scheduled.");
        }
        if (request == null || request.getSessions() == null || request.getSessions().isEmpty()) {
            throw new IllegalArgumentException("Workout schedule sessions are required.");
        }

        AiWorkoutPlanDraftResponseDto plan = readPlan(entity.getPlanPayload());
        if (request.getSessions().size() != plan.getDays().size()) {
            throw new IllegalArgumentException("Schedule must contain exactly one session for every workout day.");
        }
        LocalDate today = userTimeZoneSupport.today(user);
        Set<Integer> indexes = new HashSet<>();
        Set<LocalDate> dates = new HashSet<>();
        for (WorkoutPlanScheduleSessionRequestDto session : request.getSessions()) {
            if (session == null || session.getDayIndex() == null || session.getScheduledDate() == null
                    || session.getIntensity() == null) {
                throw new IllegalArgumentException("Each workout schedule session requires dayIndex, date, and intensity.");
            }
            if (session.getDayIndex() < 0 || session.getDayIndex() >= plan.getDays().size()) {
                throw new IllegalArgumentException("Workout schedule dayIndex is outside the plan range.");
            }
            if (session.getScheduledDate().isBefore(today)) {
                throw new IllegalArgumentException("Workout schedule dates cannot be in the past.");
            }
            if (!indexes.add(session.getDayIndex())) {
                throw new IllegalArgumentException("Workout schedule contains a duplicate dayIndex.");
            }
            if (!dates.add(session.getScheduledDate())) {
                throw new IllegalArgumentException("Only one workout session per date is supported.");
            }
            AiWorkoutPlanDayDto day = plan.getDays().get(session.getDayIndex());
            day.setScheduledDate(session.getScheduledDate());
            day.setScheduledStartTime(session.getScheduledStartTime());
            day.setSessionIntensity(session.getIntensity());
        }

        entity.setPlanPayload(writeJson(plan));
        entity.setScheduleVersion("workout_schedule_v1");
        entity.setScheduleUpdatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return toDto(workoutPlanRepository.save(entity));
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
        response.setSchemaVersion("ai_response_v3");
        if (response.getReviewReasons() == null) {
            response.setReviewReasons(List.of());
        }
        if (response.getAssumptions() == null) {
            response.setAssumptions(List.of());
        }
        if (response.getNextBestActions() == null) {
            response.setNextBestActions(List.of());
        }
        if (response.getTrainingPrinciples() == null) {
            response.setTrainingPrinciples(List.of());
        }
        if (response.getResultType() == null || response.getResultType().isBlank()) {
            response.setResultType("AI_WORKOUT_PLAN_DRAFT");
        }
        if (response.getUserMessage() == null || response.getUserMessage().isBlank()) {
            response.setUserMessage("AI prepared a structured workout draft. Review the plan and adjust any movement that does not feel suitable.");
        }
        if (response.getProfessionalSummary() == null || response.getProfessionalSummary().isBlank()) {
            response.setProfessionalSummary(response.getSummary());
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
        if (isBlank(exercise.getCoachingNote())) {
            exercise.setCoachingNote("Move with control, keep the target effort sustainable, and adjust the exercise if form breaks down.");
        }
        if (exercise.getExerciseItemId() != null) {
            ExerciseItemEntity item = exerciseItemRepository.findById(exercise.getExerciseItemId())
                    .orElseThrow(() -> new IllegalArgumentException("AI workout provider referenced an unknown exercise item."));
            if (!Boolean.TRUE.equals(item.getActive())
                    || !Boolean.TRUE.equals(item.getAiEligible())
                    || item.getTechniqueReviewStatus() != ExerciseTechniqueReviewStatus.APPROVED) {
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
        AiWorkoutPlanDraftResponseDto plan = readPlan(entity.getPlanPayload());
        dto.setPlan(plan);
        dto.setActive(entity.getActive());
        dto.setScheduleReady(scheduleReady(entity, plan));
        dto.setScheduleVersion(entity.getScheduleVersion());
        dto.setScheduleUpdatedAt(entity.getScheduleUpdatedAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private boolean scheduleReady(WorkoutPlanEntity entity, AiWorkoutPlanDraftResponseDto plan) {
        return "workout_schedule_v1".equals(entity.getScheduleVersion())
                && plan.getDays() != null
                && !plan.getDays().isEmpty()
                && plan.getDays().stream().allMatch(day -> day.getScheduledDate() != null
                && day.getSessionIntensity() != null
                && day.getEstimatedDurationMinutes() != null
                && day.getEstimatedDurationMinutes() > 0);
    }

    private AiWorkoutPlanDraftResponseDto existingDraft(UserEntity user, String idempotencyKey) {
        return aiRequestHistoryRepository.findByUserAndRequestTypeAndIdempotencyKey(
                        user, AiRequestType.AI_WORKOUT_PLAN, idempotencyKey)
                .map(history -> AiIdempotencySupport.replayOrReject(
                        history,
                        stored -> {
                            AiWorkoutPlanDraftResponseDto draft = readPlan(stored.getOutputPayload());
                            draft.setRequestId(stored.getId());
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
                        "AI workout-plan"
                )).orElse(null);
    }

    private String cleanFeedback(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.trim().replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ");
        return cleaned.length() <= 1000 ? cleaned : cleaned.substring(0, 1000);
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
                .filter(item -> item.getTechniqueReviewStatus() == ExerciseTechniqueReviewStatus.APPROVED)
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

    private boolean refundConsumedQuota(UserEntity user, int creditCost) {
        try {
            subscriptionService.refundConsumedAiQuota(user.getId(), creditCost);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
