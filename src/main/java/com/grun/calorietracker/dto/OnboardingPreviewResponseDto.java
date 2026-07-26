package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Non-persistent calorie and macro preview for the current onboarding draft.")
public class OnboardingPreviewResponseDto {
    private GoalCalculationResponse calculation;
    private OnboardingStateDto state;
}
