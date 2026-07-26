package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.OnboardingStatus;
import com.grun.calorietracker.enums.OnboardingStep;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resumable onboarding state for the authenticated user.")
public class OnboardingStateDto {
    private OnboardingStatus status;
    private OnboardingStep currentStep;
    private List<OnboardingStep> completedSteps;
    private boolean canComplete;
    private OnboardingProfileStepDto profile;
    private OnboardingPreferencesStepDto preferences;
    private OnboardingGoalStepDto goal;
    private OnboardingNutritionStepDto nutrition;
    private OnboardingFitnessPreferenceStepDto fitnessPreference;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
