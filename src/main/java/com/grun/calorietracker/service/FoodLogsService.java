package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodLogDailyStatsDto;
import com.grun.calorietracker.dto.FoodLogMealSummaryDto;
import com.grun.calorietracker.dto.FoodLogCopyMealRequestDto;
import com.grun.calorietracker.dto.FoodLogRecentMealDto;
import com.grun.calorietracker.dto.FoodLogRecentPortionDto;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.dto.QuickCalorieLogRequestDto;

import java.time.LocalDateTime;
import java.util.List;

// Service interface for managing food logs
public interface FoodLogsService {
        FoodLogsDto addFoodLog(FoodLogsDto dto, String user);
        List<FoodLogsDto> copyMeal(String email, FoodLogCopyMealRequestDto request);
        FoodLogsDto updateFoodLog(Long id, FoodLogsDto dto, String email);
        List<FoodLogsDto> getFoodLogs(String email, String date, int page, int size);
        List<FoodLogsDto> getFoodLogsHistory(String email, LocalDateTime start, LocalDateTime end);
        List<FoodLogMealSummaryDto> getMealSummaries(String email, LocalDateTime start, LocalDateTime end);
        List<FoodLogRecentMealDto> getRecentMeals(String email, int limit);
        List<FoodLogRecentPortionDto> getRecentPortions(String email, Long foodItemId, int limit);
        FoodLogsDto quickAddCalories(String email, QuickCalorieLogRequestDto request);
        FoodLogsDto getFoodLogById(Long id, String email);
        void deleteFoodLog(Long id, String email);
        List<FoodLogDailyStatsDto> getDailyStats(String email, LocalDateTime start, LocalDateTime end);

}
