package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.VerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "Recipe owned by a user or prepared for the future public recipe catalog.")
public class RecipeDto {
    private Long id;
    private String name;
    private String description;
    private String mealType;
    private RecipeVisibility visibility;
    private VerificationStatus verificationStatus;
    private MarketRegion marketRegion;
    private String language;
    private String imageUrl;
    private Double totalYieldGrams;
    private Double defaultServingGrams;
    private Integer servingCount;
    private Double calories;
    private Double protein;
    private Double carbs;
    private Double fat;
    private Double fiber;
    private Double sugar;
    private Double sodium;
    private RecipeNutritionDto totalNutrition;
    private RecipeNutritionDto perServingNutrition;
    private RecipeNutritionDto per100gNutrition;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<RecipeIngredientDto> ingredients;
}
