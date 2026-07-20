package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.WorkoutSessionIntensity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutNutritionSessionContextDto {
    private LocalDate date;
    private LocalTime startTime;
    private Integer durationMinutes;
    private WorkoutSessionIntensity intensity;
    private String focus;
    private Integer exerciseCount;
    private Integer totalWorkingSets;
    private Integer totalExerciseDurationMinutes;
}
