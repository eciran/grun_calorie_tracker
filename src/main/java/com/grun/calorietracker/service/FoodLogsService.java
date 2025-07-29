package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodLogDailyStatsDto;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.entity.UserEntity;

import java.time.LocalDateTime;
import java.util.List;

// Service interface for managing food logs
public interface FoodLogsService {
        FoodLogsDto addFoodLog(FoodLogsDto dto, UserEntity user);
        List<FoodLogsDto> getFoodLogs(UserEntity user, String date);
        FoodLogsDto getFoodLogById(Long id, UserEntity user);
        void deleteFoodLog(Long id, UserEntity user);
        List<FoodLogDailyStatsDto> getDailyStats(UserEntity user, LocalDateTime start, LocalDateTime end);
}
