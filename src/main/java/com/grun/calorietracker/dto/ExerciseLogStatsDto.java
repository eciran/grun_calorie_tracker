package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Daily exercise statistics.")
public class ExerciseLogStatsDto {
    @Schema(description = "Statistics date.", example = "2026-05-11")
    private LocalDate date;

    @Schema(description = "Total calories burned on the date.", example = "520.0")
    private Double totalCaloriesBurned;
}
