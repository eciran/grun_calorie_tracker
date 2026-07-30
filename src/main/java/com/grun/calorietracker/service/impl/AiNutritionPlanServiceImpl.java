package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.AiProviderException;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.*;
import com.grun.calorietracker.service.support.RecipeAllergenResolver;
import com.grun.calorietracker.service.support.AiSafeResponseBuilder;
import com.grun.calorietracker.service.support.AiUxContractFactory;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiNutritionPlanServiceImpl implements AiNutritionPlanService {

    private static final double CALORIE_TOTAL_TOLERANCE = 8.0;
    private static final double MACRO_TOTAL_TOLERANCE = 3.0;

    private final AiProperties properties;
    private final List<AiMealDraftProviderClient> providerClients;
    private final AiRequestHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final GoalRepository goalRepository;
    private final MealPlanRepository mealPlanRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final MealPlanService mealPlanService;
    private final SubscriptionService subscriptionService;
    private final AiCreditPricingService aiCreditPricingService;
    private final UserNutritionPreferenceService nutritionPreferenceService;
    private final ObjectMapper objectMapper;
    private final AiProviderConfigurationValidator providerConfigurationValidator;

    @Override
    public AiNutritionPlanCreditEstimateDto estimateCreditCost(
            String email, int dayCount, int mealsPerDay,
            NutritionPlanGenerationMode generationMode) {
        if (generationMode == null) {
            throw new IllegalArgumentException("Nutrition-plan generation mode is required.");
        }
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.AI_NUTRITION_PLAN);
        AiCreditCostEstimateDto estimate = aiCreditPricingService.estimateNutrition(
                dayCount, mealsPerDay,
                generationMode == NutritionPlanGenerationMode.WORKOUT_ALIGNED);
        return nutritionPlanCreditEstimate(dayCount, mealsPerDay, generationMode, estimate);
    }

    @Override
    public AiNutritionPlanDraftResponseDto createDraft(
            String email, String idempotencyKey, AiNutritionPlanDraftRequestDto request) {
        validateRequest(request);
        String key = normalizeKey(idempotencyKey);
        providerConfigurationValidator.validateConfiguredForDraft();
        subscriptionService.assertFeatureAccess(email, SubscriptionFeature.AI_NUTRITION_PLAN);
        int creditCost = aiCreditPricingService.estimateNutrition(
                request.getDayCount(),
                request.getMealsPerDay(),
                request.getGenerationMode() == NutritionPlanGenerationMode.WORKOUT_ALIGNED)
                .getTotalCreditCost();
        UserEntity user = user(email);

        AiNutritionPlanDraftResponseDto previous = existing(user, key);
        if (previous != null) {
            return previous;
        }

        UserGoalEntity goal = goalRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Complete onboarding and calorie targets before generating a nutrition plan."));
        MealPlanNutritionSnapshotDto target = target(goal);
        request.setTrustedDailyTarget(target);
        request.setTrustedUserContext(userContext(user, goal));
        applyPersistentNutritionPreferences(email, request);
        request.setTrustedWorkoutContext(workoutContext(user, request));

        AiRequestHistoryEntity history = new AiRequestHistoryEntity();
        history.setUser(user);
        history.setRequestType(AiRequestType.AI_NUTRITION_PLAN);
        history.setProvider(properties.getProvider());
        history.setModel(properties.getModel());
        history.setPromptVersion(properties.getPromptVersion());
        history.setStatus(AiRequestStatus.PROCESSING);
        history.setIdempotencyKey(key);
        history.setInputPayload(json(safeInput(request)));
        history.setCreatedAt(LocalDateTime.now());
        history.setQuotaConsumed(false);
        history.setQuotaConsumedAmount(0);
        try {
            history = historyRepository.save(history);
        } catch (DataIntegrityViolationException ex) {
            AiNutritionPlanDraftResponseDto concurrent = existing(user, key);
            if (concurrent != null) {
                return concurrent;
            }
            throw new RequestConflictException(
                    "An AI nutrition-plan request with this key is already processing.");
        }

        long startedAt = System.nanoTime();
        boolean charged = false;
        try {
            AiNutritionPlanDraftResponseDto response = createValidatedProviderDraft(request, target, history);
            SubscriptionDto quota = subscriptionService.consumeAiQuota(email, creditCost);
            charged = true;
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
            copyUsage(response, history);
            history.setStatus(AiRequestStatus.DRAFT_CREATED);
            history.setOutputPayload(json(response));
            history.setQuotaConsumed(true);
            history.setQuotaConsumedAmount(creditCost);
            history.setLatencyMs(elapsed(startedAt));
            history = historyRepository.save(history);
            response.setRequestId(history.getId());
            return response;
        } catch (RuntimeException ex) {
            if (charged) {
                refund(user, creditCost);
            }
            history.setStatus(AiRequestStatus.FAILED);
            history.setErrorMessage(ex.getMessage());
            history.setOutputPayload(json(AiSafeResponseBuilder.failurePayload(
                    AiRequestType.AI_NUTRITION_PLAN, true, creditCost, false, user.getPreferredLanguage())));
            history.setQuotaConsumed(false);
            history.setQuotaConsumedAmount(0);
            history.setLatencyMs(elapsed(startedAt));
            historyRepository.save(history);
            if (ex instanceof AiProviderException) {
                throw ex;
            }
            throw new AiProviderException("AI nutrition plan could not produce a usable result.");
        }
    }

    @Override
    @Transactional
    public MealPlanDto confirmDraft(
            String email, Long requestId, AiNutritionPlanConfirmRequestDto request) {
        if (request == null || request.getDraft() == null) {
            throw new IllegalArgumentException("Reviewed nutrition-plan draft is required.");
        }
        UserEntity user = userForUpdate(email);
        AiRequestHistoryEntity history = ownedHistory(user, requestId);
        if (history.getStatus() == AiRequestStatus.CONFIRMED) {
            Long planId = confirmedPlanId(history);
            if (planId != null) {
                return mealPlanService.getMealPlan(email, planId);
            }
        }
        if (history.getStatus() != AiRequestStatus.DRAFT_CREATED) {
            throw new IllegalArgumentException("AI nutrition-plan draft is not open for confirmation.");
        }

        AiNutritionPlanDraftResponseDto original = readDraft(history);
        AiNutritionPlanDraftResponseDto reviewed = request.getDraft();
        reviewed.setGenerationMode(original.getGenerationMode());
        reviewed.setWorkoutPlanId(original.getWorkoutPlanId());
        reviewed.setStartDate(original.getStartDate());
        reviewed.setEndDate(original.getEndDate());
        reviewed.setDailyTarget(original.getDailyTarget());
        AiNutritionPlanDraftRequestDto validationRequest = requestFrom(original);
        applyPersistentNutritionPreferences(email, validationRequest);
        WorkoutNutritionContextDto currentWorkoutContext = workoutContext(user, validationRequest);
        validationRequest.setTrustedWorkoutContext(currentWorkoutContext);
        if (original.getGenerationMode() == NutritionPlanGenerationMode.WORKOUT_ALIGNED
                && !Objects.equals(original.getWorkoutScheduleUpdatedAt(),
                currentWorkoutContext.getScheduleUpdatedAt())) {
            throw new RequestConflictException(
                    "Workout schedule changed after this nutrition draft was generated. Generate a new draft.");
        }
        normalizeAndValidate(reviewed, validationRequest, original.getDailyTarget());

        MealPlanDto created = mealPlanService.createMealPlan(email, toMealPlanRequest(reviewed));
        MealPlanEntity plan = mealPlanRepository.findByIdAndUser(created.getId(), user)
                .orElseThrow(() -> new IllegalStateException("Confirmed meal plan could not be loaded."));
        plan.setSourceAiRequest(history);
        plan.setSchemaVersion(reviewed.getSchemaVersion());
        plan.setPromptVersion(history.getPromptVersion());
        mealPlanRepository.findByUserAndStatus(user, MealPlanStatus.ACTIVE)
                .stream()
                .filter(active -> !active.getId().equals(plan.getId()))
                .forEach(active -> active.setStatus(MealPlanStatus.DRAFT));
        plan.setStatus(MealPlanStatus.ACTIVE);
        plan.getItems().forEach(item -> {
            item.setSourceAiRequest(history);
            item.setSchemaVersion(reviewed.getSchemaVersion());
            item.setPromptVersion(history.getPromptVersion());
        });
        mealPlanRepository.save(plan);

        history.setStatus(AiRequestStatus.CONFIRMED);
        history.setConfirmationPayload(json(Map.of("mealPlanId", plan.getId())));
        history.setConfirmedAt(LocalDateTime.now());
        historyRepository.save(history);
        return mealPlanService.getMealPlan(email, plan.getId());
    }

    @Override
    @Transactional
    public void rejectDraft(String email, Long requestId, AiMealDraftRejectRequestDto request) {
        AiRequestHistoryEntity history = ownedHistory(user(email), requestId);
        if (history.getStatus() != AiRequestStatus.DRAFT_CREATED) {
            throw new IllegalArgumentException("AI nutrition-plan draft is not open for rejection.");
        }
        history.setStatus(AiRequestStatus.REJECTED);
        history.setRejectionReason(request == null ? null : request.getReason());
        history.setRejectionFeedback(feedback(request == null ? null : request.getFeedback()));
        history.setRejectedAt(LocalDateTime.now());
        historyRepository.save(history);
    }

    private void validateRequest(AiNutritionPlanDraftRequestDto request) {
        if (request == null || request.getGenerationMode() == null || request.getStartDate() == null
                || request.getDayCount() == null || request.getMealsPerDay() == null) {
            throw new IllegalArgumentException(
                    "Nutrition-plan mode, start date, day count, and meal count are required.");
        }
        if (request.getGenerationMode() == NutritionPlanGenerationMode.GENERAL
                && request.getWorkoutPlanId() != null) {
            throw new IllegalArgumentException("GENERAL nutrition plans cannot reference a workout plan.");
        }
        if (request.getGenerationMode() == NutritionPlanGenerationMode.WORKOUT_ALIGNED
                && request.getWorkoutPlanId() == null) {
            throw new IllegalArgumentException("WORKOUT_ALIGNED nutrition plans require workoutPlanId.");
        }
        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Nutrition-plan start date cannot be in the past.");
        }
        if (request.getDayCount() < 1 || request.getDayCount() > 7) {
            throw new IllegalArgumentException("Nutrition plans can cover between 1 and 7 days.");
        }
        if (request.getMealsPerDay() < 2 || request.getMealsPerDay() > 6) {
            throw new IllegalArgumentException("Nutrition plans require between 2 and 6 meals per day.");
        }
        request.setExcludedFoods(cleanList(request.getExcludedFoods(), 30, 80));
        request.setDietaryPreferences(cleanList(request.getDietaryPreferences(), 20, 80));
        if (request.getPreferredMealTimes() != null && !request.getPreferredMealTimes().isEmpty()
                && request.getPreferredMealTimes().size() != request.getMealsPerDay()) {
            throw new IllegalArgumentException(
                    "Preferred meal times must be empty or match mealsPerDay.");
        }
    }

    private void normalizeAndValidate(
            AiNutritionPlanDraftResponseDto response,
            AiNutritionPlanDraftRequestDto request,
            MealPlanNutritionSnapshotDto trustedTarget) {
        if (response == null) {
            throw new IllegalArgumentException("AI nutrition provider returned an empty response.");
        }
        response.setSchemaVersion("ai_nutrition_plan_v1");
        response.setRequestType(AiRequestType.AI_NUTRITION_PLAN);
        response.setStatus(AiRequestStatus.DRAFT_CREATED);
        response.setProvider(properties.getProvider());
        response.setModel(properties.getModel());
        response.setGenerationMode(request.getGenerationMode());
        response.setWorkoutPlanId(request.getWorkoutPlanId());
        response.setWorkoutScheduleVersion(request.getTrustedWorkoutContext() == null
                ? null : request.getTrustedWorkoutContext().getScheduleVersion());
        response.setWorkoutScheduleUpdatedAt(request.getTrustedWorkoutContext() == null
                ? null : request.getTrustedWorkoutContext().getScheduleUpdatedAt());
        response.setStartDate(request.getStartDate());
        response.setEndDate(request.getStartDate().plusDays(request.getDayCount() - 1L));
        response.setDailyTarget(trustedTarget);
        response.setReviewRequired(true);
        response.setName(name(response.getName(), "AI Nutrition Plan"));
        response.setSummary(text(response.getSummary(), "Nutrition-plan summary", 1500));
        response.setProfessionalSummary(text(
                response.getProfessionalSummary(), "Nutrition-plan professional summary", 2500));
        response.setAssumptions(cleanList(response.getAssumptions(), 20, 300));
        response.setWarnings(new ArrayList<>(cleanList(response.getWarnings(), 20, 300)));
        response.setNextBestActions(cleanList(response.getNextBestActions(), 10, 300));
        validateQuality(response);

        if (response.getDays() == null || response.getDays().size() != request.getDayCount()) {
            throw new IllegalArgumentException("AI nutrition provider returned an invalid number of days.");
        }
        for (int index = 0; index < response.getDays().size(); index++) {
            validateDay(response.getDays().get(index), request.getStartDate().plusDays(index),
                    request, trustedTarget, response);
        }
    }

    private void validateDay(AiNutritionPlanDayDto day, LocalDate date,
                             AiNutritionPlanDraftRequestDto request,
                             MealPlanNutritionSnapshotDto target,
                             AiNutritionPlanDraftResponseDto response) {
        if (day == null || !date.equals(day.getDate())) {
            throw new IllegalArgumentException("AI nutrition provider returned an unexpected plan date.");
        }
        day.setDayType(nutritionDayType(date, request));
        if (day.getMeals() == null || day.getMeals().size() != request.getMealsPerDay()) {
            throw new IllegalArgumentException("AI nutrition provider returned an invalid meal count.");
        }
        MealPlanNutritionSnapshotDto sum = emptyNutrition();
        for (AiNutritionPlanMealDto meal : day.getMeals()) {
            validateMeal(meal, request, date);
            add(sum, meal.getTotalNutrition());
        }
        if (day.getTotalNutrition() == null) {
            day.setTotalNutrition(sum);
        } else {
            nutrition(day.getTotalNutrition(), "daily total");
            totals(sum, day.getTotalNutrition(), "daily meal totals");
        }
        mergeDailyMicronutrients(day.getTotalNutrition(), day.getDailyMicronutrients());
        validateDailyTarget(response, day.getTotalNutrition().getCalories(), target.getCalories(),
                Math.max(100.0, target.getCalories() * 0.15),
                Math.max(150.0, target.getCalories() * 0.25), "calories", date, true);
        validateDailyTarget(response, day.getTotalNutrition().getProtein(), target.getProtein(),
                Math.max(20.0, target.getProtein() * 0.20),
                Math.max(40.0, target.getProtein() * 0.45), "protein", date, false);
        validateDailyTarget(response, day.getTotalNutrition().getCarbs(), target.getCarbs(),
                Math.max(30.0, target.getCarbs() * 0.20),
                Math.max(70.0, target.getCarbs() * 0.45), "carbohydrates", date, false);
        validateDailyTarget(response, day.getTotalNutrition().getFat(), target.getFat(),
                Math.max(15.0, target.getFat() * 0.20),
                Math.max(30.0, target.getFat() * 0.60), "fat", date, false);
    }

    private AiNutritionPlanDraftResponseDto createValidatedProviderDraft(
            AiNutritionPlanDraftRequestDto request,
            MealPlanNutritionSnapshotDto target,
            AiRequestHistoryEntity history) {
        AiNutritionPlanDraftResponseDto first = provider().createNutritionPlanDraft(request);
        copyUsage(first, history);
        try {
            normalizeAndValidate(first, request, target);
            return first;
        } catch (IllegalArgumentException validationFailure) {
            if (!isRepairableNutritionValidation(validationFailure.getMessage())) {
                throw validationFailure;
            }
            request.setTrustedValidationFeedback(validationFailure.getMessage());
            history.setCorrectionSummary("Controlled nutrition validation retry requested: "
                    + validationFailure.getMessage());
            AiNutritionPlanDraftResponseDto repaired = provider().createNutritionPlanDraft(request);
            mergeUsage(repaired, first);
            copyUsage(repaired, history);
            normalizeAndValidate(repaired, request, target);
            history.setCorrectionSummary("Controlled nutrition validation retry succeeded: "
                    + validationFailure.getMessage());
            return repaired;
        } finally {
            request.setTrustedValidationFeedback(null);
        }
    }

    private boolean isRepairableNutritionValidation(String message) {
        if (message == null) {
            return false;
        }
        return message.startsWith("Daily ")
                || message.contains("inconsistent")
                || message.contains("invalid number of days")
                || message.contains("invalid meal count")
                || message.contains("unexpected plan date")
                || message.contains("invalid quality metadata");
    }

    private void mergeUsage(AiNutritionPlanDraftResponseDto repaired,
                            AiNutritionPlanDraftResponseDto first) {
        if (repaired == null || first == null) {
            return;
        }
        repaired.setPromptTokens(sum(repaired.getPromptTokens(), first.getPromptTokens()));
        repaired.setCompletionTokens(sum(repaired.getCompletionTokens(), first.getCompletionTokens()));
        repaired.setTotalTokens(sum(repaired.getTotalTokens(), first.getTotalTokens()));
        repaired.setEstimatedCost(sum(repaired.getEstimatedCost(), first.getEstimatedCost()));
        if (repaired.getCostCurrency() == null) {
            repaired.setCostCurrency(first.getCostCurrency());
        }
    }

    private Integer sum(Integer left, Integer right) {
        return left == null && right == null ? null : Objects.requireNonNullElse(left, 0)
                + Objects.requireNonNullElse(right, 0);
    }

    private Double sum(Double left, Double right) {
        return left == null && right == null ? null : Objects.requireNonNullElse(left, 0d)
                + Objects.requireNonNullElse(right, 0d);
    }

    private NutritionPlanDayType nutritionDayType(
            LocalDate date, AiNutritionPlanDraftRequestDto request) {
        if (request.getGenerationMode() == NutritionPlanGenerationMode.GENERAL) {
            return NutritionPlanDayType.GENERAL;
        }
        WorkoutNutritionContextDto context = request.getTrustedWorkoutContext();
        boolean training = context.getSessions().stream()
                .anyMatch(session -> date.equals(session.getDate()));
        if (training) {
            return NutritionPlanDayType.TRAINING;
        }
        boolean recovery = context.getSessions().stream()
                .anyMatch(session -> date.minusDays(1).equals(session.getDate()));
        return recovery ? NutritionPlanDayType.RECOVERY : NutritionPlanDayType.REST;
    }

    private void validateMeal(AiNutritionPlanMealDto meal, AiNutritionPlanDraftRequestDto request, LocalDate date) {
        if (meal == null || meal.getMealType() == null
                || !meal.getMealType().matches("(?i)BREAKFAST|LUNCH|DINNER|SNACK")) {
            throw new IllegalArgumentException("AI nutrition provider returned an invalid meal type.");
        }
        meal.setMealType(meal.getMealType().toUpperCase(Locale.ROOT));
        if (meal.getItems() == null || meal.getItems().isEmpty() || meal.getItems().size() > 12) {
            throw new IllegalArgumentException("AI nutrition provider returned invalid meal items.");
        }
        MealPlanNutritionSnapshotDto sum = emptyNutrition();
        for (AiNutritionPlanItemDto item : meal.getItems()) {
            validateItem(item, request, date, meal.getSuggestedTime());
            add(sum, item.getNutrition());
        }
        if (meal.getTotalNutrition() == null) {
            meal.setTotalNutrition(sum);
        } else {
            nutrition(meal.getTotalNutrition(), "meal total");
            totals(sum, meal.getTotalNutrition(), "meal item totals");
        }
    }

    private void validateItem(AiNutritionPlanItemDto item, AiNutritionPlanDraftRequestDto request,
                              LocalDate date, java.time.LocalTime mealTime) {
        if (item == null) {
            throw new IllegalArgumentException("AI nutrition provider returned an empty item.");
        }
        item.setDisplayName(name(item.getDisplayName(), null));
        item.setGroceryName(groceryName(item.getGroceryName(), item.getDisplayName()));
        if (item.getPreparationMethod() == null) {
            item.setPreparationMethod(FoodPreparationState.UNSPECIFIED);
        }
        if (item.getQuantity() == null || !Double.isFinite(item.getQuantity())
                || item.getQuantity() <= 0 || item.getQuantity() > 100_000 || item.getUnit() == null) {
            throw new IllegalArgumentException("AI nutrition provider returned an invalid item portion.");
        }
        nutrition(item.getNutrition(), "item nutrition");
        item.setAllergens(cleanList(item.getAllergens(), 20, 120));
        item.setWarnings(new ArrayList<>(cleanList(item.getWarnings(), 20, 300)));
        item.setAssumptions(cleanList(item.getAssumptions(), 20, 300));
        if (item.getWorkoutRelation() == null) {
            item.setWorkoutRelation(MealPlanWorkoutRelation.NONE);
        }
        item.setWorkoutRelation(normalizeWorkoutRelation(
                item.getWorkoutRelation(), request, date, mealTime, item.getWarnings()));
        for (String excluded : request.getExcludedFoods()) {
            String searchableName = item.getDisplayName() + " " + item.getGroceryName();
            if (searchableName.toLowerCase(Locale.ROOT)
                    .contains(excluded.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("AI nutrition provider included an excluded food.");
            }
        }
        Set<RecipeAllergen> detectedAllergens = RecipeAllergenResolver.resolve(
                String.join(",", item.getAllergens()) + " " + item.getDisplayName() + " "
                        + item.getGroceryName() + " "
                        + Objects.toString(item.getDescription(), ""));
        for (String trustedAllergen : request.getTrustedAllergens()) {
            RecipeAllergen allergen = parseAllergen(trustedAllergen);
            if (allergen != null && detectedAllergens.contains(allergen)) {
                throw new IllegalArgumentException(
                        "AI nutrition provider included a food that conflicts with the user's allergen profile.");
            }
        }
    }

    private RecipeAllergen parseAllergen(String value) {
        if (value == null) {
            return null;
        }
        try {
            return RecipeAllergen.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private MealPlanWorkoutRelation normalizeWorkoutRelation(
            MealPlanWorkoutRelation relation,
            AiNutritionPlanDraftRequestDto request,
            LocalDate date, java.time.LocalTime mealTime, List<String> warnings) {
        if (request.getGenerationMode() == NutritionPlanGenerationMode.GENERAL) {
            return MealPlanWorkoutRelation.NONE;
        }
        WorkoutNutritionContextDto context = request.getTrustedWorkoutContext();
        if (context == null) {
            throw new IllegalArgumentException("Trusted workout schedule context is required.");
        }
        WorkoutNutritionSessionContextDto session = context.getSessions().stream()
                .filter(value -> date.equals(value.getDate()))
                .findFirst()
                .orElse(null);
        if (relation == MealPlanWorkoutRelation.NONE) {
            return relation;
        }
        if (relation == MealPlanWorkoutRelation.RECOVERY) {
            boolean recoveryDay = session != null || context.getSessions().stream()
                    .anyMatch(value -> date.minusDays(1).equals(value.getDate()));
            return recoveryDay ? relation : removeInvalidWorkoutRelation(warnings);
        }
        if (session == null || session.getStartTime() == null || mealTime == null) {
            return removeInvalidWorkoutRelation(warnings);
        }
        java.time.LocalTime workoutEnd = session.getStartTime()
                .plusMinutes(session.getDurationMinutes());
        if (relation == MealPlanWorkoutRelation.PRE_WORKOUT) {
            long minutes = java.time.Duration.between(mealTime, session.getStartTime()).toMinutes();
            if (minutes < 30 || minutes > 240) {
                return removeInvalidWorkoutRelation(warnings);
            }
        } else if (relation == MealPlanWorkoutRelation.POST_WORKOUT) {
            long minutes = java.time.Duration.between(workoutEnd, mealTime).toMinutes();
            if (minutes < 0 || minutes > 240) {
                return removeInvalidWorkoutRelation(warnings);
            }
        }
        return relation;
    }

    private MealPlanWorkoutRelation removeInvalidWorkoutRelation(List<String> warnings) {
        String warning = "Workout timing was not applied because the suggested meal time did not match the confirmed workout schedule.";
        if (warnings != null && warnings.size() < 20 && !warnings.contains(warning)) {
            warnings.add(warning);
        }
        return MealPlanWorkoutRelation.NONE;
    }

    private void nutrition(MealPlanNutritionSnapshotDto value, String field) {
        if (value == null || value.getCalories() == null || value.getProtein() == null
                || value.getCarbs() == null || value.getFat() == null) {
            throw new IllegalArgumentException(field + " requires calories and macros.");
        }
        double[] numbers = {
                zero(value.getCalories()), zero(value.getProtein()), zero(value.getCarbs()),
                zero(value.getFat()), zero(value.getFiber()), zero(value.getSugar()),
                zero(value.getSaturatedFat()), zero(value.getSodium()), zero(value.getPotassium()),
                zero(value.getCholesterol()), zero(value.getCalcium()), zero(value.getIron()),
                zero(value.getMagnesium()), zero(value.getZinc()), zero(value.getVitaminA()),
                zero(value.getVitaminC()), zero(value.getVitaminD()), zero(value.getVitaminE()),
                zero(value.getVitaminB12())
        };
        for (double number : numbers) {
            if (!Double.isFinite(number) || number < 0) {
                throw new IllegalArgumentException(field + " contains invalid nutrition.");
            }
        }
        if (value.getCalories() > 10_000 || value.getProtein() > 1_000
                || value.getCarbs() > 2_000 || value.getFat() > 1_000) {
            throw new IllegalArgumentException(field + " contains unrealistic nutrition.");
        }
    }

    private void totals(MealPlanNutritionSnapshotDto actual,
                        MealPlanNutritionSnapshotDto declared, String field) {
        close(actual.getCalories(), declared.getCalories(), CALORIE_TOTAL_TOLERANCE,
                field + " calories are inconsistent");
        close(actual.getProtein(), declared.getProtein(), MACRO_TOTAL_TOLERANCE,
                field + " protein is inconsistent");
        close(actual.getCarbs(), declared.getCarbs(), MACRO_TOTAL_TOLERANCE,
                field + " carbs are inconsistent");
        close(actual.getFat(), declared.getFat(), MACRO_TOTAL_TOLERANCE,
                field + " fat is inconsistent");
    }

    private void close(Double actual, Double expected, double tolerance, String message) {
        if (actual == null || expected == null || Math.abs(actual - expected) > tolerance) {
            throw new IllegalArgumentException(message + ".");
        }
    }

    private void validateDailyTarget(AiNutritionPlanDraftResponseDto response,
                                     Double actual, Double target,
                                     double preferredTolerance, double hardTolerance,
                                     String nutrient, LocalDate date, boolean failWhenOutsideHardRange) {
        double difference = actual == null || target == null
                ? Double.POSITIVE_INFINITY : Math.abs(actual - target);
        if (difference > hardTolerance && failWhenOutsideHardRange) {
            double minimum = target == null ? 0 : Math.max(0, target - hardTolerance);
            double maximum = target == null ? 0 : target + hardTolerance;
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "Daily %s exceeds the allowed target tolerance for %s: actual=%.1f, target=%.1f, allowedRange=%.1f..%.1f",
                    nutrient, date, actual == null ? 0 : actual, target == null ? 0 : target,
                    minimum, maximum));
        }
        if (difference > preferredTolerance && response.getWarnings().size() < 20) {
            String warning = String.format(Locale.ROOT,
                    "%s %s is slightly outside the preferred target range; review portions before confirming.",
                    date, nutrient);
            if (!response.getWarnings().contains(warning)) {
                response.getWarnings().add(warning);
            }
        }
    }

    private void validateQuality(AiNutritionPlanDraftResponseDto response) {
        if (response.getConfidence() == null || !Double.isFinite(response.getConfidence())
                || response.getConfidence() < 0 || response.getConfidence() > 1
                || response.getQualityScore() == null || response.getQualityScore() < 0
                || response.getQualityScore() > 100
                || response.getEstimatedUncertainty() == null
                || !response.getEstimatedUncertainty().matches("LOW|MEDIUM|HIGH")) {
            throw new IllegalArgumentException("AI nutrition provider returned invalid quality metadata.");
        }
    }

    private MealPlanRequestDto toMealPlanRequest(AiNutritionPlanDraftResponseDto draft) {
        MealPlanRequestDto request = new MealPlanRequestDto();
        request.setName(draft.getName());
        request.setStartDate(draft.getStartDate() == null
                ? draft.getDays().get(0).getDate()
                : draft.getStartDate());
        request.setEndDate(draft.getEndDate());
        request.setGenerationMode(draft.getGenerationMode());
        request.setWorkoutPlanId(draft.getWorkoutPlanId());
        List<MealPlanItemRequestDto> items = new ArrayList<>();
        for (AiNutritionPlanDayDto day : draft.getDays()) {
            for (AiNutritionPlanMealDto meal : day.getMeals()) {
                for (AiNutritionPlanItemDto source : meal.getItems()) {
                    MealPlanItemRequestDto item = new MealPlanItemRequestDto();
                    item.setPlanDate(day.getDate());
                    item.setMealType(meal.getMealType());
                    item.setItemType(MealPlanItemType.AI_SNAPSHOT);
                    item.setSnapshotName(source.getDisplayName());
                    item.setGroceryName(source.getGroceryName());
                    item.setPreparationMethod(source.getPreparationMethod());
                    item.setSnapshotDescription(source.getDescription());
                    item.setShortPreparationState(source.getShortPreparationState());
                    item.setPortionSize(source.getQuantity());
                    item.setPortionUnit(source.getUnit());
                    item.setSnapshotNutrition(source.getNutrition());
                    item.setAllergens(source.getAllergens());
                    item.setWarnings(source.getWarnings());
                    item.setAssumptions(source.getAssumptions());
                    item.setWorkoutRelation(source.getWorkoutRelation());
                    items.add(item);
                }
            }
        }
        request.setItems(items);
        return request;
    }

    private AiNutritionPlanDraftRequestDto requestFrom(AiNutritionPlanDraftResponseDto draft) {
        AiNutritionPlanDraftRequestDto request = new AiNutritionPlanDraftRequestDto();
        request.setGenerationMode(draft.getGenerationMode());
        request.setWorkoutPlanId(draft.getWorkoutPlanId());
        request.setStartDate(draft.getStartDate() == null
                ? draft.getDays().get(0).getDate()
                : draft.getStartDate());
        request.setDayCount(draft.getDays().size());
        request.setMealsPerDay(draft.getDays().get(0).getMeals().size());
        request.setExcludedFoods(List.of());
        request.setDietaryPreferences(List.of());
        return request;
    }

    private MealPlanNutritionSnapshotDto target(UserGoalEntity goal) {
        if (goal.getDailyCalorieGoal() == null || goal.getDailyCalorieGoal() < 800
                || goal.getDailyCalorieGoal() > 6000 || goal.getDailyProteinGoal() == null
                || goal.getDailyCarbGoal() == null || goal.getDailyFatGoal() == null) {
            throw new IllegalArgumentException(
                    "A valid calorie and macro target is required before plan generation.");
        }
        MealPlanNutritionSnapshotDto target = emptyNutrition();
        target.setCalories(goal.getDailyCalorieGoal().doubleValue());
        target.setProtein(goal.getDailyProteinGoal());
        target.setCarbs(goal.getDailyCarbGoal());
        target.setFat(goal.getDailyFatGoal());
        return target;
    }

    private WorkoutNutritionContextDto workoutContext(
            UserEntity user, AiNutritionPlanDraftRequestDto request) {
        if (request.getGenerationMode() == NutritionPlanGenerationMode.GENERAL) {
            return null;
        }
        WorkoutPlanEntity workoutPlan = workoutPlanRepository
                .findByIdAndUser(request.getWorkoutPlanId(), user)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Active workout plan was not found."));
        if (!Boolean.TRUE.equals(workoutPlan.getActive())
                || workoutPlan.getStatus() != WorkoutPlanStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "WORKOUT_ALIGNED generation requires an active workout plan.");
        }
        if (!"workout_schedule_v1".equals(workoutPlan.getScheduleVersion())
                || workoutPlan.getScheduleUpdatedAt() == null) {
            throw new IllegalArgumentException(
                    "Save the workout plan schedule before generating a workout-aligned nutrition plan.");
        }

        AiWorkoutPlanDraftResponseDto storedPlan;
        try {
            storedPlan = objectMapper.readValue(
                    workoutPlan.getPlanPayload(), AiWorkoutPlanDraftResponseDto.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored workout plan schedule is unavailable.");
        }
        if (storedPlan.getDays() == null || storedPlan.getDays().isEmpty()) {
            throw new IllegalArgumentException("Workout plan has no scheduled sessions.");
        }

        Set<LocalDate> uniqueDates = new HashSet<>();
        List<WorkoutNutritionSessionContextDto> sessions = new ArrayList<>();
        LocalDate endDate = request.getStartDate().plusDays(request.getDayCount() - 1L);
        for (AiWorkoutPlanDayDto day : storedPlan.getDays()) {
            if (day.getScheduledDate() == null || day.getSessionIntensity() == null
                    || day.getEstimatedDurationMinutes() == null
                    || day.getEstimatedDurationMinutes() <= 0) {
                throw new IllegalArgumentException(
                        "Workout plan schedule is incomplete. Save every workout day before continuing.");
            }
            if (!uniqueDates.add(day.getScheduledDate())) {
                throw new IllegalArgumentException("Workout plan schedule contains duplicate dates.");
            }
            if (!day.getScheduledDate().isBefore(request.getStartDate())
                    && !day.getScheduledDate().isAfter(endDate)) {
                List<AiWorkoutPlanExerciseDto> exercises = day.getExercises() == null
                        ? List.of() : day.getExercises();
                int totalWorkingSets = exercises.stream()
                        .map(AiWorkoutPlanExerciseDto::getSetCount)
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .sum();
                int totalExerciseDurationMinutes = exercises.stream()
                        .map(AiWorkoutPlanExerciseDto::getDurationMinutes)
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .sum();
                sessions.add(new WorkoutNutritionSessionContextDto(
                        day.getScheduledDate(), day.getScheduledStartTime(),
                        day.getEstimatedDurationMinutes(), day.getSessionIntensity(),
                        safeFocus(day.getFocus()), exercises.size(), totalWorkingSets,
                        totalExerciseDurationMinutes));
            }
        }
        if (sessions.isEmpty()) {
            throw new IllegalArgumentException(
                    "The selected workout plan has no sessions inside the nutrition-plan date range.");
        }
        sessions.sort(Comparator.comparing(WorkoutNutritionSessionContextDto::getDate));
        WorkoutNutritionContextDto context = new WorkoutNutritionContextDto();
        context.setWorkoutPlanId(workoutPlan.getId());
        context.setWorkoutPlanName(safeFocus(workoutPlan.getName()));
        context.setScheduleVersion(workoutPlan.getScheduleVersion());
        context.setScheduleUpdatedAt(workoutPlan.getScheduleUpdatedAt());
        context.setSessions(sessions);
        return context;
    }

    private String safeFocus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.trim().replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ");
        return cleaned.length() > 160 ? cleaned.substring(0, 160) : cleaned;
    }

    private Map<String, Object> userContext(UserEntity user, UserGoalEntity goal) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("age", user.getAge());
        context.put("gender", user.getGender());
        context.put("heightCm", user.getHeight());
        context.put("weightKg", user.getWeight());
        context.put("marketRegion", user.getMarketRegion());
        context.put("preferredLanguage", user.getPreferredLanguage());
        context.put("unitPreference", user.getUnitPreference());
        context.put("goalType", goal.getGoalType());
        context.put("activityLevel", goal.getActivityLevel());
        context.put("targetWeightKg", goal.getTargetWeight());
        return context;
    }

    private Map<String, Object> safeInput(AiNutritionPlanDraftRequestDto request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generationMode", request.getGenerationMode());
        payload.put("workoutPlanId", request.getWorkoutPlanId());
        payload.put("workoutSessionCount", request.getTrustedWorkoutContext() == null
                ? 0 : request.getTrustedWorkoutContext().getSessions().size());
        payload.put("startDate", request.getStartDate());
        payload.put("dayCount", request.getDayCount());
        payload.put("mealsPerDay", request.getMealsPerDay());
        payload.put("preferredMealTimeCount", count(request.getPreferredMealTimes()));
        payload.put("excludedFoodCount", count(request.getExcludedFoods()));
        payload.put("dietaryPreferenceCount", count(request.getDietaryPreferences()));
        payload.put("profileAllergenCount", count(request.getTrustedAllergens()));
        payload.put("budgetPreference", request.getBudgetPreference());
        payload.put("preparationTimePreference", request.getPreparationTimePreference());
        payload.put("includeRecipeSuggestions", request.getIncludeRecipeSuggestions());
        payload.put("language", request.getLanguage());
        return payload;
    }

    private void applyPersistentNutritionPreferences(
            String email, AiNutritionPlanDraftRequestDto request) {
        UserNutritionPreferenceDto persistent = nutritionPreferenceService.get(email);
        if (persistent == null) {
            persistent = new UserNutritionPreferenceDto();
        }
        request.setExcludedFoods(mergePreferences(
                persistent.getExcludedFoods(), request.getExcludedFoods(), 30));
        request.setDietaryPreferences(mergePreferences(
                persistent.getDietaryPreferences(), request.getDietaryPreferences(), 20));
        request.setTrustedAllergens(persistent.getAllergens() == null
                ? new ArrayList<>()
                : persistent.getAllergens().stream()
                .filter(Objects::nonNull)
                .map(Enum::name)
                .sorted()
                .toList());
    }

    private List<String> mergePreferences(
            List<String> persistent, List<String> requestValues, int maxItems) {
        List<String> combined = new ArrayList<>();
        if (persistent != null) {
            combined.addAll(persistent);
        }
        if (requestValues != null) {
            combined.addAll(requestValues);
        }
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (String value : cleanList(combined, Math.max(maxItems, combined.size()), 80)) {
            unique.putIfAbsent(value.toLowerCase(Locale.ROOT), value);
        }
        if (unique.size() > maxItems) {
            throw new IllegalArgumentException("Too many combined nutrition preference values.");
        }
        return new ArrayList<>(unique.values());
    }

    private AiNutritionPlanDraftResponseDto existing(UserEntity user, String key) {
        return historyRepository.findByUserAndRequestTypeAndIdempotencyKey(
                        user, AiRequestType.AI_NUTRITION_PLAN, key)
                .map(history -> {
                    if (history.getStatus() == AiRequestStatus.DRAFT_CREATED
                            || history.getStatus() == AiRequestStatus.CONFIRMED
                            || history.getStatus() == AiRequestStatus.REJECTED) {
                        return readDraft(history);
                    }
                    if (history.getStatus() == AiRequestStatus.PROCESSING) {
                        throw new RequestConflictException(
                                "An AI nutrition-plan request with this key is already processing.");
                    }
                    throw new IllegalArgumentException(
                            "This idempotency key was already used by a failed request.");
                }).orElse(null);
    }

    private AiRequestHistoryEntity ownedHistory(UserEntity user, Long id) {
        AiRequestHistoryEntity history = historyRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException(
                        "AI nutrition-plan draft was not found."));
        if (history.getRequestType() != AiRequestType.AI_NUTRITION_PLAN) {
            throw new IllegalArgumentException("AI request is not a nutrition-plan draft.");
        }
        return history;
    }

    private AiNutritionPlanDraftResponseDto readDraft(AiRequestHistoryEntity history) {
        try {
            AiNutritionPlanDraftResponseDto response = objectMapper.readValue(
                    history.getOutputPayload(), AiNutritionPlanDraftResponseDto.class);
            response.setRequestId(history.getId());
            return response;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored AI nutrition-plan draft is unavailable.");
        }
    }

    private Long confirmedPlanId(AiRequestHistoryEntity history) {
        try {
            if (history.getConfirmationPayload() == null) {
                return null;
            }
            long value = objectMapper.readTree(history.getConfirmationPayload())
                    .path("mealPlanId").asLong(0);
            return value <= 0 ? null : value;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private AiNutritionPlanCreditEstimateDto nutritionPlanCreditEstimate(
            int dayCount,
            int mealsPerDay,
            NutritionPlanGenerationMode generationMode,
            AiCreditCostEstimateDto estimate) {
        return new AiNutritionPlanCreditEstimateDto(
                dayCount,
                mealsPerDay,
                generationMode,
                estimate.getBaseCreditCost(),
                estimate.getComplexityUnits(),
                estimate.getIncludedUnits(),
                estimate.getUnitsPerAdditionalCredit(),
                estimate.getAdditionalCredits(),
                estimate.getContextIncluded(),
                estimate.getContextSurcharge(),
                estimate.getTotalCreditCost());
    }

    private String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required.");
        }
        String key = value.trim();
        if (key.length() < 8 || key.length() > 100
                || !key.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must contain 8-100 safe characters.");
        }
        return key;
    }

    private List<String> cleanList(List<String> values, int maxItems, int maxLength) {
        if (values == null) {
            return new ArrayList<>();
        }
        if (values.size() > maxItems) {
            throw new IllegalArgumentException("AI nutrition-plan list contains too many values.");
        }
        return values.stream().map(value -> {
            if (value == null) {
                throw new IllegalArgumentException(
                        "AI nutrition-plan list contains an invalid value.");
            }
            String cleaned = value.trim().replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ");
            if (cleaned.isBlank() || cleaned.length() > maxLength) {
                throw new IllegalArgumentException(
                        "AI nutrition-plan list contains an invalid value.");
            }
            return cleaned;
        }).distinct().toList();
    }

    private String feedback(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.trim().replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ");
        return cleaned.length() > 500 ? cleaned.substring(0, 500) : cleaned;
    }

    private String text(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " is missing or too long.");
        }
        return value.trim();
    }

    private String name(String value, String fallback) {
        String source = value == null || value.isBlank() ? fallback : value;
        String normalized = source == null ? null
                : FoodProductNormalizationRules.normalizeProductDisplayName(source);
        if (normalized == null || normalized.isBlank() || normalized.length() > 255) {
            throw new IllegalArgumentException(
                    "AI nutrition provider returned an invalid display name.");
        }
        return normalized;
    }

    private String groceryName(String value, String fallback) {
        String normalized = name(value, fallback);
        if (normalized.length() > 160) {
            throw new IllegalArgumentException("AI nutrition provider returned an invalid grocery name.");
        }
        return normalized;
    }

    private void mergeDailyMicronutrients(
            MealPlanNutritionSnapshotDto total,
            MealPlanNutritionSnapshotDto micronutrients) {
        if (micronutrients == null) {
            return;
        }
        double[] values = {
                zero(micronutrients.getSodium()), zero(micronutrients.getPotassium()),
                zero(micronutrients.getCalcium()), zero(micronutrients.getIron()),
                zero(micronutrients.getMagnesium()), zero(micronutrients.getZinc()),
                zero(micronutrients.getVitaminC()), zero(micronutrients.getVitaminD()),
                zero(micronutrients.getVitaminB12())
        };
        for (double value : values) {
            if (!Double.isFinite(value) || value < 0 || value > 1_000_000) {
                throw new IllegalArgumentException(
                        "Daily micronutrient estimate contains an invalid value.");
            }
        }
        add(total, micronutrients);
    }

    private MealPlanNutritionSnapshotDto emptyNutrition() {
        return new MealPlanNutritionSnapshotDto(
                0.0, 0.0, 0.0, 0.0, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    private void add(MealPlanNutritionSnapshotDto target, MealPlanNutritionSnapshotDto value) {
        target.setCalories(target.getCalories() + zero(value.getCalories()));
        target.setProtein(target.getProtein() + zero(value.getProtein()));
        target.setCarbs(target.getCarbs() + zero(value.getCarbs()));
        target.setFat(target.getFat() + zero(value.getFat()));
        target.setFiber(sumNullable(target.getFiber(), value.getFiber()));
        target.setSugar(sumNullable(target.getSugar(), value.getSugar()));
        target.setSaturatedFat(sumNullable(target.getSaturatedFat(), value.getSaturatedFat()));
        target.setSodium(sumNullable(target.getSodium(), value.getSodium()));
        target.setPotassium(sumNullable(target.getPotassium(), value.getPotassium()));
        target.setCholesterol(sumNullable(target.getCholesterol(), value.getCholesterol()));
        target.setCalcium(sumNullable(target.getCalcium(), value.getCalcium()));
        target.setIron(sumNullable(target.getIron(), value.getIron()));
        target.setMagnesium(sumNullable(target.getMagnesium(), value.getMagnesium()));
        target.setZinc(sumNullable(target.getZinc(), value.getZinc()));
        target.setVitaminA(sumNullable(target.getVitaminA(), value.getVitaminA()));
        target.setVitaminC(sumNullable(target.getVitaminC(), value.getVitaminC()));
        target.setVitaminD(sumNullable(target.getVitaminD(), value.getVitaminD()));
        target.setVitaminE(sumNullable(target.getVitaminE(), value.getVitaminE()));
        target.setVitaminB12(sumNullable(target.getVitaminB12(), value.getVitaminB12()));
    }

    private Double sumNullable(Double left, Double right) {
        if (left == null && right == null) {
            return null;
        }
        return zero(left) + zero(right);
    }

    private double zero(Double value) {
        return value == null ? 0.0 : value;
    }

    private int count(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private AiMealDraftProviderClient provider() {
        return providerClients.stream()
                .filter(client -> client.provider() == properties.getProvider())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Configured AI provider is not available."));
    }

    private UserEntity user(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private UserEntity userForUpdate(String email) {
        return userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(
                    "AI nutrition-plan payload could not be serialized.");
        }
    }

    private void copyUsage(AiUsageMetadataCarrier response, AiRequestHistoryEntity history) {
        history.setPromptTokens(response.getPromptTokens());
        history.setCompletionTokens(response.getCompletionTokens());
        Integer total = response.getTotalTokens();
        if (total == null && (response.getPromptTokens() != null
                || response.getCompletionTokens() != null)) {
            total = (response.getPromptTokens() == null ? 0 : response.getPromptTokens())
                    + (response.getCompletionTokens() == null ? 0 : response.getCompletionTokens());
        }
        history.setTotalTokens(total);
        history.setEstimatedCost(response.getEstimatedCost());
        history.setCostCurrency(response.getCostCurrency());
    }

    private void refund(UserEntity user, int amount) {
        try {
            subscriptionService.refundConsumedAiQuota(user.getId(), amount);
        } catch (RuntimeException ignored) {
            // Monitoring can reconcile this rare provider-success/persistence-failure path.
        }
    }

    private long elapsed(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}