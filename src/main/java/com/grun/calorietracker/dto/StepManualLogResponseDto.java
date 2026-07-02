package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Manual step metric response.")
public class StepManualLogResponseDto {
    private Long id;
    private Integer steps;
    private Double distanceMeters;
    private Double caloriesBurned;
    private LocalDateTime recordedAt;

    public StepManualLogResponseDto(Long id, Integer steps) {
        this.id = id;
        this.steps = steps;
    }
}