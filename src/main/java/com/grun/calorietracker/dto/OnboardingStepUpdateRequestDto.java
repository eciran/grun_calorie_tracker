package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;

@Data
@Schema(description = "Step payload. Send only the object matching the step in the request path.")
public class OnboardingStepUpdateRequestDto {

    @Valid
    private OnboardingProfileStepDto profile;

    @Valid
    private OnboardingPreferencesStepDto preferences;

    @Valid
    private OnboardingGoalStepDto goal;

    @Valid
    private OnboardingNutritionStepDto nutrition;

    @Valid
    private OnboardingFitnessPreferenceStepDto fitnessPreference;
}
