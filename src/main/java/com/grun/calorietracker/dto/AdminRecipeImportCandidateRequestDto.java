package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.RecipeAllergen;
import com.grun.calorietracker.enums.RecipeCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Schema(description = "One source recipe candidate from an import JSON batch.")
public class AdminRecipeImportCandidateRequestDto {
    @NotBlank
    @Size(max = 220)
    private String sourceKey;

    @Size(max = 255)
    private String sourceTitle;

    @Size(max = 1000)
    private String sourceUrl;

    @Size(max = 1000)
    private String sourceRevisionUrl;

    @Size(max = 120)
    private String license;

    @Size(max = 80)
    private String recommendedImportStatus;

    @Valid
    private RecipePayload recipe;

    @Data
    public static class RecipePayload {
        @NotBlank
        @Size(max = 160)
        private String name;

        @Size(max = 1000)
        private String description;

        @Size(max = 40)
        private String mealType;

        private MarketRegion marketRegion;

        @Size(max = 12)
        private String language;

        @Size(max = 1000)
        private String imageUrl;

        private Double totalYieldGrams;
        private Double defaultServingGrams;
        private Integer servingCount;
        private Set<RecipeCategory> categories;
        private Set<RecipeAllergen> allergens;

        @Valid
        private List<IngredientPayload> ingredients;

        @Valid
        @Size(max = 30)
        private List<CookingStepPayload> cookingSteps;
    }

    @Data
    public static class IngredientPayload {
        private Long foodItemId;

        @Size(max = 255)
        private String ingredientName;

        @Size(max = 1000)
        private String imageUrl;

        private Double portionSize;
        private FoodPortionUnit portionUnit;
        private Double estimatedGrams;
    }

    @Data
    public static class CookingStepPayload {
        @Size(max = 1000)
        private String instruction;
    }
}
