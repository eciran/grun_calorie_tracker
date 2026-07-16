package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.MealPlanItemLinkState;
import lombok.Data;

@Data
public class MealPlanRecipeLinkDto {
    private Long mealPlanId;
    private Long mealPlanItemId;
    private Long recipeId;
    private String recipeName;
    private MealPlanItemLinkState linkState;
    private boolean recipeNavigationAvailable;
    private boolean recipeOwnedByUser;
}