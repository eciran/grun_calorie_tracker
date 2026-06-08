package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Request for logging a recipe to the food diary.")
public class RecipeLogRequestDto {
    @Positive
    @Schema(description = "Consumed recipe amount in grams. If omitted, servingCount and recipe defaultServingGrams are used.", example = "300.0")
    private Double servingGrams;

    @Positive
    @Schema(description = "Consumed serving count. Used when servingGrams is omitted.", example = "1.0")
    private Double servingCount;

    @NotBlank
    @Pattern(regexp = "(?i)BREAKFAST|LUNCH|DINNER|SNACK")
    @Schema(description = "Meal category.", example = "LUNCH", allowableValues = {"BREAKFAST", "LUNCH", "DINNER", "SNACK"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String mealType;

    @NotNull
    @Schema(description = "Date and time when the recipe was consumed.", example = "2026-06-06T12:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime logDate;
}
