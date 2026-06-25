package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.RecipeCategory;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.VerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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
    private ImageSource imageSource;
    private ImageStatus imageStatus;
    private Double totalYieldGrams;
    private Double defaultServingGrams;
    private Integer servingCount;
    private Double calories;
    private Double protein;
    private Double carbs;
    private Double fat;
    private Double fiber;
    private Double sugar;
    private Double saturatedFat;
    private Double sodium;
    private Double potassium;
    private Double cholesterol;
    private Double calcium;
    private Double iron;
    private Double magnesium;
    private Double zinc;
    private Double vitaminA;
    private Double vitaminC;
    private Double vitaminD;
    private Double vitaminE;
    private Double vitaminB12;
    private RecipeNutritionDto totalNutrition;
    private RecipeNutritionDto perServingNutrition;
    private RecipeNutritionDto per100gNutrition;
    private Boolean savedByMe;
    private Boolean favoriteByMe;
    private Integer myRating;
    private Long savedCount;
    private Long favoriteCount;
    private Long ratingCount;
    private Double averageRating;
    private Set<RecipeCategory> categories;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<RecipeIngredientDto> ingredients;
    private List<RecipeStepDto> cookingSteps;
}
