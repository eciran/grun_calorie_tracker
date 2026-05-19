package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.GoalType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

// All comments are in English as requested in the project rules.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "User goal input and stored goal values.")
public class UserGoalDto {

    @Schema(description = "User goal id.", example = "1")
    private Long id;

    @NotNull(message = "{validation.user-goal.target-weight.required}")
    @Min(value = 30, message = "{validation.user-goal.target-weight.min}")
    @Schema(description = "Target body weight in kilograms.", example = "78.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double targetWeight;

    @NotNull(message = "{validation.user-goal.daily-calorie-goal.required}")
    @Min(value = 1000, message = "{validation.user-goal.daily-calorie-goal.min}")
    @Schema(description = "Daily calorie target.", example = "2300", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer dailyCalorieGoal;

    @NotNull(message = "{validation.user-goal.daily-protein-goal.required}")
    @Min(value = 0, message = "{validation.user-goal.daily-protein-goal.min}")
    @Schema(description = "Daily protein target in grams.", example = "160.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double dailyProteinGoal;

    @NotNull(message = "{validation.user-goal.daily-fat-goal.required}")
    @Min(value = 0, message = "{validation.user-goal.daily-fat-goal.min}")
    @Schema(description = "Daily fat target in grams.", example = "70.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double dailyFatGoal;

    @NotNull(message = "{validation.user-goal.daily-carb-goal.required}")
    @Min(value = 0, message = "{validation.user-goal.daily-carb-goal.min}")
    @Schema(description = "Daily carbohydrate target in grams.", example = "250.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double dailyCarbGoal;

    @Schema(description = "Target weekly weight change in kilograms.", example = "-0.5")
    private Double weeklyWeightChangeTargetKg;

    @NotNull(message = "{validation.user-goal.goal-type.required}")
    @Schema(description = "User's goal type.", example = "LOSE_WEIGHT", requiredMode = Schema.RequiredMode.REQUIRED)
    private GoalType goalType;

    @NotNull(message = "{validation.user-goal.activity-level.required}")
    @Schema(description = "User's activity level.", example = "MODERATE", requiredMode = Schema.RequiredMode.REQUIRED)
    private ActivityLevel activityLevel;

    @Schema(description = "Goal creation date and time.", example = "2026-05-11T12:00:00")
    private LocalDateTime createdAt;
}
