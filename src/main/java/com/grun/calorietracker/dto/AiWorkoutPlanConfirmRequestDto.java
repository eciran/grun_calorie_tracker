package com.grun.calorietracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiWorkoutPlanConfirmRequestDto {
    @NotNull
    @Valid
    private AiWorkoutPlanDraftResponseDto plan;
}
