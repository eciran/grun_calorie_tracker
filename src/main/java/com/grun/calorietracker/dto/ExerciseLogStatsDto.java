package com.grun.calorietracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExerciseLogStatsDto {
    private LocalDate date;
    private Double totalCaloriesBurned;
}
