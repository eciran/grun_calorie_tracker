package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.MealPlanItemType;
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
    private Double portionSize;
    private FoodPortionUnit portionUnit;
    private Double servingCount;
    private Integer itemOrder;
}
