package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "Daily fasting summary.")
public class FastingDailySummaryDto {
    @Schema(description = "Summary date.", example = "2026-06-05")
    private LocalDate date;
    @Schema(description = "Current active fasting plan, if one exists.")
    private FastingPlanDto plan;
    @Schema(description = "Currently active fasting session, if one exists.")
    private FastingSessionDto activeSession;
    @Schema(description = "Sessions assigned to this date.")
    private List<FastingSessionDto> sessions;
    @Schema(description = "Total completed fasting minutes for this date.", example = "960")
    private Integer totalCompletedMinutes;
    @Schema(description = "Active session elapsed minutes.", example = "120")
    private Integer activeElapsedMinutes;
    @Schema(description = "Active session remaining minutes.", example = "840")
    private Integer activeRemainingMinutes;
    @Schema(description = "Number of consecutive days with completed target fasting.", example = "3")
    private Integer currentStreakDays;
}
