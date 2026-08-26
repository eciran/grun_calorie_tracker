package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.GoalCalculationMode;
import com.grun.calorietracker.enums.GoalControlledStrategy;
import com.grun.calorietracker.enums.GoalType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdvancedGoalRequestDto {
    @NotNull @Min(30) private Double targetWeight;
    private Double weeklyWeightChangeTargetKg;
    @NotNull private GoalType goalType;
    @NotNull private ActivityLevel activityLevel;
    @NotNull private GoalCalculationMode mode;
    private GoalControlledStrategy strategy;
    @Min(0) private Double proteinGrams;
    @Min(0) private Double carbGrams;
    @Min(0) private Double fatGrams;
    private boolean warningsAcknowledged;
    private Long expectedGoalVersion;
    private String expectedProfileVersion;
    private String previewToken;

    public GoalCalculationRequestDto automaticRequest() {
        return new GoalCalculationRequestDto(targetWeight, weeklyWeightChangeTargetKg, goalType, activityLevel);
    }
}
