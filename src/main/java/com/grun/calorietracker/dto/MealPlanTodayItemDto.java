package com.grun.calorietracker.dto;

import lombok.Data;

@Data
public class MealPlanTodayItemDto {
    private MealPlanItemDto item;
    private MealPlanItemConsumptionDto consumption;
}
