package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.BodyCompositionFocus;
import com.grun.calorietracker.enums.GoalType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Goal inputs collected during onboarding.")
public class OnboardingGoalStepDto {

    @Min(value = 30, message = "{validation.user-goal.target-weight.min}")
    @Max(value = 300, message = "{validation.user-profile.weight.max}")
    private Double targetWeight;

    private Double weeklyWeightChangeTargetKg;

    private GoalType goalType;

    private ActivityLevel activityLevel;

    private BodyCompositionFocus bodyCompositionFocus;

    public OnboardingGoalStepDto(Double targetWeight, Double weeklyWeightChangeTargetKg,
                                 GoalType goalType, ActivityLevel activityLevel) {
        this(targetWeight, weeklyWeightChangeTargetKg, goalType, activityLevel, BodyCompositionFocus.STANDARD);
    }

    public GoalCalculationRequestDto toGoalCalculationRequestDto() {
        return new GoalCalculationRequestDto(targetWeight, weeklyWeightChangeTargetKg, goalType, activityLevel,
                bodyCompositionFocus);
    }
}
