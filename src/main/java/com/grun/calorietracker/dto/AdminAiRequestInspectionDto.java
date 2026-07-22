package com.grun.calorietracker.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.grun.calorietracker.enums.AiDraftRejectReason;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiQuotaRefundDecision;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminAiRequestInspectionDto {
    private Long requestId;
    private Long userId;
    private String userEmail;
    private AiRequestType requestType;
    private AiProvider provider;
    private String model;
    private String promptVersion;
    private AiRequestStatus status;
    private Long latencyMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Double estimatedCost;
    private String costCurrency;
    private Boolean quotaConsumed;
    private Integer quotaConsumedAmount;
    private Integer quotaRefundedAmount;
    private AiDraftRejectReason rejectionReason;
    private String rejectionFeedback;
    private AiQuotaRefundDecision quotaRefundDecision;
    private String quotaRefundDecisionReason;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime rejectedAt;
    private JsonNode requestContext;
    private JsonNode result;
    private JsonNode confirmation;
    private String correctionSummary;
    private String failureSummary;
}