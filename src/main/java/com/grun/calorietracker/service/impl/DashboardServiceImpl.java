package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.DailySummaryDto;
import com.grun.calorietracker.entity.ProgressLogEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.ExerciseLogRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.GoalRepository;
import com.grun.calorietracker.repository.ProgressLogRepository;
import com.grun.calorietracker.service.DashboardService;
import com.grun.calorietracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

// Service implementation for dashboard operations
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final UserService userService;
    private final GoalRepository goalRepository;
    private final FoodLogsRepository foodLogsRepository;
    private final ExerciseLogRepository exerciseLogRepository;
    private final ProgressLogRepository progressLogRepository;

    @Override
    public DailySummaryDto getDailySummary(String email, LocalDate date) {
        UserEntity user = userService.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        Object[] foodTotals = extractSingleRow(
                foodLogsRepository.getSummaryTotalsByUserAndDateBetween(user.getId(), start, end)
        );
        Object[] exerciseTotals = extractSingleRow(
                exerciseLogRepository.getSummaryTotalsByUserAndDateBetween(user.getId(), start, end)
        );

        Optional<UserGoalEntity> goalOpt = goalRepository.findByUser(user);
        Optional<ProgressLogEntity> latestProgressOpt = progressLogRepository.findTopByUserOrderByLogDateDesc(user);

        Double consumedCalories = getDouble(foodTotals, 0);
        Double consumedProtein = getDouble(foodTotals, 1);
        Double consumedCarbs = getDouble(foodTotals, 2);
        Double consumedFat = getDouble(foodTotals, 3);

        Double burnedCalories = getDouble(exerciseTotals, 0);
        Integer totalExerciseMinutes = getInteger(exerciseTotals, 1);

        Integer targetCalories = goalOpt.map(UserGoalEntity::getDailyCalorieGoal).orElse(0);
        Double targetProtein = goalOpt.map(UserGoalEntity::getDailyProteinGoal).orElse(0.0);
        Double targetFat = goalOpt.map(UserGoalEntity::getDailyFatGoal).orElse(0.0);
        Double targetCarbs = goalOpt.map(UserGoalEntity::getDailyCarbGoal).orElse(0.0);

        Double currentWeight = latestProgressOpt.map(ProgressLogEntity::getWeight).orElse(user.getWeight());
        Double targetWeight = goalOpt.map(UserGoalEntity::getTargetWeight).orElse(null);
        String goalType = goalOpt.map(goal -> goal.getGoalType() != null ? goal.getGoalType().name() : null).orElse(null);

        DailySummaryDto dto = new DailySummaryDto();
        dto.setSummaryDate(date);
        dto.setTargetCalories(targetCalories);
        dto.setConsumedCalories(consumedCalories);
        dto.setBurnedCalories(burnedCalories);
        dto.setRemainingCalories(round((targetCalories + burnedCalories) - consumedCalories));

        dto.setTargetProtein(targetProtein);
        dto.setTargetFat(targetFat);
        dto.setTargetCarbs(targetCarbs);

        dto.setConsumedProtein(consumedProtein);
        dto.setConsumedFat(consumedFat);
        dto.setConsumedCarbs(consumedCarbs);

        dto.setCurrentWeight(currentWeight);
        dto.setTargetWeight(targetWeight);
        dto.setGoalType(goalType);
        dto.setTotalExerciseMinutes(totalExerciseMinutes);

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

    private Integer getInteger(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return 0;
        }
        return ((Number) row[index]).intValue();
    }

    private Double round(Double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private Object[] extractSingleRow(java.util.List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return new Object[0];
        }
        return rows.get(0);
    }
}