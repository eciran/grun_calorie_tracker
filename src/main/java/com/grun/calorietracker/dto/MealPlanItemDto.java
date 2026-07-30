package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.MealPlanItemType;

import com.grun.calorietracker.enums.MealPlanItemLinkState;
import com.grun.calorietracker.enums.MealPlanWorkoutRelation;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MealPlanItemDto {
    private Long id;
    private LocalDate planDate;
    private String mealType;
    private MealPlanItemType itemType;
    private Long foodItemId;
    private String foodItemName;
    private Long recipeId;
    private String recipeName;
    private boolean recipeNavigationAvailable;
    private boolean recipeOwnedByUser;
    private Double portionSize;
    private FoodPortionUnit portionUnit;
    private Double servingCount;
    private Integer itemOrder;

    private MealPlanItemLinkState linkState;
    private String snapshotName;
    private String groceryName;
    private FoodPreparationState preparationMethod;
    private String snapshotDescription;
    private String shortPreparationState;
    private MealPlanNutritionSnapshotDto snapshotNutrition;
    private java.util.List<String> allergens;
    private java.util.List<String> warnings;
    private java.util.List<String> assumptions;
    private MealPlanWorkoutRelation workoutRelation;
    private Long sourceAiRequestId;
    private String schemaVersion;
    private String promptVersion;
}
