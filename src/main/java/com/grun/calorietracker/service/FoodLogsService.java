package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodLogDailyStatsDto;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.entity.UserEntity;

import java.time.LocalDateTime;
import java.util.List;

// Service interface for managing food logs
public interface FoodLogsService {
        FoodLogsDto addFoodLog(FoodLogsDto dto, String user);
        List<FoodLogsDto> getFoodLogs(String email, String date);
        FoodLogsDto getFoodLogById(Long id, String email);
        void deleteFoodLog(Long id, String email);
        List<FoodLogDailyStatsDto> getDailyStats(String email, LocalDateTime start, LocalDateTime end);
}
