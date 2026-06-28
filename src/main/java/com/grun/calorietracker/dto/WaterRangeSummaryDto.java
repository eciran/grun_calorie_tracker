package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "Range water tracking summary for charts and hydration trends.")
public class WaterRangeSummaryDto {
    @Schema(description = "Range start date.", example = "2026-06-01")
    private LocalDate startDate;

    @Schema(description = "Range end date.", example = "2026-06-07")
    private LocalDate endDate;

    @Schema(description = "Configured daily water target in milliliters.", example = "2500")
    private Integer targetMl;

    @Schema(description = "Total water consumed during the range.", example = "14000")
    private Integer totalMl;

    @Schema(description = "Average daily water intake during the range.", example = "2000.0")
    private Double averageMl;

    @Schema(description = "Highest daily water intake during the range.", example = "2750")
    private Integer bestMl;

    @Schema(description = "Number of days where the target was reached.", example = "4")
    private Integer targetHitDays;

    @Schema(description = "Number of days included in the range.", example = "7")
    private Integer dayCount;

    @Schema(description = "Daily chart points for the selected range.")
    private List<WaterDailyTrendDto> days;
}