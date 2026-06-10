package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AchievementCategory;
import com.grun.calorietracker.enums.AchievementTier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Achievement definition with current user's progress.")
public class AchievementDto {
    private String code;
    private String title;
    private String description;
    private String metricKey;
    private AchievementCategory category;
    private AchievementTier tier;
    private Integer progressValue;
    private Integer targetValue;
    private Integer progressPercent;
    private Boolean unlocked;
    private LocalDateTime unlockedAt;
}
