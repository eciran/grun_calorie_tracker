package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.MarketRegion;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NextMealAiRecipePrefillDto {
    private String mealType;
    private MarketRegion marketRegion;
    private String language;
    private Integer servingCount;
    private Double targetCaloriesPerServing;
    private List<String> dietaryPreferences = new ArrayList<>();
    private List<String> excludedIngredients = new ArrayList<>();
}
