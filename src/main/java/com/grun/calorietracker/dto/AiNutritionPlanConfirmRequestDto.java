package com.grun.calorietracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiNutritionPlanConfirmRequestDto {
    @NotNull
    @Valid
    private AiNutritionPlanDraftResponseDto draft;
}