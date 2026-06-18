package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Manual step metric creation response.")
public class StepManualLogResponseDto {
    private Long id;
    private Integer steps;
}
