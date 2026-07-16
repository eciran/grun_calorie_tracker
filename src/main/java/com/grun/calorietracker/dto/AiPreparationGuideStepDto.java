package com.grun.calorietracker.dto;

import lombok.Data;

@Data
public class AiPreparationGuideStepDto {
    private Integer stepNumber;
    private String instruction;
    private Integer durationMinutes;
    private Integer temperatureCelsius;
}
