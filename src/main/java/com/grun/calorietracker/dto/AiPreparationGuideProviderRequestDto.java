package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.MealPlanWorkoutRelation;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiPreparationGuideProviderRequestDto {
    private Long mealPlanId;
    private Long mealPlanItemId;
    private LocalDate planDate;
    private String mealType;
    private String itemName;
    private String itemDescription;
    private Double plannedQuantity;
    private FoodPortionUnit plannedUnit;
    private MealPlanNutritionSnapshotDto plannedNutrition;
    private String shortPreparationState;
    private MealPlanWorkoutRelation workoutRelation;
    private List<String> allergens = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private String language;
}
