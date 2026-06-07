package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.RecipeVisibility;
import com.grun.calorietracker.enums.VerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "Admin recipe detail response.")
public class AdminRecipeDto {
    private Long id;
    private Long ownerUserId;
    private String ownerEmail;
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
    private String imageReviewNote;
    private String imageReviewedBy;
    private LocalDateTime imageReviewedAt;
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
    private Long savedCount;
    private Long favoriteCount;
    private Long ratingCount;
    private Double averageRating;
    private Boolean archived;
    private Integer ingredientCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<RecipeIngredientDto> ingredients;
}
