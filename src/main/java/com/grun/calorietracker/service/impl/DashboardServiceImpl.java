package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.DailySummaryDto;
import com.grun.calorietracker.dto.ExerciseLogsDto;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.dto.MealMacroDistributionDto;
import com.grun.calorietracker.dto.MicronutrientTotalsDto;
import com.grun.calorietracker.dto.WeightTrendDto;
import com.grun.calorietracker.entity.ExerciseLogsEntity;
import com.grun.calorietracker.entity.FoodLogsEntity;
import com.grun.calorietracker.entity.ProgressLogEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.ExerciseLogRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.repository.ProgressLogRepository;
import com.grun.calorietracker.repository.RecipeLogRepository;
import com.grun.calorietracker.service.DashboardService;
import com.grun.calorietracker.service.HealthIntegrationService;
import com.grun.calorietracker.service.StepTrackingService;
import com.grun.calorietracker.service.SubscriptionService;
import com.grun.calorietracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.sql.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Service implementation for dashboard operations
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final UserService userService;
    private final GoalRepository goalRepository;
    private final FoodLogsRepository foodLogsRepository;
    private final ExerciseLogRepository exerciseLogRepository;
    private final ProgressLogRepository progressLogRepository;
    private final RecipeLogRepository recipeLogRepository;
    private final HealthIntegrationService healthIntegrationService;
    private final StepTrackingService stepTrackingService;
    private final SubscriptionService subscriptionService;

    @Override
    @Transactional(readOnly = true)
    public DailySummaryDto getDailySummary(String email, LocalDate date) {
        UserEntity user = userService.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        Object[] foodTotals = extractSingleRow(
                foodLogsRepository.getSummaryTotalsByUserAndDateBetween(user.getId(), start, end)
        );
        Object[] recipeTotals = extractSingleRow(
                recipeLogRepository.getSummaryTotalsByUserAndDateBetween(user.getId(), start, end)
        );
        Object[] exerciseTotals = extractSingleRow(
                exerciseLogRepository.getSummaryTotalsByUserAndDateBetween(user.getId(), start, end)
        );

        Optional<UserGoalEntity> goalOpt = goalRepository.findByUser(user);
        Optional<ProgressLogEntity> latestProgressOpt = progressLogRepository.findTopByUserOrderByLogDateDesc(user);

        Double consumedCalories = round(getDouble(foodTotals, 0) + getDouble(recipeTotals, 0));
        Double consumedProtein = round(getDouble(foodTotals, 1) + getDouble(recipeTotals, 1));
        Double consumedCarbs = round(getDouble(foodTotals, 2) + getDouble(recipeTotals, 2));
        Double consumedFat = round(getDouble(foodTotals, 3) + getDouble(recipeTotals, 3));
        MicronutrientTotalsDto consumedMicros = combineMicros(foodTotals, recipeTotals);

        Double burnedCalories = getDouble(exerciseTotals, 0);
        Integer totalExerciseMinutes = getInteger(exerciseTotals, 1);

        boolean hasActiveGoal = goalOpt.isPresent();
        Integer targetCalories = goalOpt.map(UserGoalEntity::getDailyCalorieGoal).orElse(0);
        Double targetProtein = goalOpt.map(UserGoalEntity::getDailyProteinGoal).orElse(0.0);
        Double targetFat = goalOpt.map(UserGoalEntity::getDailyFatGoal).orElse(0.0);
        Double targetCarbs = goalOpt.map(UserGoalEntity::getDailyCarbGoal).orElse(0.0);

        Double currentWeight = latestProgressOpt.map(ProgressLogEntity::getWeight).orElse(user.getWeight());
        Double targetWeight = goalOpt.map(UserGoalEntity::getTargetWeight).orElse(null);
        String goalType = goalOpt.map(goal -> goal.getGoalType() != null ? goal.getGoalType().name() : null).orElse(null);

        List<FoodLogsDto> foodLogs = foodLogsRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                        user,
                        start,
                        end
                ).stream()
                .map(this::toFoodLogDto)
                .toList();
        List<ExerciseLogsDto> exerciseLogs = exerciseLogRepository.findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(
                        user,
                        start,
                        end
                ).stream()
                .map(this::toExerciseLogDto)
                .toList();

        DailySummaryDto dto = new DailySummaryDto();
        dto.setSummaryDate(date);
        dto.setTargetCalories(targetCalories);
        dto.setConsumedCalories(consumedCalories);
        dto.setBurnedCalories(burnedCalories);
        dto.setRemainingCalories(round((targetCalories + burnedCalories) - consumedCalories));
        dto.setNetCalories(round(consumedCalories - burnedCalories));
        dto.setCalorieProgressPercent(percent(consumedCalories, targetCalories.doubleValue()));

        dto.setTargetProtein(targetProtein);
        dto.setTargetFat(targetFat);
        dto.setTargetCarbs(targetCarbs);

        dto.setConsumedProtein(consumedProtein);
        dto.setConsumedFat(consumedFat);
        dto.setConsumedCarbs(consumedCarbs);
        dto.setRemainingProtein(round(targetProtein - consumedProtein));
        dto.setRemainingFat(round(targetFat - consumedFat));
        dto.setRemainingCarbs(round(targetCarbs - consumedCarbs));
        dto.setProteinProgressPercent(percent(consumedProtein, targetProtein));
        dto.setFatProgressPercent(percent(consumedFat, targetFat));
        dto.setCarbsProgressPercent(percent(consumedCarbs, targetCarbs));
        dto.setConsumedMicros(consumedMicros);
        MicronutrientTotalsDto targetMicros = defaultTargetMicros();
        dto.setTargetMicros(targetMicros);
        dto.setRemainingMicros(calculateRemainingMicros(consumedMicros, targetMicros));
        dto.setProteinTargetHit(targetProtein > 0 && consumedProtein >= targetProtein);
        dto.setFiberTargetHit(consumedMicros != null && consumedMicros.getFiber() != null && consumedMicros.getFiber() >= 25.0);
        dto.setSugarWarning(consumedMicros != null && consumedMicros.getSugar() != null && consumedMicros.getSugar() > 50.0);
        dto.setSodiumWarning(consumedMicros != null && consumedMicros.getSodium() != null && consumedMicros.getSodium() > 2300.0);
        dto.setNutritionQualityScore(calculateNutritionQualityScore(dto));
        dto.setMealMacroDistribution(calculateMealMacroDistribution(foodLogs, consumedCalories));

        dto.setCurrentWeight(currentWeight);
        dto.setTargetWeight(targetWeight);
        dto.setWeightTrend(calculateWeightTrend(user, date, targetWeight));
        dto.setGoalType(goalType);
        dto.setTotalExerciseMinutes(totalExerciseMinutes);
        dto.setHasActiveGoal(hasActiveGoal);
        dto.setOnboardingCompleted(isOnboardingCompleted(user, hasActiveGoal));
        dto.setHasFoodLogs(!foodLogs.isEmpty());
        dto.setHasExerciseLogs(!exerciseLogs.isEmpty());
        dto.setHasAnyDiaryEntry(!foodLogs.isEmpty() || !exerciseLogs.isEmpty());
        dto.setCurrentLogStreakDays(calculateCurrentLogStreakDays(user.getId(), date));
        dto.setFoodLogs(foodLogs);
        dto.setExerciseLogs(exerciseLogs);
        if (subscriptionService.hasFeatureAccess(email, SubscriptionFeature.HEALTH_INTEGRATION)) {
            dto.setHealthSummary(healthIntegrationService.getDailySummary(email, date));
        }
        dto.setStepSummary(stepTrackingService.getDailySummary(email, date));

        return dto;
    }

    private int calculateCurrentLogStreakDays(Long userId, LocalDate summaryDate) {
        LocalDate startDate = summaryDate.minusDays(29);
        List<Object> rows = foodLogsRepository.findDiaryEntryDates(
                userId,
                startDate.atStartOfDay(),
                summaryDate.plusDays(1).atStartOfDay()
        );
        Set<LocalDate> loggedDates = new HashSet<>();
        for (Object row : rows) {
            LocalDate date = toLocalDate(row);
            if (date != null) {
                loggedDates.add(date);
            }
        }
        int streak = 0;
        LocalDate cursor = summaryDate;
        while (!cursor.isBefore(startDate) && loggedDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        return value == null ? null : LocalDate.parse(value.toString());
    }

    private FoodLogsDto toFoodLogDto(FoodLogsEntity entity) {
        FoodLogsDto dto = new FoodLogsDto();
        dto.setId(entity.getId());
        dto.setFoodItemId(entity.getFoodItem().getId());
        dto.setFoodName(entity.getFoodItem().getName());
        dto.setPortionSize(entity.getPortionSize());
        dto.setPortionUnit(entity.getPortionUnit());
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
        dto.setSource(entity.getSource());
        dto.setMealType(entity.getMealType());
        dto.setLogDate(entity.getLogDate());
        return dto;
    }

    private MicronutrientTotalsDto combineMicros(Object[] foodTotals, Object[] recipeTotals) {
        MicronutrientTotalsDto dto = new MicronutrientTotalsDto();
        dto.setFiber(addNullable(getNullableDouble(foodTotals, 4), getNullableDouble(recipeTotals, 4)));
        dto.setSugar(addNullable(getNullableDouble(foodTotals, 5), getNullableDouble(recipeTotals, 5)));
        dto.setSaturatedFat(addNullable(getNullableDouble(foodTotals, 6), getNullableDouble(recipeTotals, 6)));
        dto.setSodium(addNullable(getNullableDouble(foodTotals, 7), getNullableDouble(recipeTotals, 7)));
        dto.setPotassium(addNullable(getNullableDouble(foodTotals, 8), getNullableDouble(recipeTotals, 8)));
        dto.setCholesterol(addNullable(getNullableDouble(foodTotals, 9), getNullableDouble(recipeTotals, 9)));
        dto.setCalcium(addNullable(getNullableDouble(foodTotals, 10), getNullableDouble(recipeTotals, 10)));
        dto.setIron(addNullable(getNullableDouble(foodTotals, 11), getNullableDouble(recipeTotals, 11)));
        dto.setMagnesium(addNullable(getNullableDouble(foodTotals, 12), getNullableDouble(recipeTotals, 12)));
        dto.setZinc(addNullable(getNullableDouble(foodTotals, 13), getNullableDouble(recipeTotals, 13)));
        dto.setVitaminA(addNullable(getNullableDouble(foodTotals, 14), getNullableDouble(recipeTotals, 14)));
        dto.setVitaminC(addNullable(getNullableDouble(foodTotals, 15), getNullableDouble(recipeTotals, 15)));
        dto.setVitaminD(addNullable(getNullableDouble(foodTotals, 16), getNullableDouble(recipeTotals, 16)));
        dto.setVitaminE(addNullable(getNullableDouble(foodTotals, 17), getNullableDouble(recipeTotals, 17)));
        dto.setVitaminB12(addNullable(getNullableDouble(foodTotals, 18), getNullableDouble(recipeTotals, 18)));
        return hasAnyMicronutrient(dto) ? dto : null;
    }

    private boolean hasAnyMicronutrient(MicronutrientTotalsDto dto) {
        return dto.getFiber() != null
                || dto.getSugar() != null
                || dto.getSaturatedFat() != null
                || dto.getSodium() != null
                || dto.getPotassium() != null
                || dto.getCholesterol() != null
                || dto.getCalcium() != null
                || dto.getIron() != null
                || dto.getMagnesium() != null
                || dto.getZinc() != null
                || dto.getVitaminA() != null
                || dto.getVitaminC() != null
                || dto.getVitaminD() != null
                || dto.getVitaminE() != null
                || dto.getVitaminB12() != null;
    }

    private ExerciseLogsDto toExerciseLogDto(ExerciseLogsEntity entity) {
        ExerciseLogsDto dto = new ExerciseLogsDto();
        dto.setId(entity.getId());
        dto.setExerciseItemId(entity.getExerciseItem().getId());
        dto.setExerciseItemName(entity.getExerciseItem().getName());
        dto.setDurationMinutes(entity.getDurationMinutes());
        dto.setMeasurementType(entity.getMeasurementType());
        dto.setSetCount(entity.getSetCount());
        dto.setReps(entity.getReps());
        dto.setWeightKg(entity.getWeightKg());
        dto.setDistanceKm(entity.getDistanceKm());
        dto.setCaloriesBurned(entity.getCaloriesBurned());
        dto.setLogDate(entity.getLogDate());
        dto.setSource(entity.getSource());
        dto.setExternalId(entity.getExternalId());
        dto.setExtraData(entity.getExtraData());
        return dto;
    }

    private Double getDouble(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return 0.0;
        }

        Object value = row[index];

        if (value instanceof Number number) {
            return round(number.doubleValue());
        }

        throw new IllegalStateException("Expected numeric value at index " + index + " but got: " + value.getClass().getName());
    }

    private Double getNullableDouble(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return null;
        }
        return getDouble(row, index);
    }

    private Double addNullable(Double first, Double second) {
        if (first == null && second == null) {
            return null;
        }
        return round((first == null ? 0.0 : first) + (second == null ? 0.0 : second));
    }

    private Integer getInteger(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return 0;
        }
        return ((Number) row[index]).intValue();
    }

    private Double round(Double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private Double percent(Double value, Double target) {
        if (target == null || target <= 0) {
            return 0.0;
        }
        return round((value / target) * 100.0);
    }

    private boolean isOnboardingCompleted(UserEntity user, boolean hasActiveGoal) {
        return hasActiveGoal
                && user.getAge() != null
                && user.getGender() != null
                && user.getHeight() != null
                && user.getWeight() != null;
    }

    private MicronutrientTotalsDto defaultTargetMicros() {
        MicronutrientTotalsDto target = new MicronutrientTotalsDto();
        target.setFiber(25.0);
        target.setSugar(50.0);
        target.setSaturatedFat(20.0);
        target.setSodium(2300.0);
        target.setPotassium(3500.0);
        target.setCholesterol(300.0);
        target.setCalcium(1000.0);
        target.setIron(14.0);
        target.setMagnesium(375.0);
        target.setZinc(10.0);
        target.setVitaminA(800.0);
        target.setVitaminC(80.0);
        target.setVitaminD(5.0);
        target.setVitaminE(12.0);
        target.setVitaminB12(2.5);
        return target;
    }

    private MicronutrientTotalsDto calculateRemainingMicros(MicronutrientTotalsDto consumed, MicronutrientTotalsDto target) {
        MicronutrientTotalsDto remaining = new MicronutrientTotalsDto();
        remaining.setFiber(round(target.getFiber() - micro(consumed == null ? null : consumed.getFiber())));
        remaining.setSugar(round(target.getSugar() - micro(consumed == null ? null : consumed.getSugar())));
        remaining.setSaturatedFat(round(target.getSaturatedFat() - micro(consumed == null ? null : consumed.getSaturatedFat())));
        remaining.setSodium(round(target.getSodium() - micro(consumed == null ? null : consumed.getSodium())));
        remaining.setPotassium(round(target.getPotassium() - micro(consumed == null ? null : consumed.getPotassium())));
        remaining.setCholesterol(round(target.getCholesterol() - micro(consumed == null ? null : consumed.getCholesterol())));
        remaining.setCalcium(round(target.getCalcium() - micro(consumed == null ? null : consumed.getCalcium())));
        remaining.setIron(round(target.getIron() - micro(consumed == null ? null : consumed.getIron())));
        remaining.setMagnesium(round(target.getMagnesium() - micro(consumed == null ? null : consumed.getMagnesium())));
        remaining.setZinc(round(target.getZinc() - micro(consumed == null ? null : consumed.getZinc())));
        remaining.setVitaminA(round(target.getVitaminA() - micro(consumed == null ? null : consumed.getVitaminA())));
        remaining.setVitaminC(round(target.getVitaminC() - micro(consumed == null ? null : consumed.getVitaminC())));
        remaining.setVitaminD(round(target.getVitaminD() - micro(consumed == null ? null : consumed.getVitaminD())));
        remaining.setVitaminE(round(target.getVitaminE() - micro(consumed == null ? null : consumed.getVitaminE())));
        remaining.setVitaminB12(round(target.getVitaminB12() - micro(consumed == null ? null : consumed.getVitaminB12())));
        return remaining;
    }

    private double micro(Double value) {
        return value == null ? 0.0 : value;
    }

    private Integer calculateNutritionQualityScore(DailySummaryDto dto) {
        int score = 0;
        if (Boolean.TRUE.equals(dto.getProteinTargetHit())) {
            score += 25;
        }
        if (Boolean.TRUE.equals(dto.getFiberTargetHit())) {
            score += 25;
        }
        if (!Boolean.TRUE.equals(dto.getSugarWarning())) {
            score += 25;
        }
        if (!Boolean.TRUE.equals(dto.getSodiumWarning())) {
            score += 25;
        }
        return score;
    }

    private List<MealMacroDistributionDto> calculateMealMacroDistribution(List<FoodLogsDto> foodLogs, Double totalCalories) {
        Map<String, List<FoodLogsDto>> byMeal = foodLogs.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        log -> log.getMealType() == null ? "UNKNOWN" : log.getMealType(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        return byMeal.entrySet().stream()
                .map(entry -> {
                    MealMacroDistributionDto dto = new MealMacroDistributionDto();
                    dto.setMealType(entry.getKey());
                    dto.setCalories(round(entry.getValue().stream().mapToDouble(log -> value(log.getSnapshotCalories())).sum()));
                    dto.setProtein(round(entry.getValue().stream().mapToDouble(log -> value(log.getSnapshotProtein())).sum()));
                    dto.setCarbs(round(entry.getValue().stream().mapToDouble(log -> value(log.getSnapshotCarbs())).sum()));
                    dto.setFat(round(entry.getValue().stream().mapToDouble(log -> value(log.getSnapshotFat())).sum()));
                    dto.setCalorieSharePercent(percent(dto.getCalories(), totalCalories));
                    return dto;
                })
                .toList();
    }

    private WeightTrendDto calculateWeightTrend(UserEntity user, LocalDate summaryDate, Double targetWeight) {
        LocalDateTime start = summaryDate.minusDays(13).atStartOfDay();
        LocalDateTime end = summaryDate.plusDays(1).atStartOfDay();
        List<ProgressLogEntity> logs = progressLogRepository
                .findByUserAndLogDateGreaterThanEqualAndLogDateLessThanOrderByLogDateAsc(user, start, end);
        if (logs.isEmpty()) {
            return null;
        }
        List<ProgressLogEntity> current = logs.stream()
                .filter(log -> !log.getLogDate().toLocalDate().isBefore(summaryDate.minusDays(6)))
                .toList();
        List<ProgressLogEntity> previous = logs.stream()
                .filter(log -> log.getLogDate().toLocalDate().isBefore(summaryDate.minusDays(6)))
                .toList();
        WeightTrendDto dto = new WeightTrendDto();
        dto.setCurrentSevenDayAverageKg(averageWeight(current));
        dto.setPreviousSevenDayAverageKg(averageWeight(previous));
        if (dto.getCurrentSevenDayAverageKg() != null && dto.getPreviousSevenDayAverageKg() != null) {
            dto.setWeeklyChangeKg(round(dto.getCurrentSevenDayAverageKg() - dto.getPreviousSevenDayAverageKg()));
            dto.setTrendDirection(resolveTrendDirection(dto.getWeeklyChangeKg()));
            dto.setProjectedGoalDate(projectGoalDate(dto.getCurrentSevenDayAverageKg(), targetWeight, dto.getWeeklyChangeKg(), summaryDate));
        } else {
            dto.setTrendDirection("INSUFFICIENT_DATA");
        }
        return dto;
    }

    private Double averageWeight(List<ProgressLogEntity> logs) {
        if (logs == null || logs.isEmpty()) {
            return null;
        }
        return round(logs.stream().mapToDouble(ProgressLogEntity::getWeight).average().orElse(0.0));
    }

    private String resolveTrendDirection(Double weeklyChangeKg) {
        if (weeklyChangeKg == null || Math.abs(weeklyChangeKg) < 0.1) {
            return "STABLE";
        }
        return weeklyChangeKg > 0 ? "GAINING" : "LOSING";
    }

    private LocalDate projectGoalDate(Double currentWeight, Double targetWeight, Double weeklyChangeKg, LocalDate summaryDate) {
        if (currentWeight == null || targetWeight == null || weeklyChangeKg == null || Math.abs(weeklyChangeKg) < 0.1) {
            return null;
        }
        double remaining = targetWeight - currentWeight;
        if (Math.signum(remaining) != Math.signum(weeklyChangeKg)) {
            return null;
        }
        long weeks = (long) Math.ceil(Math.abs(remaining / weeklyChangeKg));
        return summaryDate.plusWeeks(Math.min(weeks, 260));
    }

    private double value(Double value) {
        return value == null ? 0.0 : value;
    }

    private Object[] extractSingleRow(java.util.List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return new Object[0];
        }
        return rows.get(0);
    }
}
