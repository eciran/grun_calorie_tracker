package com.grun.calorietracker.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalTime;

@Data
@Builder
public class SleepGoalDto {
    private int targetMinutes;
    private LocalTime preferredBedtime;
    private LocalTime preferredWakeTime;
    private boolean persisted;
}
