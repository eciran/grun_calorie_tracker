package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiDraftRejectReason;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiRequestHistoryDto {
    private Long id;
    private AiRequestType requestType;
    private AiProvider provider;
    private String model;
    private AiRequestStatus status;
    private Boolean quotaConsumed;
    private Long latencyMs;
    private Integer totalTokens;
    private Double estimatedCost;
    private String costCurrency;
    private AiDraftRejectReason rejectionReason;
    private Boolean hasRejectionFeedback;
    private LocalDateTime createdAt;
}
