package com.grun.calorietracker.dto;

import lombok.Data;

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
}
