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

    @NotNull(message = "Target weight is required")
    @Min(value = 30, message = "Target weight must be at least 30 kg")
    @Schema(description = "Target body weight in kilograms.", example = "78.0")
    private Double targetWeight;

    @NotNull(message = "Daily calorie goal is required")
    @Min(value = 1000, message = "Calories must be at least 1000")
    @Schema(description = "Daily calorie target.", example = "2300")
    private Integer dailyCalorieGoal;

    @NotNull(message = "Daily protein goal is required")
    @Min(value = 0, message = "Protein cannot be negative")
    @Schema(description = "Daily protein target in grams.", example = "160.0")
    private Double dailyProteinGoal;

    @NotNull(message = "Daily fat goal is required")
    @Min(value = 0, message = "Fat cannot be negative")
    @Schema(description = "Daily fat target in grams.", example = "70.0")
    private Double dailyFatGoal;

    @NotNull(message = "Daily carb goal is required")
    @Min(value = 0, message = "Carbs cannot be negative")
    @Schema(description = "Daily carbohydrate target in grams.", example = "250.0")
    private Double dailyCarbGoal;

    @Schema(description = "Target weekly weight change in kilograms.", example = "-0.5")
    private Double weeklyWeightChangeTargetKg;

    @NotNull(message = "Goal type is required")
    @Schema(description = "User's goal type.", example = "LOSE_WEIGHT")
    private GoalType goalType;

    @NotNull(message = "Activity level is required")
    @Schema(description = "User's activity level.", example = "MODERATE")
    private ActivityLevel activityLevel;

    @Schema(description = "Goal creation date and time.", example = "2026-05-11T12:00:00")
    private LocalDateTime createdAt;
}
