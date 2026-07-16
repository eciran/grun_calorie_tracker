package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.FoodLogsService;
import com.grun.calorietracker.service.MealPlanService;
import com.grun.calorietracker.service.MealPlanTrackingService;
import com.grun.calorietracker.service.RecipeLogService;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MealPlanTrackingServiceImpl implements MealPlanTrackingService {

    private static final double MAX_CONSUMED_QUANTITY = 100_000.0;
    private static final double QUANTITY_EPSILON = 0.0001;

    private final MealPlanRepository mealPlanRepository;
    private final MealPlanItemRepository mealPlanItemRepository;
    private final MealPlanItemConsumptionRepository consumptionRepository;
    private final FoodLogsRepository foodLogsRepository;
    private final RecipeLogRepository recipeLogRepository;
    private final UserRepository userRepository;
    private final FoodLogsService foodLogsService;
    private final RecipeLogService recipeLogService;
    private final MealPlanService mealPlanService;
    private final UserTimeZoneSupport userTimeZoneSupport;

    @Override
    @Transactional
    public MealPlanDto activate(String email, Long planId) {
        UserEntity user = userForUpdate(email);
        MealPlanEntity target = ownedPlan(user, planId);
        for (MealPlanEntity active : mealPlanRepository.findByUserAndStatus(user, MealPlanStatus.ACTIVE)) {
            if (!active.getId().equals(target.getId())) {
                active.setStatus(MealPlanStatus.DRAFT);
            }
        }
        target.setStatus(MealPlanStatus.ACTIVE);
        mealPlanRepository.save(target);
        return mealPlanService.getMealPlan(email, planId);
    }

    @Override
    @Transactional
    public MealPlanDto deactivate(String email, Long planId) {
        UserEntity user = userForUpdate(email);
        MealPlanEntity plan = ownedPlan(user, planId);
        if (plan.getStatus() != MealPlanStatus.ACTIVE) {
            throw new IllegalArgumentException("Only an active meal plan can be deactivated.");
        }
        plan.setStatus(MealPlanStatus.DRAFT);
        mealPlanRepository.save(plan);
        return mealPlanService.getMealPlan(email, planId);
    }

    @Override
    @Transactional(readOnly = true)
    public MealPlanTodayDto getActiveForDate(String email, LocalDate requestedDate) {
        UserEntity user = user(email);
        LocalDate date = requestedDate == null ? userTimeZoneSupport.today(user) : requestedDate;
        MealPlanTodayDto result = new MealPlanTodayDto();
        result.setDate(date);
        result.setMeals(List.of());

        Optional<MealPlanEntity> active = mealPlanRepository.findActiveForDate(
                user, MealPlanStatus.ACTIVE, date);
        if (active.isEmpty()) {
            return result;
        }

        MealPlanEntity plan = active.get();
        MealPlanDto planDto = mealPlanService.getMealPlan(email, plan.getId());
        Map<Long, MealPlanItemConsumptionDto> latest = latestConsumptions(plan.getId(), user);
        Map<String, List<MealPlanTodayItemDto>> grouped = new LinkedHashMap<>();
        for (MealPlanItemDto item : planDto.getItems()) {
            if (!date.equals(item.getPlanDate())) {
                continue;
            }
            MealPlanTodayItemDto todayItem = new MealPlanTodayItemDto();
            todayItem.setItem(item);
            todayItem.setConsumption(latest.get(item.getId()));
            grouped.computeIfAbsent(item.getMealType(), ignored -> new ArrayList<>()).add(todayItem);
        }

        result.setPlanId(plan.getId());
        result.setPlanName(plan.getName());
        result.setMeals(grouped.entrySet().stream().map(entry -> {
            MealPlanTodayMealDto meal = new MealPlanTodayMealDto();
            meal.setMealType(entry.getKey());
            meal.setItems(entry.getValue());
            return meal;
        }).toList());
        return result;
    }

    @Override
    @Transactional
    public MealPlanItemConsumptionDto logItem(
            String email, Long planId, Long itemId, String idempotencyKey,
            MealPlanItemLogRequestDto request) {
        validateLogRequest(request);
        UserEntity user = userForUpdate(email);
        String key = normalizeKey(idempotencyKey);
        MealPlanItemConsumptionDto replay = replay(user, itemId, key);
        if (replay != null) {
            return replay;
        }
        MealPlanItemEntity item = actionableItem(user, planId, itemId);
        ensureNotConsumed(item, user, key);
        validateLogDate(item, request.getLoggedAt().toLocalDate(), user);

        PlannedAmount planned = plannedAmount(item);
        double consumed = request.getConsumedQuantity();
        FoodPortionUnit unit = request.getConsumedUnit();
        MealPlanItemConsumptionEntity consumption = baseConsumption(item, user, key, planned);

        if (item.getItemType() == MealPlanItemType.AI_SNAPSHOT) {
            requireSameSnapshotUnit(planned, unit);
            FoodLogsDto saved = foodLogsService.addAiEstimateFoodLog(
                    snapshotLogRequest(item, consumed, unit, request.getLoggedAt(), planned), email);
            consumption.setFoodLog(ownedFoodLog(user, saved.getId()));
            applyNutrition(consumption, nutrition(saved));
        } else if (item.getItemType() == MealPlanItemType.FOOD_ITEM) {
            validateCatalogUnit(item.getFoodItem(), unit);
            FoodLogsDto log = new FoodLogsDto();
            log.setFoodItemId(item.getFoodItem().getId());
            log.setPortionSize(consumed);
            log.setPortionUnit(unit);
            log.setMealType(item.getMealType());
            log.setLogDate(request.getLoggedAt());
            log.setSource(FoodLogSource.MEAL_PLAN);
            FoodLogsDto saved = foodLogsService.addFoodLog(log, email);
            consumption.setFoodLog(ownedFoodLog(user, saved.getId()));
            applyNutrition(consumption, nutrition(saved));
        } else if (item.getItemType() == MealPlanItemType.RECIPE) {
            RecipeLogRequestDto log = new RecipeLogRequestDto();
            if (unit == FoodPortionUnit.SERVING) {
                log.setServingCount(consumed);
            } else if (unit == FoodPortionUnit.GRAM) {
                log.setServingGrams(consumed);
            } else {
                throw new IllegalArgumentException(
                        "Recipe plan items can be logged only in SERVING or GRAM units.");
            }
            log.setMealType(item.getMealType());
            log.setLogDate(request.getLoggedAt());
            RecipeLogDto saved = recipeLogService.logRecipe(email, item.getRecipe().getId(), log);
            consumption.setRecipeLog(ownedRecipeLog(user, saved.getId()));
            applyNutrition(consumption, nutrition(saved));
        } else {
            throw new IllegalArgumentException("Unsupported meal-plan item type.");
        }

        consumption.setConsumedQuantity(consumed);
        consumption.setConsumedUnit(unit);
        consumption.setStatus(consumed + QUANTITY_EPSILON < comparablePlannedQuantity(planned, unit)
                ? MealPlanItemConsumptionStatus.PARTIALLY_CONSUMED
                : MealPlanItemConsumptionStatus.LOGGED);
        return toDto(consumptionRepository.save(consumption));
    }

    @Override
    @Transactional
    public MealPlanItemConsumptionDto skipItem(
            String email, Long planId, Long itemId, String idempotencyKey) {
        UserEntity user = userForUpdate(email);
        String key = normalizeKey(idempotencyKey);
        MealPlanItemConsumptionDto replay = replay(user, itemId, key);
        if (replay != null) {
            return replay;
        }
        MealPlanItemEntity item = actionableItem(user, planId, itemId);
        ensureNotConsumed(item, user, key);
        ensureItemDateNotFuture(item, user);
        MealPlanItemConsumptionEntity consumption = baseConsumption(
                item, user, key, plannedAmount(item));
        consumption.setStatus(MealPlanItemConsumptionStatus.SKIPPED);
        return toDto(consumptionRepository.save(consumption));
    }

    @Override
    @Transactional
    public MealPlanItemConsumptionDto replaceItem(
            String email, Long planId, Long itemId, String idempotencyKey,
            MealPlanItemReplaceRequestDto request) {
        validateReplacement(request);
        UserEntity user = userForUpdate(email);
        String key = normalizeKey(idempotencyKey);
        MealPlanItemConsumptionDto replay = replay(user, itemId, key);
        if (replay != null) {
            return replay;
        }
        MealPlanItemEntity item = actionableItem(user, planId, itemId);
        ensureNotConsumed(item, user, key);
        ensureItemDateNotFuture(item, user);
        MealPlanItemConsumptionEntity consumption = baseConsumption(
                item, user, key, plannedAmount(item));

        if (request.getFoodLogId() != null) {
            FoodLogsEntity log = ownedFoodLog(user, request.getFoodLogId());
            validateReplacementDate(item, log.getLogDate().toLocalDate());
            consumption.setFoodLog(log);
            consumption.setConsumedQuantity(log.getPortionSize());
            consumption.setConsumedUnit(log.getPortionUnit());
            applyNutrition(consumption, nutrition(log));
        } else {
            RecipeLogEntity log = ownedRecipeLog(user, request.getRecipeLogId());
            validateReplacementDate(item, log.getLogDate().toLocalDate());
            consumption.setRecipeLog(log);
            if (log.getServingCount() != null && log.getServingCount() > 0) {
                consumption.setConsumedQuantity(log.getServingCount());
                consumption.setConsumedUnit(FoodPortionUnit.SERVING);
            } else {
                consumption.setConsumedQuantity(log.getServingGrams());
                consumption.setConsumedUnit(FoodPortionUnit.GRAM);
            }
            applyNutrition(consumption, nutrition(log));
        }
        consumption.setStatus(MealPlanItemConsumptionStatus.REPLACED);
        return toDto(consumptionRepository.save(consumption));
    }

    private MealPlanItemEntity actionableItem(UserEntity user, Long planId, Long itemId) {
        MealPlanItemEntity item = mealPlanItemRepository.findOwnedForUpdate(itemId, planId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Meal-plan item not found"));
        if (item.getMealPlan().getStatus() != MealPlanStatus.ACTIVE) {
            throw new IllegalArgumentException("Meal-plan items can be tracked only while the plan is active.");
        }
        return item;
    }

    private MealPlanItemConsumptionDto replay(UserEntity user, Long itemId, String key) {
        return consumptionRepository.findByUserAndIdempotencyKey(user, key)
                .map(existing -> {
                    if (!existing.getMealPlanItem().getId().equals(itemId)) {
                        throw new RequestConflictException(
                                "Idempotency-Key was already used for a different meal-plan item.");
                    }
                    return toDto(existing);
                }).orElse(null);
    }

    private void ensureNotConsumed(MealPlanItemEntity item, UserEntity user, String key) {
        consumptionRepository.findByMealPlanItemAndUser(item, user).ifPresent(existing -> {
            if (existing.getIdempotencyKey().equals(key)) {
                return;
            }
            throw new RequestConflictException(
                    "This meal-plan item already has a consumption decision.");
        });
    }

    private MealPlanItemConsumptionEntity baseConsumption(
            MealPlanItemEntity item, UserEntity user, String key, PlannedAmount planned) {
        MealPlanItemConsumptionEntity entity = new MealPlanItemConsumptionEntity();
        entity.setMealPlanItem(item);
        entity.setUser(user);
        entity.setIdempotencyKey(key);
        entity.setPlannedQuantity(planned.quantity());
        entity.setPlannedUnit(planned.unit());
        return entity;
    }

    private PlannedAmount plannedAmount(MealPlanItemEntity item) {
        if (item.getItemType() == MealPlanItemType.RECIPE) {
            return new PlannedAmount(
                    item.getServingCount() == null ? 1.0 : item.getServingCount(),
                    FoodPortionUnit.SERVING);
        }
        if (item.getPortionSize() == null || item.getPortionUnit() == null) {
            throw new IllegalArgumentException("Meal-plan item has no usable planned amount.");
        }
        return new PlannedAmount(item.getPortionSize(), item.getPortionUnit());
    }

    private FoodLogsDto snapshotLogRequest(
            MealPlanItemEntity item, double consumed, FoodPortionUnit unit,
            java.time.LocalDateTime loggedAt, PlannedAmount planned) {
        double factor = consumed / planned.quantity();
        FoodLogsDto log = new FoodLogsDto();
        log.setDisplayName(item.getSnapshotName());
        log.setEstimated(true);
        log.setAiRequestId(item.getSourceAiRequest() == null ? null : item.getSourceAiRequest().getId());
        log.setPortionSize(consumed);
        log.setPortionUnit(unit);
        log.setNormalizedPortionGrams(unit == FoodPortionUnit.GRAM || unit == FoodPortionUnit.MILLILITER
                ? consumed : null);
        log.setMealType(item.getMealType());
        log.setLogDate(loggedAt);
        log.setSource(FoodLogSource.MEAL_PLAN);
        log.setSnapshotCalories(scale(item.getSnapshotCalories(), factor));
        log.setSnapshotProtein(scale(item.getSnapshotProtein(), factor));
        log.setSnapshotCarbs(scale(item.getSnapshotCarbs(), factor));
        log.setSnapshotFat(scale(item.getSnapshotFat(), factor));
        log.setSnapshotFiber(scaleNullable(item.getSnapshotFiber(), factor));
        log.setSnapshotSugar(scaleNullable(item.getSnapshotSugar(), factor));
        log.setSnapshotSaturatedFat(scaleNullable(item.getSnapshotSaturatedFat(), factor));
        log.setSnapshotSodium(scaleNullable(item.getSnapshotSodium(), factor));
        log.setSnapshotPotassium(scaleNullable(item.getSnapshotPotassium(), factor));
        log.setSnapshotCholesterol(scaleNullable(item.getSnapshotCholesterol(), factor));
        log.setSnapshotCalcium(scaleNullable(item.getSnapshotCalcium(), factor));
        log.setSnapshotIron(scaleNullable(item.getSnapshotIron(), factor));
        log.setSnapshotMagnesium(scaleNullable(item.getSnapshotMagnesium(), factor));
        log.setSnapshotZinc(scaleNullable(item.getSnapshotZinc(), factor));
        log.setSnapshotVitaminA(scaleNullable(item.getSnapshotVitaminA(), factor));
        log.setSnapshotVitaminC(scaleNullable(item.getSnapshotVitaminC(), factor));
        log.setSnapshotVitaminD(scaleNullable(item.getSnapshotVitaminD(), factor));
        log.setSnapshotVitaminE(scaleNullable(item.getSnapshotVitaminE(), factor));
        log.setSnapshotVitaminB12(scaleNullable(item.getSnapshotVitaminB12(), factor));
        return log;
    }

    private void validateCatalogUnit(FoodItemEntity food, FoodPortionUnit unit) {
        if (List.of(FoodPortionUnit.SERVING, FoodPortionUnit.PIECE, FoodPortionUnit.SLICE).contains(unit)
                && (food.getServingSizeGrams() == null || food.getServingSizeGrams() <= 0)) {
            throw new IllegalArgumentException(
                    "This product has no verified conversion for the selected portion unit.");
        }
    }

    private void requireSameSnapshotUnit(PlannedAmount planned, FoodPortionUnit consumedUnit) {
        if (planned.unit() != consumedUnit) {
            throw new IllegalArgumentException(
                    "Snapshot plan items require the consumed amount in the planned unit.");
        }
    }

    private double comparablePlannedQuantity(PlannedAmount planned, FoodPortionUnit consumedUnit) {
        return planned.unit() == consumedUnit ? planned.quantity() : 0.0;
    }

    private void validateLogRequest(MealPlanItemLogRequestDto request) {
        if (request == null || request.getConsumedQuantity() == null
                || request.getConsumedUnit() == null || request.getLoggedAt() == null) {
            throw new IllegalArgumentException("Consumed quantity, unit, and log time are required.");
        }
        if (!Double.isFinite(request.getConsumedQuantity())
                || request.getConsumedQuantity() <= 0
                || request.getConsumedQuantity() > MAX_CONSUMED_QUANTITY) {
            throw new IllegalArgumentException("Consumed quantity is outside the allowed range.");
        }
    }

    private void validateLogDate(MealPlanItemEntity item, LocalDate logDate, UserEntity user) {
        validateReplacementDate(item, logDate);
        if (logDate.isAfter(userTimeZoneSupport.today(user))) {
            throw new IllegalArgumentException("A future meal-plan item cannot be logged.");
        }
    }

    private void validateReplacementDate(MealPlanItemEntity item, LocalDate logDate) {
        if (!item.getPlanDate().equals(logDate)) {
            throw new IllegalArgumentException(
                    "Diary record date must match the planned item date.");
        }
    }

    private void ensureItemDateNotFuture(MealPlanItemEntity item, UserEntity user) {
        if (item.getPlanDate().isAfter(userTimeZoneSupport.today(user))) {
            throw new IllegalArgumentException("A future meal-plan item cannot be changed.");
        }
    }

    private void validateReplacement(MealPlanItemReplaceRequestDto request) {
        if (request == null || (request.getFoodLogId() == null) == (request.getRecipeLogId() == null)) {
            throw new IllegalArgumentException(
                    "Provide exactly one replacement foodLogId or recipeLogId.");
        }
    }

    private String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required.");
        }
        String key = value.trim();
        if (key.length() < 8 || key.length() > 120 || !key.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must contain 8-120 safe characters.");
        }
        return key;
    }

    private Map<Long, MealPlanItemConsumptionDto> latestConsumptions(Long planId, UserEntity user) {
        Map<Long, MealPlanItemConsumptionDto> result = new LinkedHashMap<>();
        for (MealPlanItemConsumptionEntity entity : consumptionRepository
                .findByMealPlanItemMealPlanIdAndUserOrderByCreatedAtDesc(planId, user)) {
            result.putIfAbsent(entity.getMealPlanItem().getId(), toDto(entity));
        }
        return result;
    }

    private MealPlanItemConsumptionDto toDto(MealPlanItemConsumptionEntity entity) {
        MealPlanItemConsumptionDto dto = new MealPlanItemConsumptionDto();
        dto.setId(entity.getId());
        dto.setMealPlanId(entity.getMealPlanItem().getMealPlan().getId());
        dto.setMealPlanItemId(entity.getMealPlanItem().getId());
        dto.setStatus(entity.getStatus());
        dto.setPlannedQuantity(entity.getPlannedQuantity());
        dto.setPlannedUnit(entity.getPlannedUnit());
        dto.setConsumedQuantity(entity.getConsumedQuantity());
        dto.setConsumedUnit(entity.getConsumedUnit());
        if (entity.getConsumedQuantity() != null && entity.getConsumedUnit() == entity.getPlannedUnit()) {
            double variance = round(entity.getConsumedQuantity() - entity.getPlannedQuantity());
            dto.setQuantityVariance(variance);
            dto.setQuantityVariancePercent(entity.getPlannedQuantity() <= 0 ? null
                    : round(variance * 100.0 / entity.getPlannedQuantity()));
        }
        dto.setConsumedNutrition(nutrition(entity));
        dto.setFoodLogId(entity.getFoodLog() == null ? null : entity.getFoodLog().getId());
        dto.setRecipeLogId(entity.getRecipeLog() == null ? null : entity.getRecipeLog().getId());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private MealPlanNutritionSnapshotDto nutrition(FoodLogsDto value) {
        return new MealPlanNutritionSnapshotDto(
                value.getSnapshotCalories(), value.getSnapshotProtein(), value.getSnapshotCarbs(), value.getSnapshotFat(),
                value.getSnapshotFiber(), value.getSnapshotSugar(), value.getSnapshotSaturatedFat(), value.getSnapshotSodium(),
                value.getSnapshotPotassium(), value.getSnapshotCholesterol(), value.getSnapshotCalcium(), value.getSnapshotIron(),
                value.getSnapshotMagnesium(), value.getSnapshotZinc(), value.getSnapshotVitaminA(), value.getSnapshotVitaminC(),
                value.getSnapshotVitaminD(), value.getSnapshotVitaminE(), value.getSnapshotVitaminB12());
    }

    private MealPlanNutritionSnapshotDto nutrition(RecipeLogDto value) {
        return new MealPlanNutritionSnapshotDto(
                value.getSnapshotCalories(), value.getSnapshotProtein(), value.getSnapshotCarbs(), value.getSnapshotFat(),
                value.getSnapshotFiber(), value.getSnapshotSugar(), value.getSnapshotSaturatedFat(), value.getSnapshotSodium(),
                value.getSnapshotPotassium(), value.getSnapshotCholesterol(), value.getSnapshotCalcium(), value.getSnapshotIron(),
                value.getSnapshotMagnesium(), value.getSnapshotZinc(), value.getSnapshotVitaminA(), value.getSnapshotVitaminC(),
                value.getSnapshotVitaminD(), value.getSnapshotVitaminE(), value.getSnapshotVitaminB12());
    }

    private MealPlanNutritionSnapshotDto nutrition(FoodLogsEntity value) {
        return new MealPlanNutritionSnapshotDto(
                value.getSnapshotCalories(), value.getSnapshotProtein(), value.getSnapshotCarbs(), value.getSnapshotFat(),
                value.getSnapshotFiber(), value.getSnapshotSugar(), value.getSnapshotSaturatedFat(), value.getSnapshotSodium(),
                value.getSnapshotPotassium(), value.getSnapshotCholesterol(), value.getSnapshotCalcium(), value.getSnapshotIron(),
                value.getSnapshotMagnesium(), value.getSnapshotZinc(), value.getSnapshotVitaminA(), value.getSnapshotVitaminC(),
                value.getSnapshotVitaminD(), value.getSnapshotVitaminE(), value.getSnapshotVitaminB12());
    }

    private MealPlanNutritionSnapshotDto nutrition(RecipeLogEntity value) {
        return new MealPlanNutritionSnapshotDto(
                value.getSnapshotCalories(), value.getSnapshotProtein(), value.getSnapshotCarbs(), value.getSnapshotFat(),
                value.getSnapshotFiber(), value.getSnapshotSugar(), value.getSnapshotSaturatedFat(), value.getSnapshotSodium(),
                value.getSnapshotPotassium(), value.getSnapshotCholesterol(), value.getSnapshotCalcium(), value.getSnapshotIron(),
                value.getSnapshotMagnesium(), value.getSnapshotZinc(), value.getSnapshotVitaminA(), value.getSnapshotVitaminC(),
                value.getSnapshotVitaminD(), value.getSnapshotVitaminE(), value.getSnapshotVitaminB12());
    }

    private MealPlanNutritionSnapshotDto nutrition(MealPlanItemConsumptionEntity value) {
        if (value.getSnapshotCalories() == null) {
            return null;
        }
        return new MealPlanNutritionSnapshotDto(
                value.getSnapshotCalories(), value.getSnapshotProtein(), value.getSnapshotCarbs(), value.getSnapshotFat(),
                value.getSnapshotFiber(), value.getSnapshotSugar(), value.getSnapshotSaturatedFat(), value.getSnapshotSodium(),
                value.getSnapshotPotassium(), value.getSnapshotCholesterol(), value.getSnapshotCalcium(), value.getSnapshotIron(),
                value.getSnapshotMagnesium(), value.getSnapshotZinc(), value.getSnapshotVitaminA(), value.getSnapshotVitaminC(),
                value.getSnapshotVitaminD(), value.getSnapshotVitaminE(), value.getSnapshotVitaminB12());
    }

    private void applyNutrition(MealPlanItemConsumptionEntity target, MealPlanNutritionSnapshotDto value) {
        target.setSnapshotCalories(value.getCalories());
        target.setSnapshotProtein(value.getProtein());
        target.setSnapshotCarbs(value.getCarbs());
        target.setSnapshotFat(value.getFat());
        target.setSnapshotFiber(value.getFiber());
        target.setSnapshotSugar(value.getSugar());
        target.setSnapshotSaturatedFat(value.getSaturatedFat());
        target.setSnapshotSodium(value.getSodium());
        target.setSnapshotPotassium(value.getPotassium());
        target.setSnapshotCholesterol(value.getCholesterol());
        target.setSnapshotCalcium(value.getCalcium());
        target.setSnapshotIron(value.getIron());
        target.setSnapshotMagnesium(value.getMagnesium());
        target.setSnapshotZinc(value.getZinc());
        target.setSnapshotVitaminA(value.getVitaminA());
        target.setSnapshotVitaminC(value.getVitaminC());
        target.setSnapshotVitaminD(value.getVitaminD());
        target.setSnapshotVitaminE(value.getVitaminE());
        target.setSnapshotVitaminB12(value.getVitaminB12());
    }

    private FoodLogsEntity ownedFoodLog(UserEntity user, Long id) {
        return foodLogsRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Food log not found"));
    }

    private RecipeLogEntity ownedRecipeLog(UserEntity user, Long id) {
        return recipeLogRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe log not found"));
    }

    private MealPlanEntity ownedPlan(UserEntity user, Long id) {
        return mealPlanRepository.findByIdAndUser(id, user)
                .filter(plan -> plan.getStatus() != MealPlanStatus.ARCHIVED)
                .orElseThrow(() -> new ResourceNotFoundException("Meal plan not found"));
    }

    private UserEntity user(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private UserEntity userForUpdate(String email) {
        return userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Double scale(Double value, double factor) {
        return round((value == null ? 0.0 : value) * factor);
    }

    private Double scaleNullable(Double value, double factor) {
        return value == null ? null : round(value * factor);
    }

    private Double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record PlannedAmount(double quantity, FoodPortionUnit unit) { }
}
