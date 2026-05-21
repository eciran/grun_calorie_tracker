package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Result returned after completing the mobile onboarding flow.")
public class OnboardingCompleteResponseDto {

    @Schema(description = "Updated user profile.")
    private UserProfileDto profile;

    @Schema(description = "Saved user goal with backend-calculated calorie and macro targets.")
    private UserGoalDto goal;

    @Schema(description = "Calculated calorie and macro target returned for immediate mobile UI rendering.")
    private GoalCalculationResponse calculation;

    @Schema(description = "Whether onboarding has enough data for the app's main tracking flow.", example = "true")
    private boolean onboardingCompleted;
}
