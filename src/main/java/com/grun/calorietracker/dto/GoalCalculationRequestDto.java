package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.GoalType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Goal setup input used to calculate calorie and macro targets.")
public class GoalCalculationRequestDto {

    @NotNull(message = "{validation.user-goal.target-weight.required}")
    @Min(value = 30, message = "{validation.user-goal.target-weight.min}")
    @Schema(description = "Target body weight in kilograms.", example = "78.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double targetWeight;

    @Schema(
            description = "Target weekly weight change in kilograms. The backend normalizes the sign by goal type.",
            example = "0.5"
    )
    private Double weeklyWeightChangeTargetKg;

    @NotNull(message = "{validation.user-goal.goal-type.required}")
    @Schema(description = "User's goal type.", example = "LOSE_WEIGHT", requiredMode = Schema.RequiredMode.REQUIRED)
    private GoalType goalType;

    @NotNull(message = "{validation.user-goal.activity-level.required}")
    @Schema(description = "User's activity level.", example = "MODERATE", requiredMode = Schema.RequiredMode.REQUIRED)
    private ActivityLevel activityLevel;
}
