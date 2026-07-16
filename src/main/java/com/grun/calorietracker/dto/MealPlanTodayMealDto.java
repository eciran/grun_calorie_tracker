package com.grun.calorietracker.dto;

import lombok.Data;

import java.util.List;

@Data
public class MealPlanTodayMealDto {
    private String mealType;
    private List<MealPlanTodayItemDto> items;
}
