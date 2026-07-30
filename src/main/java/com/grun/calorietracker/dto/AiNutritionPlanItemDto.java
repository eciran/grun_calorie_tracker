package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.MealPlanWorkoutRelation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiNutritionPlanItemDto {
    @NotBlank
    @Size(max = 255)
    private String displayName;

    @NotBlank
    @Size(max = 160)
    private String groceryName;

    @NotNull
    private FoodPreparationState preparationMethod;

    @Size(max = 1000)
    private String description;

    @NotNull
    @Positive
    private Double quantity;

    @NotNull
    private FoodPortionUnit unit;

    @NotNull
    @Valid
    private MealPlanNutritionSnapshotDto nutrition;

    @Size(max = 20)
    private List<@Size(max = 120) String> allergens = new ArrayList<>();

    @Size(max = 20)
    private List<@Size(max = 300) String> warnings = new ArrayList<>();

    @Size(max = 20)
    private List<@Size(max = 300) String> assumptions = new ArrayList<>();

    @Size(max = 120)
    private String shortPreparationState;

    private MealPlanWorkoutRelation workoutRelation = MealPlanWorkoutRelation.NONE;
}