package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

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

    @Size(max = 20)
    @Valid
    private List<AiWorkoutPlanExerciseDto> exercises = new ArrayList<>();
}
