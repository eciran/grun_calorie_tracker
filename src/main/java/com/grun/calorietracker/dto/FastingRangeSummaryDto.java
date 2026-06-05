package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "Fasting history summary for a date range.")
public class FastingRangeSummaryDto {
    @Schema(description = "Range start date.", example = "2026-06-01")
    private LocalDate startDate;
    @Schema(description = "Range end date.", example = "2026-06-07")
    private LocalDate endDate;
    @Schema(description = "Number of calendar days in the range.", example = "7")
    private Integer dayCount;
    @Schema(description = "Total fasting sessions in the range.", example = "6")
    private Integer sessionCount;
    @Schema(description = "Completed fasting sessions in the range.", example = "5")
    private Integer completedSessionCount;
    @Schema(description = "Completed sessions that reached the target.", example = "4")
    private Integer targetReachedSessionCount;
    @Schema(description = "Total completed fasting minutes in the range.", example = "4800")
    private Integer totalCompletedMinutes;
    @Schema(description = "Average completed fasting minutes per completed session.", example = "960")
    private Integer averageCompletedMinutes;
    @Schema(description = "Best completed fasting session duration in minutes.", example = "1020")
    private Integer bestSessionMinutes;
    @Schema(description = "Target success rate for completed sessions between 0 and 1.", example = "0.8")
    private Double targetSuccessRate;
    @Schema(description = "Current streak ending at the range end date.", example = "3")
    private Integer currentStreakDays;
    @Schema(description = "Daily trend points for charts.")
    private List<FastingDailyTrendDto> dailyTrends;
}
