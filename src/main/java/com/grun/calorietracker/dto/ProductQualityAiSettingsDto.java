package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Admin-managed AI product quality validation settings and current quota usage.")
public class ProductQualityAiSettingsDto {
    private boolean enabled;
    private int maxProductsPerRun;
    private int dailyProductLimit;
    private int monthlyProductLimit;
    private boolean forceRescanAllowed;
    private int usedToday;
    private int usedThisMonth;
    private int remainingToday;
    private int remainingThisMonth;
    private String adminNote;
    private LocalDateTime updatedAt;
    private String updatedBy;
}