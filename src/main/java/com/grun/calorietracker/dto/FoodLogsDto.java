package com.grun.calorietracker.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FoodLogsDto {
    private Long id;
    private Long foodItemId;
    private String foodName;
    private Double portionSize;
    private String mealType;
    private LocalDateTime logDate;
}