package com.grun.calorietracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MealPlanMealLogResponseDto {
    private String mealType;
    private int loggedItemCount;
    private List<MealPlanItemConsumptionDto> items;
}

