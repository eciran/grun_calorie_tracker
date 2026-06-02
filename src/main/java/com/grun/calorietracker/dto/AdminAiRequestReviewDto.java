package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AiDraftRejectReason;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminAiRequestReviewDto {
    private Long requestId;
    private Long userId;
    private String userEmail;
    private AiRequestType requestType;
    private AiProvider provider;
    private String model;
    private AiRequestStatus status;
    private Boolean quotaConsumed;
    private Integer quotaConsumedAmount;
    private Integer quotaRefundedAmount;
    private Integer refundableAmount;
    private AiDraftRejectReason rejectionReason;
    private String rejectionFeedback;
    private Long latencyMs;
    private Integer totalTokens;
    private Double estimatedCost;
    private String costCurrency;
    private LocalDateTime createdAt;
    private LocalDateTime rejectedAt;
    private String quotaRefundReason;
    private String quotaRefundedBy;
    private LocalDateTime quotaRefundedAt;
}
