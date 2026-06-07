package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Recipe diary log with nutrition snapshot captured at log time.")
public class RecipeLogDto {
    private Long id;
    private Long recipeId;
    private String recipeName;
    private Double servingGrams;
    private Double servingCount;
    private String mealType;
    private LocalDateTime logDate;
    private Double snapshotCalories;
    private Double snapshotProtein;
    private Double snapshotCarbs;
    private Double snapshotFat;
    private Double snapshotFiber;
    private Double snapshotSugar;
    private Double snapshotSodium;
}
