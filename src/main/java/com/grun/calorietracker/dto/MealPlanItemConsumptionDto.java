package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.MealPlanItemConsumptionStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MealPlanItemConsumptionDto {
    private Long id;
    private Long mealPlanId;
    private Long mealPlanItemId;
    private MealPlanItemConsumptionStatus status;
    private Double plannedQuantity;
    private FoodPortionUnit plannedUnit;
    private Double consumedQuantity;
    private FoodPortionUnit consumedUnit;
    private Double quantityVariance;
    private Double quantityVariancePercent;
    private MealPlanNutritionSnapshotDto consumedNutrition;
    private Long foodLogId;
    private Long recipeLogId;
    private LocalDateTime createdAt;
}
