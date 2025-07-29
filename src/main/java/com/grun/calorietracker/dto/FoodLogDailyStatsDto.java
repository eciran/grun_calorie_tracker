package com.grun.calorietracker.dto;

import lombok.Data;

// DTO for daily food log statistics
@Data
public class FoodLogDailyStatsDto {
    private String date;
    private Double totalCalories;
    private Double totalProtein;
    private Double totalCarbs;
    private Double totalFat;
}
