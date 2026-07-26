package com.grun.calorietracker.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class SleepWeeklySummaryDto {
    private LocalDate startDate;
    private LocalDate endDate;
    private int targetMinutes;
    private int loggedDays;
    private int targetHitDays;
    private Double averageSleepMinutesOnLoggedDays;
    private Double averageQualityScore;
    private List<SleepDailySummaryDto> days;
}
