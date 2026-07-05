package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.WorkoutPlanStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkoutPlanDto {
    private Long id;
    private String name;
    private WorkoutPlanStatus status;
    private Long sourceAiRequestId;
    private AiWorkoutPlanDraftResponseDto plan;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
