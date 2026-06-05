package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Daily fasting trend point for history charts.")
public class FastingDailyTrendDto {
    @Schema(description = "Trend date.", example = "2026-06-05")
    private LocalDate date;
    @Schema(description = "Number of fasting sessions assigned to this date.", example = "1")
    private Integer sessionCount;
    @Schema(description = "Number of completed fasting sessions assigned to this date.", example = "1")
    private Integer completedSessionCount;
    @Schema(description = "Number of completed sessions that reached the target.", example = "1")
    private Integer targetReachedSessionCount;
    @Schema(description = "Total completed fasting minutes for this date.", example = "960")
    private Integer totalCompletedMinutes;
}
