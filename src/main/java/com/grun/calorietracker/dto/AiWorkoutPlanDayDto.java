package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.WorkoutSessionIntensity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "One day inside an AI workout plan draft.")
public class AiWorkoutPlanDayDto {
    @NotBlank
    @Schema(description = "Day label.", example = "Day 1")
    private String dayLabel;

    @Schema(description = "Session focus.", example = "Upper body strength")
    private String focus;

    @Schema(description = "User-confirmed calendar date. AI generation leaves this empty until the schedule is saved.")
    private LocalDate scheduledDate;

    @Schema(description = "Optional user-confirmed approximate workout start time.")
    private LocalTime scheduledStartTime;

    @Schema(description = "User-confirmed session intensity used by workout-aligned nutrition planning.")
    private WorkoutSessionIntensity sessionIntensity;

    @Schema(description = "Estimated total session duration in minutes including warm-up and cool-down.", example = "45")
    private Integer estimatedDurationMinutes;

    @Schema(description = "Warm-up guidance before the workout day.", example = "5 minutes light cardio, shoulder circles, and bodyweight squats.")
    private String warmup;

    @Schema(description = "Cool-down guidance after the workout day.", example = "Walk for 3 minutes, then stretch chest, shoulders, and hips.")
    private String cooldown;

    @Size(max = 20)
    @Valid
    private List<AiWorkoutPlanExerciseDto> exercises = new ArrayList<>();
}

