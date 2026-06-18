package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "Range step tracking summary.")
public class StepRangeSummaryDto {
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer targetSteps;
    private Integer totalSteps;
    private Double averageSteps;
    private Integer bestSteps;
    private Integer targetHitDays;
    private Integer dayCount;
    private List<StepDailySummaryDto> days;
}
