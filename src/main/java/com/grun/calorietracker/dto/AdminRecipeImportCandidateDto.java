package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.RecipeImportCandidateStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminRecipeImportCandidateDto {
    private Long id;
    private String batchId;
    private String sourceKey;
    private String sourceTitle;
    private String sourceUrl;
    private String sourceRevisionUrl;
    private String license;
    private String recommendedImportStatus;
    private RecipeImportCandidateStatus status;
    private String recipeName;
    private String mealType;
    private MarketRegion marketRegion;
    private String language;
    private String imageUrl;
    private Integer ingredientCount;
    private Integer unresolvedIngredientCount;
    private String validationIssues;
    private Long createdRecipeId;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<IngredientDto> ingredients;

    @Data
    public static class IngredientDto {
        private Integer index;
        private Long foodItemId;
        private String ingredientName;
        private String imageUrl;
        private Double portionSize;
        private FoodPortionUnit portionUnit;
        private Double estimatedGrams;
    }
}