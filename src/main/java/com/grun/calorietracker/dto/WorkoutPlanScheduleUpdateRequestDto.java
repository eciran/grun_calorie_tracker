package com.grun.calorietracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkoutPlanScheduleUpdateRequestDto {
    @NotEmpty
    @Size(max = 7)
    @Valid
    private List<WorkoutPlanScheduleSessionRequestDto> sessions = new ArrayList<>();
}
