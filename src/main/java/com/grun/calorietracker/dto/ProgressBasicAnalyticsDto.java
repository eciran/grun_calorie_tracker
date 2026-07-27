package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Plan-independent period summary. Excludes projections, comparisons, relationships and advanced interpretations.")
public class ProgressBasicAnalyticsDto {
    private ProgressAnalyticsDto.Range range;
    private ProgressAnalyticsDto.DataCoverage dataCoverage;
    private ProgressAnalyticsDto.Nutrition nutrition;
    private ProgressAnalyticsDto.Activity activity;
    private ProgressAnalyticsDto.Habits habits;

    public static ProgressBasicAnalyticsDto from(ProgressAnalyticsDto analytics) {
        return ProgressBasicAnalyticsDto.builder()
                .range(analytics.getRange())
                .dataCoverage(analytics.getDataCoverage())
                .nutrition(analytics.getNutrition())
                .activity(analytics.getActivity())
                .habits(analytics.getHabits())
                .build();
    }
}