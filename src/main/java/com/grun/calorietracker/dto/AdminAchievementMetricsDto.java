package com.grun.calorietracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AdminAchievementMetricsDto {
    private List<String> metricKeys;
}
