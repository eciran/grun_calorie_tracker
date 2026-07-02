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
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;
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
import com.grun.calorietracker.service.support.FoodPortionCalculator;
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

        FoodLogsEntity saved = foodLogsRepository.save(entity);
        markFoodItemUsed(foodItem);
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

        return sourceLogs.stream()
                .map(source -> copyLogToDate(source, request.getTargetDate(), user))
                .map(foodLogsRepository::save)
                .peek(saved -> markFoodItemUsed(saved.getFoodItem()))
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public FoodLogsDto updateFoodLog(Long id, FoodLogsDto dto, String email) {
        validateFoodLogRequest(dto);
        UserEntity user = getUser(email);
        FoodLogsEntity entity = foodLogsRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Food log not found"));
        FoodItemEntity foodItem = foodItemRepository.findById(dto.getFoodItemId())
                .orElseThrow(() -> new ProductNotFoundException("Food item not found"));
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
        entity.setMealType(normalizeMealType(dto.getMealType()));
        entity.setLogDate(dto.getLogDate());
        entity.setSource(resolveSource(dto.getSource(), entity.getSource()));

        return toDto(foodLogsRepository.save(entity));
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
        FoodItemEntity quickCalories = getOrCreateQuickCaloriesFood(user);

        FoodLogsEntity entity = new FoodLogsEntity();
        entity.setUser(user);
        entity.setFoodItem(quickCalories);
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
        markFoodItemUsed(quickCalories);
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
    public void deleteFoodLog(Long id, String email) {
        UserEntity user = getUser(email);
        FoodLogsEntity entity = foodLogsRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Food log not found"));
        foodLogsRepository.delete(entity);
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
        ensureFoodItemAvailableToUser(source.getFoodItem(), user);
        FoodLogsEntity copy = new FoodLogsEntity();
        copy.setUser(user);
        copy.setFoodItem(source.getFoodItem());
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
        dto.setFoodItemId(entity.getFoodItem().getId());
        dto.setFoodName(entity.getFoodItem().getName());
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

    private FoodItemEntity getOrCreateQuickCaloriesFood(UserEntity user) {
        String sourceKey = "quick-calorie:user:" + user.getId();
        return foodItemRepository.findBySourceKey(sourceKey)
                .map(item -> {
                    item.setCalories(100.0);
                    item.setProtein(0.0);
                    item.setCarbs(0.0);
                    item.setFat(0.0);
                    item.setServingSizeGrams(100.0);
                    item.setServingUnit("kcal");
                    return item;
                })
                .orElseGet(() -> {
                    FoodItemEntity item = new FoodItemEntity();
                    item.setName("Quick calories");
                    item.setSourceKey(sourceKey);
                    item.setCalories(100.0);
                    item.setProtein(0.0);
                    item.setCarbs(0.0);
                    item.setFat(0.0);
                    item.setServingSizeGrams(100.0);
                    item.setServingUnit("kcal");
                    item.setDataSource(FoodDataSource.MANUAL);
                    item.setCatalogType(FoodCatalogType.USER_CUSTOM);
                    item.setVerificationStatus(VerificationStatus.VERIFIED);
                    item.setIsCustom(true);
                    item.setCreatedByUser(user);
                    item.setUsageCount(0L);
                    FoodProductQualityRules.updateQualityAndReviewPriority(item);
                    return foodItemRepository.save(item);
                });
    }
}
