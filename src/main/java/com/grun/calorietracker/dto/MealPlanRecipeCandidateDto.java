package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.MarketRegion;
import lombok.Data;

import java.util.List;

@Data
public class MealPlanRecipeCandidateDto {
    private Long recipeId;
    private String name;
    private String description;
    private String mealType;
    private String imageUrl;
    private MarketRegion marketRegion;
    private String language;
    private boolean ownedByUser;
    private boolean exactNameMatch;
    private int matchScore;
    private List<String> matchReasons;
    private Integer servingCount;
    private Double defaultServingGrams;
    private MealPlanNutritionSnapshotDto perServingNutrition;
}