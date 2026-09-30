package com.grun.calorietracker.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiRecipeQualityReviewRequestDto {
    private Long recipeId;
    private String name;
    private String description;
    private String language;
    private String marketRegion;
    private Double totalYieldGrams;
    private Double defaultServingGrams;
    private Integer servingCount;
    private Nutrition storedNutrition;
    private Nutrition recalculatedNutrition;
    private List<Ingredient> ingredients = new ArrayList<>();
    private List<String> cookingSteps = new ArrayList<>();
    private List<String> allergens = new ArrayList<>();
    private List<String> deterministicIssues = new ArrayList<>();

    @Data
    public static class Nutrition {
        private Double calories;
        private Double protein;
        private Double carbs;
        private Double fat;
        private Double fiber;
        private Double sugar;
    }

    @Data
    public static class Ingredient {
        private Long foodItemId;
        private String name;
        private Double portionSize;
        private String portionUnit;
        private Double normalizedGrams;
        private Double normalizedMilliliters;
        private String verificationStatus;
        private Integer qualityScore;
    }
}
