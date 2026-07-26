package com.grun.calorietracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class SleepGoalRequestDto {
    @NotNull
    @Min(60)
    @Max(960)
    private Integer targetMinutes;
    private LocalTime preferredBedtime;
    private LocalTime preferredWakeTime;
}
