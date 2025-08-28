package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.ActivityLevel;
import com.grun.calorietracker.enums.GoalType;
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
public class UserGoalDto {

    private Long id;

    @NotNull(message = "Target weight is required")
    @Min(value = 30, message = "Target weight must be at least 30 kg")
    private Double targetWeight;

    @NotNull(message = "Daily calorie goal is required")
    @Min(value = 1000, message = "Calories must be at least 1000")
    private Integer dailyCalorieGoal;

    @NotNull(message = "Daily protein goal is required")
    @Min(value = 0, message = "Protein cannot be negative")
    private Double dailyProteinGoal;

    @NotNull(message = "Daily fat goal is required")
    @Min(value = 0, message = "Fat cannot be negative")
    private Double dailyFatGoal;

    @NotNull(message = "Daily carb goal is required")
    @Min(value = 0, message = "Carbs cannot be negative")
    private Double dailyCarbGoal;

    private Double weeklyWeightChangeTargetKg;

    @NotNull(message = "Goal type is required")
    private GoalType goalType;

    @NotNull(message = "Activity level is required")
    private ActivityLevel activityLevel;

    private LocalDateTime createdAt;
}
