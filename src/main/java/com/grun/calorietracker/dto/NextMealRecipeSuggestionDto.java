package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.NextMealSuggestionReason;
import com.grun.calorietracker.enums.NextMealSuggestionStrategy;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NextMealRecipeSuggestionDto {
    private Long recipeId;
    private String name;
    private String imageUrl;
    private Double caloriesPerServing;
    private Double proteinPerServing;
    private Double carbsPerServing;
    private Double fatPerServing;
    private Integer matchScore;
    private NextMealSuggestionStrategy strategy;
    private List<NextMealSuggestionReason> reasonCodes = new ArrayList<>();
}
