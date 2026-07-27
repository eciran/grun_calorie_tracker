package com.grun.calorietracker.dto;

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
    private String activePromptVersion;
    private boolean rollbackAvailable;
    private String updatedBy;
    private LocalDateTime updatedAt;
}