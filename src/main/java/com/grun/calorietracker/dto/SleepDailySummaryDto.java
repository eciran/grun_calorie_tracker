package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.SleepStageType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SleepDailySummaryDto {
    private LocalDate date;
    private int targetMinutes;
    private int totalSleepMinutes;
    private int sessionCount;
    private Double averageQualityScore;
    private boolean targetReached;
    private Map<SleepStageType, Integer> stageMinutes;
    private List<SleepSessionDto> sessions;
}
