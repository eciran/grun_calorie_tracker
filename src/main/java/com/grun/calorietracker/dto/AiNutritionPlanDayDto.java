package com.grun.calorietracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.grun.calorietracker.enums.NutritionPlanDayType;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiNutritionPlanDayDto {
    @NotNull
    private LocalDate date;

    private NutritionPlanDayType dayType;

    @NotEmpty
    @Size(max = 6)
    @Valid
    private List<AiNutritionPlanMealDto> meals = new ArrayList<>();

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(hidden = true)
    @Valid
    private MealPlanNutritionSnapshotDto dailyMicronutrients;

    @Valid
    private MealPlanNutritionSnapshotDto totalNutrition;
}