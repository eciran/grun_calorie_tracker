package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Daily water intake trend point for charts and range summaries.")
public class WaterDailyTrendDto {
    @Schema(description = "Diary date.", example = "2026-06-05")
    private LocalDate date;

    @Schema(description = "Total water consumed in milliliters.", example = "1250")
    private Integer totalMl;

    @Schema(description = "Configured daily water target in milliliters.", example = "2500")
    private Integer targetMl;

    @Schema(description = "Remaining amount to reach the target. Zero when target is reached or exceeded.", example = "1250")
    private Integer remainingMl;

    @Schema(description = "Target completion percentage capped at 100.", example = "50.0")
    private Double progressPercent;

    @Schema(description = "Whether the daily water target was reached.", example = "false")
    private Boolean targetReached;
}