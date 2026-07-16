package com.grun.calorietracker.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class MealPlanTodayDto {
    private Long planId;
    private String planName;
    private LocalDate date;
    private List<MealPlanTodayMealDto> meals;
}
