package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.MealPlanItemType;

import com.grun.calorietracker.enums.MealPlanWorkoutRelation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import jakarta.validation.Valid;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Meal planner item. Use either foodItemId for FOOD_ITEM or recipeId for RECIPE.")
public class MealPlanItemRequestDto {

    @NotNull
    @Schema(example = "2026-06-15")
    private LocalDate planDate;

    @NotBlank
    @Pattern(regexp = "(?i)BREAKFAST|LUNCH|DINNER|SNACK")
    @Schema(example = "DINNER", allowableValues = {"BREAKFAST", "LUNCH", "DINNER", "SNACK"})
    private String mealType;

    @NotNull
    @Schema(example = "RECIPE")
    private MealPlanItemType itemType;

    @Schema(example = "10")
    private Long foodItemId;

    @Schema(example = "25")
    private Long recipeId;

    @Positive
    @Schema(description = "Food item portion size. Required for FOOD_ITEM.", example = "150")
    private Double portionSize;

    @Schema(description = "Food item portion unit. Required for FOOD_ITEM.", example = "GRAM")
    private FoodPortionUnit portionUnit;

    @Positive
    @Schema(description = "Recipe serving count. Defaults to 1 for RECIPE.", example = "1")
    private Double servingCount;


    @Schema(description = "Required display name for AI_SNAPSHOT.", example = "Grilled Chicken With Rice")
    private String snapshotName;

    @Schema(description = "Purchasable base food name without cooking-only modifiers.", example = "Salmon")
    private String groceryName;

    @Schema(description = "Preparation method represented separately from the grocery name.", example = "BAKED")
    private FoodPreparationState preparationMethod;

    private String snapshotDescription;
    @Schema(description = "Short state only, not full cooking instructions.", example = "Grilled")
    private String shortPreparationState;

    @Valid
    private MealPlanNutritionSnapshotDto snapshotNutrition;

    private java.util.List<String> allergens;
    private java.util.List<String> warnings;
    private java.util.List<String> assumptions;
    private MealPlanWorkoutRelation workoutRelation;
}
