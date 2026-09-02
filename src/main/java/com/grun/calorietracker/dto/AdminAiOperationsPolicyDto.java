package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AiProvider;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminAiOperationsPolicyDto {
    private Long version;
    private boolean circuitOpen;
    private double failureRateThreshold;
    private double rejectionRateThreshold;
    private long maxTokensPer24Hours;
    private double maxCostPer24Hours;
    private String costCurrency;
    private String activeModel;
    private String activePhotoModel;
    private AiProvider activePhotoProvider;
    private double photoInputTokenCostPer1m;
    private double photoOutputTokenCostPer1m;
    private String photoCostCurrency;
    private String activePromptVersion;
    private boolean rollbackAvailable;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
