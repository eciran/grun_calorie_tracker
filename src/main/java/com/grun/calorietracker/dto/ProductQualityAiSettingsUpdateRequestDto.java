package com.grun.calorietracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductQualityAiSettingsUpdateRequestDto {
    private Boolean enabled;

    @Min(1)
    @Max(25)
    private Integer maxProductsPerRun;

    @Min(1)
    @Max(10000)
    private Integer dailyProductLimit;

    @Min(1)
    @Max(100000)
    private Integer monthlyProductLimit;

    private Boolean forceRescanAllowed;

    @Size(max = 1000)
    private String adminNote;
}