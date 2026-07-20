package com.grun.calorietracker.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.grun.calorietracker.enums.AiDraftRejectReason;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiRequestHistoryDetailDto {
    private Long id;
    private AiRequestType requestType;
    private AiProvider provider;
    private String model;
    private String promptVersion;
    private AiRequestStatus status;
    private Boolean quotaConsumed;
    private Integer quotaConsumedAmount;
    private Integer quotaRefundedAmount;
    private Long latencyMs;
    private Integer totalTokens;
    private Double estimatedCost;
    private String costCurrency;
    private AiDraftRejectReason rejectionReason;
    private Boolean hasRejectionFeedback;
    private String userMessage;
    private JsonNode inputPayload;
    private JsonNode outputPayload;
    private JsonNode safeOutputPayload;
    private Boolean hasSafeOutputPayload;
    private JsonNode confirmationPayload;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime rejectedAt;
}
