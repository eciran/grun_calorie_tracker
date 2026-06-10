package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AchievementCategory;
import com.grun.calorietracker.enums.AchievementTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAchievementDefinitionDto {
    private Long id;
    private String code;
    private String title;
    private String description;
    private String metricKey;
    private AchievementCategory category;
    private AchievementTier tier;
    private Integer targetValue;
    private Boolean active;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
