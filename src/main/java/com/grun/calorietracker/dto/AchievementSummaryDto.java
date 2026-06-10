package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authenticated user's achievement summary.")
public class AchievementSummaryDto {
    private Integer total;
    private Integer unlocked;
    private Integer completionPercent;
    private List<AchievementDto> achievements;
}
