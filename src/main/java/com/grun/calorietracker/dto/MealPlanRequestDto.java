package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.NutritionPlanGenerationMode;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "Weekly meal plan request with day/meal items.")
public class MealPlanRequestDto {

    @NotBlank
    @Schema(example = "High protein week")
    private String name;

    @NotNull
    @Schema(example = "2026-06-15")
    private LocalDate startDate;

    @NotNull
    @Schema(example = "2026-06-21")
    private LocalDate endDate;


    @Schema(description = "Optional explicit nutrition-plan mode. Omit for a manual meal plan.", example = "GENERAL")
    private NutritionPlanGenerationMode generationMode;

    @Schema(description = "Owned active workout plan required only for WORKOUT_ALIGNED mode.", example = "42")
    private Long workoutPlanId;

    @Valid
    private List<MealPlanItemRequestDto> items = new ArrayList<>();
}
