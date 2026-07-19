package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.NutritionPlanDayType;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MealPlanDayNutritionDto {
    private LocalDate date;
    private NutritionPlanDayType dayType;
    private MealPlanNutritionSnapshotDto totalNutrition;
}
