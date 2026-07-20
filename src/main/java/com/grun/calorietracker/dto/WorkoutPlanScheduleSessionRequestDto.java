package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.WorkoutSessionIntensity;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class WorkoutPlanScheduleSessionRequestDto {
    @NotNull
    @Min(0)
    private Integer dayIndex;

    @NotNull
    private LocalDate scheduledDate;

    private LocalTime scheduledStartTime;

    @NotNull
    private WorkoutSessionIntensity intensity;
}
