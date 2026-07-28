package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodLogDailyStatsDto;
import com.grun.calorietracker.dto.FoodLogCopyMealRequestDto;
import com.grun.calorietracker.dto.FoodLogMealSummaryDto;
import com.grun.calorietracker.dto.FoodLogRecentMealDto;
import com.grun.calorietracker.dto.FoodLogRecentPortionDto;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.dto.QuickCalorieLogRequestDto;
import com.grun.calorietracker.dto.RecipeLogDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import com.grun.calorietracker.entity.FoodLogsEntity;
import com.grun.calorietracker.entity.RecipeLogEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AnalyticsMutationSource;
import com.grun.calorietracker.enums.FoodLogSource;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.RecipeLogRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.FoodLogsService;
import com.grun.calorietracker.service.UserAnalyticsCacheRevisionService;
import com.grun.calorietracker.service.support.FoodPortionCalculator;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import com.grun.calorietracker.service.support.FoodProductQualityRules;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FoodLogsServiceImpl implements FoodLogsService {

    private final FoodLogsRepository foodLogsRepository;
    private final FoodItemRepository foodItemRepository;
    private final RecipeLogRepository recipeLogRepository;
    private final FoodItemServingOptionRepository foodItemServingOptionRepository;
    private final UserRepository userRepository;
    private final UserAnalyticsCacheRevisionService analyticsCacheRevisionService;

    @Override
    @Transactional
    public FoodLogsDto addFoodLog(FoodLogsDto dto, String email) {
        validateFoodLogRequest(dto);
        UserEntity user = getUser(email);
        FoodItemEntity foodItem = foodItemRepository.findById(dto.getFoodItemId())
                .orElseThrow(() -> new ProductNotFoundException("Food item not found"));
        ensureFoodItemAvailableToUser(foodItem, user);
        FoodLogsEntity entity = new FoodLogsEntity();
        entity.setUser(user);
        entity.setFoodItem(foodItem);
        entity.setServingOption(resolveServingOption(dto.getServingOptionId(), foodItem));
        entity.setPortionSize(dto.getPortionSize());
        entity.setPortionUnit(FoodPortionCalculator.resolveUnit(dto.getPortionUnit()));
        entity.setNormalizedPortionGrams(FoodPortionCalculator.normalizeToGrams(
                dto.getPortionSize(),
                entity.getPortionUnit(),
                foodItem,
                entity.getServingOption()
        ));
        applyNutritionSnapshot(entity, foodItem);
        entity.setMealType(normalizeMealType(dto.getMealType()));
        entity.setLogDate(dto.getLogDate());
        entity.setSource(resolveSource(dto.getSource(), FoodLogSource.MANUAL));
        entity.setAiRequestId(dto.getAiRequestId());
        entity.setAiConfidence(dto.getAiConfidence());

        FoodLogsEntity saved = foodLogsRepository.save(entity);
        markFoodItemUsed(foodItem);
        analyticsCacheRevisionService.bump(user.getId(), AnalyticsMutationSource.FOOD_LOG);
        return toDto(saved);
    }

    @Override
    @Transactional
    public FoodLogsDto addAiEstimateFoodLog(FoodLogsDto dto, String email) {
        validateAiEstimateFoodLogRequest(dto);
        UserEntity user = getUser(email);
        FoodLogsEntity entity = new FoodLogsEntity();
        entity.setUser(user);
        entity.setFoodItem(null);
        entity.setDisplayName(normalizeDisplayName(dto.getDisplayName(), dto.getFoodName()));
        entity.setEstimated(true);
        entity.setAiRequestId(dto.getAiRequestId());
        entity.setAiConfidence(dto.getAiConfidence());
        entity.setPortionSize(dto.getPortionSize());
        entity.setPortionUnit(FoodPortionCalculator.resolveUnit(dto.getPortionUnit()));
        entity.setNormalizedPortionGrams(dto.getNormalizedPortionGrams() != null ? dto.getNormalizedPortionGrams() : dto.getPortionSize());
        entity.setSnapshotCalories(round(dto.getSnapshotCalories()));
        entity.setSnapshotProtein(roundOrZero(dto.getSnapshotProtein()));
        entity.setSnapshotCarbs(roundOrZero(dto.getSnapshotCarbs()));
        entity.setSnapshotFat(roundOrZero(dto.getSnapshotFat()));
        entity.setSnapshotFiber(dto.getSnapshotFiber());
        entity.setSnapshotSugar(dto.getSnapshotSugar());
        entity.setSnapshotSaturatedFat(dto.getSnapshotSaturatedFat());
        entity.setSnapshotSodium(dto.getSnapshotSodium());
        entity.setSnapshotPotassium(dto.getSnapshotPotassium());
        entity.setSnapshotCholesterol(dto.getSnapshotCholesterol());
        entity.setSnapshotCalcium(dto.getSnapshotCalcium());
        entity.setSnapshotIron(dto.getSnapshotIron());
        entity.setSnapshotMagnesium(dto.getSnapshotMagnesium());
        entity.setSnapshotZinc(dto.getSnapshotZinc());
        entity.setSnapshotVitaminA(dto.getSnapshotVitaminA());
        entity.setSnapshotVitaminC(dto.getSnapshotVitaminC());
        entity.setSnapshotVitaminD(dto.getSnapshotVitaminD());
        entity.setSnapshotVitaminE(dto.getSnapshotVitaminE());
        entity.setSnapshotVitaminB12(dto.getSnapshotVitaminB12());
        entity.setMealType(normalizeMealType(dto.getMealType()));
        entity.setLogDate(dto.getLogDate());
        entity.setSource(resolveSource(dto.getSource(), FoodLogSource.AI_ESTIMATE));
        FoodLogsEntity saved = foodLogsRepository.save(entity);
        analyticsCacheRevisionService.bump(user.getId(), AnalyticsMutationSource.FOOD_LOG);
        return toDto(saved);
    }
    @Override
    @Transactional
    public List<FoodLogsDto> copyMeal(String email, FoodLogCopyMealRequestDto request) {
        UserEntity user = getUser(email);
        String mealType = normalizeMealType(request.getMealType());
        List<FoodLogsEntity> sourceLogs = foodLogsRepository.findByUserAndMealTypeAndLogDateBetween(
                user,
                mealType,
                request.getSourceDate().atStartOfDay(),
                request.getSourceDate().plusDays(1).atStartOfDay()
        );

        List<FoodLogsDto> copied = sourceLogs.stream()
                .map(source -> copyLogToDate(source, request.getTargetDate(), user))
                .map(foodLogsRepository::save)
                .peek(saved -> { if (saved.getFoodItem() != null) { markFoodItemUsed(saved.getFoodItem()); } })
                .map(this::toDto)
                .toList();
        if (!copied.isEmpty()) {
            analyticsCacheRevisionService.bump(user.getId(), AnalyticsMutationSource.FOOD_LOG);
        }
        return copied;
    }

    @Override
    @Transactional
    public FoodLogsDto updateFoodLog(Long id, FoodLogsDto dto, String email) {
        UserEntity user = getUser(email);
        FoodLogsEntity entity = foodLogsRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Food log not found"));
        validateFoodLogUpdateRequest(dto, entity);
        FoodItemEntity foodItem = resolveFoodItemForUpdate(dto, entity);
        if (foodItem != null) {
            ensureFoodItemAvailableToUser(foodItem, user);
            entity.setFoodItem(foodItem);
            entity.setServingOption(resolveServingOption(dto.getServingOptionId(), foodItem));
            entity.setPortionSize(dto.getPortionSize());
            entity.setPortionUnit(FoodPortionCalculator.resolveUnit(dto.getPortionUnit()));
            entity.setNormalizedPortionGrams(FoodPortionCalculator.normalizeToGrams(
                    dto.getPortionSize(),
                    entity.getPortionUnit(),
                    foodItem,
                    entity.getServingOption()
            ));
            applyNutritionSnapshot(entity, foodItem);
        } else {
            updateSnapshotOnlyPortion(entity, dto);
        }
        entity.setMealType(normalizeMealType(dto.getMealType()));
        entity.setLogDate(dto.getLogDate());
        entity.setSource(resolveSource(dto.getSource(), entity.getSource()));

        FoodLogsEntity saved = foodLogsRepository.save(entity);
        analyticsCacheRevisionService.bump(user.getId(), AnalyticsMutationSource.FOOD_LOG);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodLogsDto> getFoodLogs(String email, String date, int page, int size) {
        List<FoodLogsEntity> logs;
        UserEntity user = getUser(email);
        if (date != null) {
            LocalDate targetDate = LocalDate.parse(date);
            logs = foodLogsRepository.findByUserAndLogDateBetween(
                    user,
                    targetDate.atStartOfDay(),
                    targetDate.plusDays(1).atStartOfDay()
            );
        } else {
            logs = foodLogsRepository.findByUserOrderByLogDateDesc(
                    user,
                    PageRequest.of(normalizePage(page), normalizeHistoryPageSize(size))
            ).getContent();
        }
        return logs.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodLogsDto> getFoodLogsHistory(String email, LocalDateTime start, LocalDateTime end) {
        UserEntity user = getUser(email);
        return foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                        user,
                        start,
                        end
                ).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodLogMealSummaryDto> getMealSummaries(String email, LocalDateTime start, LocalDateTime end) {
        UserEntity user = getUser(email);
        List<FoodLogsEntity> logs = foodLogsRepository
                .findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(user, start, end);
        Map<String, List<FoodLogsEntity>> logsByMeal = logs.stream()
                .collect(Collectors.groupingBy(
                        log -> normalizeMealType(log.getMealType()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<String, List<RecipeLogEntity>> recipeLogsByMeal = recipeLogRepository
                .findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(user, start, end)
                .stream()
                .collect(Collectors.groupingBy(
                        log -> normalizeMealType(log.getMealType()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        return List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").stream()
                .map(mealType -> toMealSummary(
                        mealType,
                        logsByMeal.getOrDefault(mealType, List.of()),
                        recipeLogsByMeal.getOrDefault(mealType, List.of())
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodLogRecentMealDto> getRecentMeals(String email, int limit) {
        UserEntity user = getUser(email);
        return foodLogsRepository.findRecentMealKeys(
                        user.getId(),
                        VerificationStatus.REJECTED.name(),
                        PageRequest.of(0, normalizeLimit(limit))
                ).stream()
                .map(key -> toRecentMeal(user, toLocalDate(key[0]), normalizeMealType((String) key[1])))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodLogRecentPortionDto> getRecentPortions(String email, Long foodItemId, int limit) {
        UserEntity user = getUser(email);
        FoodItemEntity foodItem = foodItemRepository.findById(foodItemId)
                .orElseThrow(() -> new ProductNotFoundException("Food item not found"));
        ensureFoodItemAvailableToUser(foodItem, user);
        return foodLogsRepository.findRecentPortionsByUserAndFoodItem(
                        user.getId(),
                        foodItem.getId(),
                        PageRequest.of(0, normalizeLimit(limit))
                ).stream()
                .map(this::toRecentPortionDto)
                .toList();
    }

    @Override
    @Transactional
    public FoodLogsDto quickAddCalories(String email, QuickCalorieLogRequestDto request) {
        UserEntity user = getUser(email);

        FoodLogsEntity entity = new FoodLogsEntity();
        entity.setUser(user);
        entity.setFoodItem(null);
        entity.setDisplayName("Quick calories");
        entity.setEstimated(false);
        entity.setPortionSize(request.getCalories());
        entity.setPortionUnit(FoodPortionUnit.GRAM);
        entity.setNormalizedPortionGrams(request.getCalories());
        entity.setSnapshotCalories(round(request.getCalories()));
        entity.setSnapshotProtein(0.0);
        entity.setSnapshotCarbs(0.0);
        entity.setSnapshotFat(0.0);
        entity.setMealType(normalizeMealType(request.getMealType()));
        entity.setLogDate(request.getLogDate());
        entity.setSource(FoodLogSource.QUICK_ADD);

        FoodLogsEntity saved = foodLogsRepository.save(entity);
        analyticsCacheRevisionService.bump(user.getId(), AnalyticsMutationSource.FOOD_LOG);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public FoodLogsDto getFoodLogById(Long id, String email) {
        UserEntity user = getUser(email);
        FoodLogsEntity entity = foodLogsRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Food log not found"));
        return toDto(entity);
    }

    @Override
    @Transactional
    public void deleteFoodLog(Long id, String email) {
        UserEntity user = getUser(email);
        FoodLogsEntity entity = foodLogsRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Food log not found"));
        foodLogsRepository.delete(entity);
        analyticsCacheRevisionService.bump(user.getId(), AnalyticsMutationSource.FOOD_LOG);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodLogDailyStatsDto> getDailyStats(String email, LocalDateTime start, LocalDateTime end) {
        UserEntity user = getUser(email);
        Map<String, FoodLogDailyStatsDto> statsByDate = new LinkedHashMap<>();
        foodLogsRepository.getDailyStatsByUserAndDateBetween(user.getId(), start, end)
                .stream()
                .map(this::toDailyStatsDto)
                .forEach(dto -> statsByDate.put(dto.getDate(), dto));
        recipeLogRepository.getDailyStatsByUserAndDateBetween(user.getId(), start, end)
                .stream()
                .map(this::toDailyStatsDto)
                .forEach(dto -> mergeDailyStats(statsByDate, dto));
        return statsByDate.values().stream().toList();
    }

    private FoodLogDailyStatsDto toDailyStatsDto(Object[] row) {
        FoodLogDailyStatsDto dto = new FoodLogDailyStatsDto();
        dto.setDate(formatDate(row[0]));
        dto.setTotalCalories(toDouble(row[1]));
        dto.setTotalProtein(toDouble(row[2]));
        dto.setTotalCarbs(toDouble(row[3]));
        dto.setTotalFat(toDouble(row[4]));
        dto.setTotalFiber(toNullableDouble(row, 5));
        dto.setTotalSugar(toNullableDouble(row, 6));
        dto.setTotalSaturatedFat(toNullableDouble(row, 7));
        dto.setTotalSodium(toNullableDouble(row, 8));
        dto.setTotalPotassium(toNullableDouble(row, 9));
        dto.setTotalCholesterol(toNullableDouble(row, 10));
        dto.setTotalCalcium(toNullableDouble(row, 11));
        dto.setTotalIron(toNullableDouble(row, 12));
        dto.setTotalMagnesium(toNullableDouble(row, 13));
        dto.setTotalZinc(toNullableDouble(row, 14));
        dto.setTotalVitaminA(toNullableDouble(row, 15));
        dto.setTotalVitaminC(toNullableDouble(row, 16));
        dto.setTotalVitaminD(toNullableDouble(row, 17));
        dto.setTotalVitaminE(toNullableDouble(row, 18));
        dto.setTotalVitaminB12(toNullableDouble(row, 19));
        return dto;
    }

    private FoodLogMealSummaryDto toMealSummary(String mealType, List<FoodLogsEntity> logs, List<RecipeLogEntity> recipeLogs) {
        FoodLogMealSummaryDto dto = new FoodLogMealSummaryDto();
        dto.setMealType(mealType);
        List<FoodLogsDto> foodLogDtos = logs.stream().map(this::toDto).toList();
        dto.setLogs(foodLogDtos);
        dto.setFoodLogs(foodLogDtos);
        dto.setRecipeLogs(recipeLogs.stream().map(this::toRecipeLogDto).toList());
        dto.setTotalCalories(round(sumNutrition(logs, FoodLogsEntity::getSnapshotCalories, FoodItemEntity::getCalories)
                + sumRecipeNutrition(recipeLogs, RecipeLogEntity::getSnapshotCalories)));
        dto.setTotalProtein(round(sumNutrition(logs, FoodLogsEntity::getSnapshotProtein, FoodItemEntity::getProtein)
                + sumRecipeNutrition(recipeLogs, RecipeLogEntity::getSnapshotProtein)));
        dto.setTotalFat(round(sumNutrition(logs, FoodLogsEntity::getSnapshotFat, FoodItemEntity::getFat)
                + sumRecipeNutrition(recipeLogs, RecipeLogEntity::getSnapshotFat)));
        dto.setTotalCarbs(round(sumNutrition(logs, FoodLogsEntity::getSnapshotCarbs, FoodItemEntity::getCarbs)
                + sumRecipeNutrition(recipeLogs, RecipeLogEntity::getSnapshotCarbs)));
        return dto;
    }

    private void mergeDailyStats(Map<String, FoodLogDailyStatsDto> statsByDate, FoodLogDailyStatsDto addition) {
        FoodLogDailyStatsDto existing = statsByDate.computeIfAbsent(addition.getDate(), ignored -> {
            FoodLogDailyStatsDto dto = new FoodLogDailyStatsDto();
            dto.setDate(addition.getDate());
            dto.setTotalCalories(0.0);
            dto.setTotalProtein(0.0);
            dto.setTotalCarbs(0.0);
            dto.setTotalFat(0.0);
            return dto;
        });
        existing.setTotalCalories(round(existing.getTotalCalories() + addition.getTotalCalories()));
        existing.setTotalProtein(round(existing.getTotalProtein() + addition.getTotalProtein()));
        existing.setTotalCarbs(round(existing.getTotalCarbs() + addition.getTotalCarbs()));
        existing.setTotalFat(round(existing.getTotalFat() + addition.getTotalFat()));
        existing.setTotalFiber(addNullable(existing.getTotalFiber(), addition.getTotalFiber()));
        existing.setTotalSugar(addNullable(existing.getTotalSugar(), addition.getTotalSugar()));
        existing.setTotalSaturatedFat(addNullable(existing.getTotalSaturatedFat(), addition.getTotalSaturatedFat()));
        existing.setTotalSodium(addNullable(existing.getTotalSodium(), addition.getTotalSodium()));
        existing.setTotalPotassium(addNullable(existing.getTotalPotassium(), addition.getTotalPotassium()));
        existing.setTotalCholesterol(addNullable(existing.getTotalCholesterol(), addition.getTotalCholesterol()));
        existing.setTotalCalcium(addNullable(existing.getTotalCalcium(), addition.getTotalCalcium()));
        existing.setTotalIron(addNullable(existing.getTotalIron(), addition.getTotalIron()));
        existing.setTotalMagnesium(addNullable(existing.getTotalMagnesium(), addition.getTotalMagnesium()));
        existing.setTotalZinc(addNullable(existing.getTotalZinc(), addition.getTotalZinc()));
        existing.setTotalVitaminA(addNullable(existing.getTotalVitaminA(), addition.getTotalVitaminA()));
        existing.setTotalVitaminC(addNullable(existing.getTotalVitaminC(), addition.getTotalVitaminC()));
        existing.setTotalVitaminD(addNullable(existing.getTotalVitaminD(), addition.getTotalVitaminD()));
        existing.setTotalVitaminE(addNullable(existing.getTotalVitaminE(), addition.getTotalVitaminE()));
        existing.setTotalVitaminB12(addNullable(existing.getTotalVitaminB12(), addition.getTotalVitaminB12()));
    }

    private FoodLogRecentMealDto toRecentMeal(UserEntity user, LocalDate sourceDate, String mealType) {
        List<FoodLogsEntity> logs = foodLogsRepository.findByUserAndMealTypeAndLogDateBetween(
                user,
                mealType,
                sourceDate.atStartOfDay(),
                sourceDate.plusDays(1).atStartOfDay()
        );
        FoodLogMealSummaryDto summary = toMealSummary(mealType, logs, List.of());
        FoodLogRecentMealDto dto = new FoodLogRecentMealDto();
        dto.setSourceDate(sourceDate);
        dto.setMealType(summary.getMealType());
        dto.setLogs(summary.getLogs());
        dto.setTotalCalories(summary.getTotalCalories());
        dto.setTotalProtein(summary.getTotalProtein());
        dto.setTotalFat(summary.getTotalFat());
        dto.setTotalCarbs(summary.getTotalCarbs());
        return dto;
    }

    private Double sumNutrition(
            List<FoodLogsEntity> logs,
            java.util.function.Function<FoodLogsEntity, Double> snapshot,
            java.util.function.Function<FoodItemEntity, Double> nutrient
    ) {
        return round(logs.stream()
                .mapToDouble(log -> {
                    Double capturedValue = snapshot.apply(log);
                    if (capturedValue != null) {
                        return capturedValue;
                    }
                    if (log.getFoodItem() == null) {
                        return 0.0;
                    }
                    Double value = nutrient.apply(log.getFoodItem());
                    Double grams = log.getNormalizedPortionGrams() != null ? log.getNormalizedPortionGrams() : log.getPortionSize();
                    return (value == null ? 0.0 : value) * (grams == null ? 0.0 : grams) / 100.0;
                })
                .sum());
    }

    private Double sumRecipeNutrition(
            List<RecipeLogEntity> recipeLogs,
            java.util.function.Function<RecipeLogEntity, Double> snapshot
    ) {
        return recipeLogs.stream()
                .map(snapshot)
                .mapToDouble(value -> value == null ? 0.0 : value)
                .sum();
    }

    private RecipeLogDto toRecipeLogDto(RecipeLogEntity log) {
        RecipeLogDto dto = new RecipeLogDto();
        dto.setId(log.getId());
        dto.setRecipeId(log.getRecipe().getId());
        dto.setRecipeName(log.getRecipe().getName());
        dto.setServingGrams(log.getServingGrams());
        dto.setServingCount(log.getServingCount());
        dto.setMealType(log.getMealType());
        dto.setLogDate(log.getLogDate());
        dto.setSnapshotCalories(log.getSnapshotCalories());
        dto.setSnapshotProtein(log.getSnapshotProtein());
        dto.setSnapshotCarbs(log.getSnapshotCarbs());
        dto.setSnapshotFat(log.getSnapshotFat());
        dto.setSnapshotFiber(log.getSnapshotFiber());
        dto.setSnapshotSugar(log.getSnapshotSugar());
        dto.setSnapshotSaturatedFat(log.getSnapshotSaturatedFat());
        dto.setSnapshotSodium(log.getSnapshotSodium());
        dto.setSnapshotPotassium(log.getSnapshotPotassium());
        dto.setSnapshotCholesterol(log.getSnapshotCholesterol());
        dto.setSnapshotCalcium(log.getSnapshotCalcium());
        dto.setSnapshotIron(log.getSnapshotIron());
        dto.setSnapshotMagnesium(log.getSnapshotMagnesium());
        dto.setSnapshotZinc(log.getSnapshotZinc());
        dto.setSnapshotVitaminA(log.getSnapshotVitaminA());
        dto.setSnapshotVitaminC(log.getSnapshotVitaminC());
        dto.setSnapshotVitaminD(log.getSnapshotVitaminD());
        dto.setSnapshotVitaminE(log.getSnapshotVitaminE());
        dto.setSnapshotVitaminB12(log.getSnapshotVitaminB12());
        return dto;
    }

    private String formatDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate().toString();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate().toString();
        }
        if (value instanceof LocalDate localDate) {
            return localDate.toString();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return value.toString();
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private Double toNullableDouble(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return null;
        }
        return round(toDouble(row[index]));
    }

    private Double addNullable(Double first, Double second) {
        if (first == null && second == null) {
            return null;
        }
        return round((first == null ? 0.0 : first) + (second == null ? 0.0 : second));
    }

    private Double round(Double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        return LocalDate.parse(value.toString());
    }

    private void markFoodItemUsed(FoodItemEntity foodItem) {
        FoodProductQualityRules.markUsed(foodItem);
        foodItemRepository.save(foodItem);
    }

    private void validateAiEstimateFoodLogRequest(FoodLogsDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Food log request must not be empty.");
        }
        if (normalizeDisplayName(dto.getDisplayName(), dto.getFoodName()).isBlank()) {
            throw new IllegalArgumentException("Display name is required for AI estimate food logs.");
        }
        if (dto.getPortionSize() == null || dto.getPortionSize() <= 0) {
            throw new IllegalArgumentException("Portion size must be a positive value.");
        }
        if (dto.getSnapshotCalories() == null || dto.getSnapshotCalories() < 0) {
            throw new IllegalArgumentException("Snapshot calories must be provided for AI estimate food logs.");
        }
        validateSnapshotNutritionValue(dto.getSnapshotProtein(), "protein");
        validateSnapshotNutritionValue(dto.getSnapshotCarbs(), "carbohydrate");
        validateSnapshotNutritionValue(dto.getSnapshotFat(), "fat");
        validateSnapshotNutritionValue(dto.getSnapshotFiber(), "fiber");
        validateSnapshotNutritionValue(dto.getSnapshotSugar(), "sugar");
        validateSnapshotNutritionValue(dto.getSnapshotSaturatedFat(), "saturated fat");
        validateSnapshotNutritionValue(dto.getSnapshotSodium(), "sodium");
        validateSnapshotNutritionValue(dto.getSnapshotPotassium(), "potassium");
        validateSnapshotNutritionValue(dto.getSnapshotCholesterol(), "cholesterol");
        validateSnapshotNutritionValue(dto.getSnapshotCalcium(), "calcium");
        validateSnapshotNutritionValue(dto.getSnapshotIron(), "iron");
        validateSnapshotNutritionValue(dto.getSnapshotMagnesium(), "magnesium");
        validateSnapshotNutritionValue(dto.getSnapshotZinc(), "zinc");
        validateSnapshotNutritionValue(dto.getSnapshotVitaminA(), "vitamin A");
        validateSnapshotNutritionValue(dto.getSnapshotVitaminC(), "vitamin C");
        validateSnapshotNutritionValue(dto.getSnapshotVitaminD(), "vitamin D");
        validateSnapshotNutritionValue(dto.getSnapshotVitaminE(), "vitamin E");
        validateSnapshotNutritionValue(dto.getSnapshotVitaminB12(), "vitamin B12");
        if (dto.getAiConfidence() != null && (dto.getAiConfidence() < 0 || dto.getAiConfidence() > 1)) {
            throw new IllegalArgumentException("AI confidence must be between 0 and 1.");
        }
        if (dto.getLogDate() == null) {
            throw new IllegalArgumentException("Log date is required.");
        }
        String mealType = normalizeMealType(dto.getMealType());
        if (!List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(mealType)) {
            throw new IllegalArgumentException("Meal type must be one of BREAKFAST, LUNCH, DINNER, or SNACK.");
        }
    }

    private void validateSnapshotNutritionValue(Double value, String field) {
        if (value != null && (!Double.isFinite(value) || value < 0)) {
            throw new IllegalArgumentException("Snapshot " + field + " must not be negative or non-finite.");
        }
    }

    private String normalizeDisplayName(String displayName, String fallbackName) {
        String value = displayName != null && !displayName.isBlank() ? displayName : fallbackName;
        String normalized = FoodProductNormalizationRules.normalizeProductDisplayName(value);
        return normalized == null ? "" : normalized;
    }

    private Double roundOrZero(Double value) {
        return value == null ? 0.0 : round(value);
    }

    private void validateFoodLogUpdateRequest(FoodLogsDto dto, FoodLogsEntity existing) {
        if (dto == null) {
            throw new IllegalArgumentException("Food log request must not be empty.");
        }
        if ((dto.getFoodItemId() == null || dto.getFoodItemId() <= 0)
                && existing.getFoodItem() == null
                && !hasNutritionSnapshot(existing)) {
            throw new IllegalArgumentException("Food item id must be a positive value.");
        }
        if (dto.getPortionSize() == null || dto.getPortionSize() <= 0) {
            throw new IllegalArgumentException("Portion size must be a positive value.");
        }
        if (dto.getLogDate() == null) {
            throw new IllegalArgumentException("Log date is required.");
        }
        String mealType = normalizeMealType(dto.getMealType());
        if (!List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(mealType)) {
            throw new IllegalArgumentException("Meal type must be one of BREAKFAST, LUNCH, DINNER, or SNACK.");
        }
    }

    private FoodItemEntity resolveFoodItemForUpdate(FoodLogsDto dto, FoodLogsEntity existing) {
        Long requestedFoodItemId = dto.getFoodItemId();
        if (requestedFoodItemId == null || requestedFoodItemId <= 0) {
            return existing.getFoodItem();
        }
        return foodItemRepository.findById(requestedFoodItemId)
                .orElseThrow(() -> new ProductNotFoundException("Food item not found"));
    }

    private boolean hasNutritionSnapshot(FoodLogsEntity entity) {
        return entity.getSnapshotCalories() != null
                || entity.getSnapshotProtein() != null
                || entity.getSnapshotCarbs() != null
                || entity.getSnapshotFat() != null;
    }

    private void updateSnapshotOnlyPortion(FoodLogsEntity entity, FoodLogsDto dto) {
        double previousPortion = entity.getPortionSize() != null && entity.getPortionSize() > 0
                ? entity.getPortionSize()
                : dto.getPortionSize();
        double ratio = dto.getPortionSize() / previousPortion;

        entity.setFoodItem(null);
        entity.setServingOption(null);
        entity.setPortionSize(dto.getPortionSize());
        entity.setPortionUnit(FoodPortionCalculator.resolveUnit(dto.getPortionUnit()));
        entity.setNormalizedPortionGrams(scaleSnapshotValue(entity.getNormalizedPortionGrams(), ratio));
        entity.setSnapshotCalories(scaleSnapshotValue(entity.getSnapshotCalories(), ratio));
        entity.setSnapshotProtein(scaleSnapshotValue(entity.getSnapshotProtein(), ratio));
        entity.setSnapshotCarbs(scaleSnapshotValue(entity.getSnapshotCarbs(), ratio));
        entity.setSnapshotFat(scaleSnapshotValue(entity.getSnapshotFat(), ratio));
        entity.setSnapshotFiber(scaleSnapshotValue(entity.getSnapshotFiber(), ratio));
        entity.setSnapshotSugar(scaleSnapshotValue(entity.getSnapshotSugar(), ratio));
        entity.setSnapshotSaturatedFat(scaleSnapshotValue(entity.getSnapshotSaturatedFat(), ratio));
        entity.setSnapshotSodium(scaleSnapshotValue(entity.getSnapshotSodium(), ratio));
        entity.setSnapshotPotassium(scaleSnapshotValue(entity.getSnapshotPotassium(), ratio));
        entity.setSnapshotCholesterol(scaleSnapshotValue(entity.getSnapshotCholesterol(), ratio));
        entity.setSnapshotCalcium(scaleSnapshotValue(entity.getSnapshotCalcium(), ratio));
        entity.setSnapshotIron(scaleSnapshotValue(entity.getSnapshotIron(), ratio));
        entity.setSnapshotMagnesium(scaleSnapshotValue(entity.getSnapshotMagnesium(), ratio));
        entity.setSnapshotZinc(scaleSnapshotValue(entity.getSnapshotZinc(), ratio));
        entity.setSnapshotVitaminA(scaleSnapshotValue(entity.getSnapshotVitaminA(), ratio));
        entity.setSnapshotVitaminC(scaleSnapshotValue(entity.getSnapshotVitaminC(), ratio));
        entity.setSnapshotVitaminD(scaleSnapshotValue(entity.getSnapshotVitaminD(), ratio));
        entity.setSnapshotVitaminE(scaleSnapshotValue(entity.getSnapshotVitaminE(), ratio));
        entity.setSnapshotVitaminB12(scaleSnapshotValue(entity.getSnapshotVitaminB12(), ratio));
    }

    private Double scaleSnapshotValue(Double value, double ratio) {
        return value == null ? null : round(value * ratio);
    }

    private void validateFoodLogRequest(FoodLogsDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Food log request must not be empty.");
        }
        if (dto.getFoodItemId() == null || dto.getFoodItemId() <= 0) {
            throw new IllegalArgumentException("Food item id must be a positive value.");
        }
        if (dto.getPortionSize() == null || dto.getPortionSize() <= 0) {
            throw new IllegalArgumentException("Portion size must be a positive value.");
        }
        if (dto.getLogDate() == null) {
            throw new IllegalArgumentException("Log date is required.");
        }
        String mealType = normalizeMealType(dto.getMealType());
        if (!List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(mealType)) {
            throw new IllegalArgumentException("Meal type must be one of BREAKFAST, LUNCH, DINNER, or SNACK.");
        }
    }

    private String normalizeMealType(String mealType) {
        return mealType == null ? null : mealType.trim().toUpperCase();
    }

    private FoodLogsEntity copyLogToDate(FoodLogsEntity source, LocalDate targetDate, UserEntity user) {
        if (source.getFoodItem() != null) {
            ensureFoodItemAvailableToUser(source.getFoodItem(), user);
        }
        FoodLogsEntity copy = new FoodLogsEntity();
        copy.setUser(user);
        copy.setFoodItem(source.getFoodItem());
        copy.setDisplayName(source.getDisplayName());
        copy.setEstimated(source.getEstimated());
        copy.setAiRequestId(source.getAiRequestId());
        copy.setAiConfidence(source.getAiConfidence());
        copy.setServingOption(source.getServingOption());
        copy.setPortionSize(source.getPortionSize());
        copy.setPortionUnit(FoodPortionCalculator.resolveUnit(source.getPortionUnit()));
        copy.setNormalizedPortionGrams(source.getNormalizedPortionGrams());
        copy.setSnapshotCalories(source.getSnapshotCalories());
        copy.setSnapshotProtein(source.getSnapshotProtein());
        copy.setSnapshotCarbs(source.getSnapshotCarbs());
        copy.setSnapshotFat(source.getSnapshotFat());
        copy.setSnapshotFiber(source.getSnapshotFiber());
        copy.setSnapshotSugar(source.getSnapshotSugar());
        copy.setSnapshotSaturatedFat(source.getSnapshotSaturatedFat());
        copy.setSnapshotSodium(source.getSnapshotSodium());
        copy.setSnapshotPotassium(source.getSnapshotPotassium());
        copy.setSnapshotCholesterol(source.getSnapshotCholesterol());
        copy.setSnapshotCalcium(source.getSnapshotCalcium());
        copy.setSnapshotIron(source.getSnapshotIron());
        copy.setSnapshotMagnesium(source.getSnapshotMagnesium());
        copy.setSnapshotZinc(source.getSnapshotZinc());
        copy.setSnapshotVitaminA(source.getSnapshotVitaminA());
        copy.setSnapshotVitaminC(source.getSnapshotVitaminC());
        copy.setSnapshotVitaminD(source.getSnapshotVitaminD());
        copy.setSnapshotVitaminE(source.getSnapshotVitaminE());
        copy.setSnapshotVitaminB12(source.getSnapshotVitaminB12());
        copy.setSource(FoodLogSource.RECENT);
        copy.setMealType(normalizeMealType(source.getMealType()));
        copy.setLogDate(targetDate.atTime(source.getLogDate().toLocalTime()));
        return copy;
    }

    private void ensureFoodItemAvailableToUser(FoodItemEntity foodItem, UserEntity user) {
        if (Boolean.TRUE.equals(foodItem.getIsCustom())
                && (foodItem.getCreatedByUser() == null || !foodItem.getCreatedByUser().getId().equals(user.getId()))) {
            throw new ProductNotFoundException("Custom food item is not available to this user");
        }
        if (foodItem.getVerificationStatus() == VerificationStatus.REJECTED) {
            throw new ProductNotFoundException("Food item is not available");
        }
    }

    private UserEntity getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }

    private int normalizeLimit(int limit) {
        if (limit < 1) {
            return 10;
        }
        return Math.min(limit, 30);
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeHistoryPageSize(int size) {
        if (size < 1) {
            return 50;
        }
        return Math.min(size, 100);
    }

    private FoodLogsDto toDto(FoodLogsEntity entity) {
        FoodLogsDto dto = new FoodLogsDto();
        dto.setId(entity.getId());
        if (entity.getFoodItem() != null) {
            dto.setFoodItemId(entity.getFoodItem().getId());
            dto.setFoodName(entity.getFoodItem().getName());
        } else {
            dto.setFoodName(entity.getDisplayName());
        }
        dto.setDisplayName(entity.getDisplayName());
        dto.setEstimated(Boolean.TRUE.equals(entity.getEstimated()));
        dto.setAiRequestId(entity.getAiRequestId());
        dto.setAiConfidence(entity.getAiConfidence());
        if (entity.getServingOption() != null) {
            dto.setServingOptionId(entity.getServingOption().getId());
            dto.setServingOptionLabel(entity.getServingOption().getLabel());
        }
        dto.setPortionSize(entity.getPortionSize());
        dto.setPortionUnit(FoodPortionCalculator.resolveUnit(entity.getPortionUnit()));
        dto.setNormalizedPortionGrams(entity.getNormalizedPortionGrams());
        dto.setSnapshotCalories(entity.getSnapshotCalories());
        dto.setSnapshotProtein(entity.getSnapshotProtein());
        dto.setSnapshotCarbs(entity.getSnapshotCarbs());
        dto.setSnapshotFat(entity.getSnapshotFat());
        dto.setSnapshotFiber(entity.getSnapshotFiber());
        dto.setSnapshotSugar(entity.getSnapshotSugar());
        dto.setSnapshotSaturatedFat(entity.getSnapshotSaturatedFat());
        dto.setSnapshotSodium(entity.getSnapshotSodium());
        dto.setSnapshotPotassium(entity.getSnapshotPotassium());
        dto.setSnapshotCholesterol(entity.getSnapshotCholesterol());
        dto.setSnapshotCalcium(entity.getSnapshotCalcium());
        dto.setSnapshotIron(entity.getSnapshotIron());
        dto.setSnapshotMagnesium(entity.getSnapshotMagnesium());
        dto.setSnapshotZinc(entity.getSnapshotZinc());
        dto.setSnapshotVitaminA(entity.getSnapshotVitaminA());
        dto.setSnapshotVitaminC(entity.getSnapshotVitaminC());
        dto.setSnapshotVitaminD(entity.getSnapshotVitaminD());
        dto.setSnapshotVitaminE(entity.getSnapshotVitaminE());
        dto.setSnapshotVitaminB12(entity.getSnapshotVitaminB12());
        dto.setSource(resolveSource(entity.getSource(), FoodLogSource.MANUAL));
        dto.setMealType(entity.getMealType());
        dto.setLogDate(entity.getLogDate());
        return dto;
    }

    private void applyNutritionSnapshot(FoodLogsEntity entity, FoodItemEntity foodItem) {
        Double grams = entity.getNormalizedPortionGrams() != null
                ? entity.getNormalizedPortionGrams()
                : entity.getPortionSize();
        entity.setSnapshotCalories(calculateNutritionValue(foodItem.getCalories(), grams));
        entity.setSnapshotProtein(calculateNutritionValue(foodItem.getProtein(), grams));
        entity.setSnapshotCarbs(calculateNutritionValue(foodItem.getCarbs(), grams));
        entity.setSnapshotFat(calculateNutritionValue(foodItem.getFat(), grams));
        entity.setSnapshotFiber(calculateNullableNutritionValue(foodItem.getFiber(), grams));
        entity.setSnapshotSugar(calculateNullableNutritionValue(foodItem.getSugar(), grams));
        entity.setSnapshotSaturatedFat(calculateNullableNutritionValue(foodItem.getSaturatedFat(), grams));
        entity.setSnapshotSodium(calculateNullableNutritionValue(foodItem.getSodium(), grams));
        entity.setSnapshotPotassium(calculateNullableNutritionValue(foodItem.getPotassium(), grams));
        entity.setSnapshotCholesterol(calculateNullableNutritionValue(foodItem.getCholesterol(), grams));
        entity.setSnapshotCalcium(calculateNullableNutritionValue(foodItem.getCalcium(), grams));
        entity.setSnapshotIron(calculateNullableNutritionValue(foodItem.getIron(), grams));
        entity.setSnapshotMagnesium(calculateNullableNutritionValue(foodItem.getMagnesium(), grams));
        entity.setSnapshotZinc(calculateNullableNutritionValue(foodItem.getZinc(), grams));
        entity.setSnapshotVitaminA(calculateNullableNutritionValue(foodItem.getVitaminA(), grams));
        entity.setSnapshotVitaminC(calculateNullableNutritionValue(foodItem.getVitaminC(), grams));
        entity.setSnapshotVitaminD(calculateNullableNutritionValue(foodItem.getVitaminD(), grams));
        entity.setSnapshotVitaminE(calculateNullableNutritionValue(foodItem.getVitaminE(), grams));
        entity.setSnapshotVitaminB12(calculateNullableNutritionValue(foodItem.getVitaminB12(), grams));
    }

    private Double calculateNutritionValue(Double perHundredGrams, Double grams) {
        return round((perHundredGrams == null ? 0.0 : perHundredGrams)
                * (grams == null ? 0.0 : grams)
                / 100.0);
    }

    private Double calculateNullableNutritionValue(Double perHundredGrams, Double grams) {
        if (perHundredGrams == null || grams == null) {
            return null;
        }
        return round(perHundredGrams * grams / 100.0);
    }

    private FoodItemServingOptionEntity resolveServingOption(Long servingOptionId, FoodItemEntity foodItem) {
        if (servingOptionId == null) {
            return null;
        }
        return foodItemServingOptionRepository.findByIdAndFoodItem(servingOptionId, foodItem)
                .orElseThrow(() -> new IllegalArgumentException("Serving option does not belong to the selected food item."));
    }

    private FoodLogSource resolveSource(FoodLogSource source, FoodLogSource fallback) {
        return source == null ? fallback : source;
    }

    private FoodLogRecentPortionDto toRecentPortionDto(Object[] row) {
        FoodLogRecentPortionDto dto = new FoodLogRecentPortionDto();
        dto.setPortionSize(toDouble(row[0]));
        dto.setPortionUnit(row[1] == null ? FoodPortionUnit.GRAM : FoodPortionUnit.valueOf(row[1].toString()));
        dto.setServingOptionId(row[2] == null ? null : ((Number) row[2]).longValue());
        dto.setServingOptionLabel(row[3] == null ? null : row[3].toString());
        dto.setNormalizedPortionGrams(toDouble(row[4]));
        dto.setSource(row[5] == null ? FoodLogSource.MANUAL : FoodLogSource.valueOf(row[5].toString()));
        return dto;
    }

}

