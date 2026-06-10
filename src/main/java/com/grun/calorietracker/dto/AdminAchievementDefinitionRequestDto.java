package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AchievementCategory;
import com.grun.calorietracker.enums.AchievementTier;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminAchievementDefinitionRequestDto {
    @NotBlank
    @Size(max = 80)
    @Pattern(regexp = "^[A-Z0-9_]+$")
    private String code;

    @NotBlank
    @Size(max = 120)
    private String title;

    @NotBlank
    @Size(max = 500)
    private String description;

    @NotBlank
    @Size(max = 80)
    private String metricKey;

    @NotNull
    private AchievementCategory category;

    @NotNull
    private AchievementTier tier;

    @NotNull
    @Min(1)
    @Max(1_000_000)
    private Integer targetValue;

    @NotNull
    private Boolean active;

    @NotNull
    @Min(0)
    private Integer sortOrder;
}
