package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AiRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Admin AI quota refund result.")
public class AdminAiQuotaRefundResponseDto {
    private Long requestId;
    private Long userId;
    private AiRequestStatus status;
    private Integer quotaConsumedAmount;
    private Integer quotaRefundedAmount;
    private Integer refundedNow;
    private String quotaRefundReason;
    private String quotaRefundedBy;
    private LocalDateTime quotaRefundedAt;
    private SubscriptionDto subscription;
}
