package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.WeeklyWorkoutFrequency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Optional planned workout frequency collected during onboarding.")
public class OnboardingFitnessPreferenceStepDto {

    @Schema(description = "Planned workout days. Null means the optional question was skipped.")
    private WeeklyWorkoutFrequency weeklyWorkoutFrequency;

    @AssertTrue(message = "Fitness preference selection must be confirmed or explicitly skipped.")
    private boolean selectionConfirmed;
}
