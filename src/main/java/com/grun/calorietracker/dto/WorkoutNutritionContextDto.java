package com.grun.calorietracker.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class WorkoutNutritionContextDto {
    private Long workoutPlanId;
    private String workoutPlanName;
    private String scheduleVersion;
    private LocalDateTime scheduleUpdatedAt;
    private List<WorkoutNutritionSessionContextDto> sessions = new ArrayList<>();
}
