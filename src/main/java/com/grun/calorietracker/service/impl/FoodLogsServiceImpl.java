package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodLogDailyStatsDto;
import com.grun.calorietracker.dto.FoodLogCopyMealRequestDto;
import com.grun.calorietracker.dto.FoodLogMealSummaryDto;
import com.grun.calorietracker.dto.FoodLogRecentMealDto;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodLogsEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.FoodLogsService;
import com.grun.calorietracker.service.support.FoodPortionCalculator;
import com.grun.calorietracker.service.support.FoodProductQualityRules;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
        entity.setPortionSize(dto.getPortionSize());
        entity.setPortionUnit(FoodPortionCalculator.resolveUnit(dto.getPortionUnit()));
        entity.setNormalizedPortionGrams(FoodPortionCalculator.normalizeToGrams(
                dto.getPortionSize(),
                entity.getPortionUnit(),
                foodItem
        ));
        applyNutritionSnapshot(entity, foodItem);
        entity.setMealType(normalizeMealType(dto.getMealType()));
        entity.setLogDate(dto.getLogDate());

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
        entity.setPortionSize(dto.getPortionSize());
        entity.setPortionUnit(FoodPortionCalculator.resolveUnit(dto.getPortionUnit()));
        entity.setNormalizedPortionGrams(FoodPortionCalculator.normalizeToGrams(
                dto.getPortionSize(),
                entity.getPortionUnit(),
                foodItem
        ));
        applyNutritionSnapshot(entity, foodItem);
        entity.setMealType(normalizeMealType(dto.getMealType()));
        entity.setLogDate(dto.getLogDate());

        return toDto(foodLogsRepository.save(entity));
    }

    @Override
    public List<FoodLogsDto> getFoodLogs(String email, String date) {
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
            logs = foodLogsRepository.findByUser(user);
        }
        return logs.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
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
        return List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").stream()
                .map(mealType -> toMealSummary(mealType, logsByMeal.getOrDefault(mealType, List.of())))
                .toList();
    }

    @Override
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
    public List<FoodLogDailyStatsDto> getDailyStats(String email, LocalDateTime start, LocalDateTime end) {
        UserEntity user = getUser(email);

        return foodLogsRepository.getDailyStatsByUserAndDateBetween(user.getId(), start, end)
                .stream()
                .map(this::toDailyStatsDto)
                .collect(Collectors.toList());
    }

    private FoodLogDailyStatsDto toDailyStatsDto(Object[] row) {
        FoodLogDailyStatsDto dto = new FoodLogDailyStatsDto();
        dto.setDate(formatDate(row[0]));
        dto.setTotalCalories(toDouble(row[1]));
        dto.setTotalProtein(toDouble(row[2]));
        dto.setTotalCarbs(toDouble(row[3]));
        dto.setTotalFat(toDouble(row[4]));
        return dto;
    }

    private FoodLogMealSummaryDto toMealSummary(String mealType, List<FoodLogsEntity> logs) {
        FoodLogMealSummaryDto dto = new FoodLogMealSummaryDto();
        dto.setMealType(mealType);
        dto.setLogs(logs.stream().map(this::toDto).toList());
        dto.setTotalCalories(sumNutrition(logs, FoodLogsEntity::getSnapshotCalories, FoodItemEntity::getCalories));
        dto.setTotalProtein(sumNutrition(logs, FoodLogsEntity::getSnapshotProtein, FoodItemEntity::getProtein));
        dto.setTotalFat(sumNutrition(logs, FoodLogsEntity::getSnapshotFat, FoodItemEntity::getFat));
        dto.setTotalCarbs(sumNutrition(logs, FoodLogsEntity::getSnapshotCarbs, FoodItemEntity::getCarbs));
        return dto;
    }

    private FoodLogRecentMealDto toRecentMeal(UserEntity user, LocalDate sourceDate, String mealType) {
        List<FoodLogsEntity> logs = foodLogsRepository.findByUserAndMealTypeAndLogDateBetween(
                user,
                mealType,
                sourceDate.atStartOfDay(),
                sourceDate.plusDays(1).atStartOfDay()
        );
        FoodLogMealSummaryDto summary = toMealSummary(mealType, logs);
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
        copy.setPortionSize(source.getPortionSize());
        copy.setPortionUnit(FoodPortionCalculator.resolveUnit(source.getPortionUnit()));
        copy.setNormalizedPortionGrams(source.getNormalizedPortionGrams());
        copy.setSnapshotCalories(source.getSnapshotCalories());
        copy.setSnapshotProtein(source.getSnapshotProtein());
        copy.setSnapshotCarbs(source.getSnapshotCarbs());
        copy.setSnapshotFat(source.getSnapshotFat());
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

    private FoodLogsDto toDto(FoodLogsEntity entity) {
        FoodLogsDto dto = new FoodLogsDto();
        dto.setId(entity.getId());
        dto.setFoodItemId(entity.getFoodItem().getId());
        dto.setFoodName(entity.getFoodItem().getName());
        dto.setPortionSize(entity.getPortionSize());
        dto.setPortionUnit(FoodPortionCalculator.resolveUnit(entity.getPortionUnit()));
        dto.setNormalizedPortionGrams(entity.getNormalizedPortionGrams());
        dto.setSnapshotCalories(entity.getSnapshotCalories());
        dto.setSnapshotProtein(entity.getSnapshotProtein());
        dto.setSnapshotCarbs(entity.getSnapshotCarbs());
        dto.setSnapshotFat(entity.getSnapshotFat());
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
    }

    private Double calculateNutritionValue(Double perHundredGrams, Double grams) {
        return round((perHundredGrams == null ? 0.0 : perHundredGrams)
                * (grams == null ? 0.0 : grams)
                / 100.0);
    }
}
