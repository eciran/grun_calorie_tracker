package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "Privacy-safe aggregate AI operational metrics for an admin-selected time window.")
public class AdminAiMonitoringSummaryDto {
    private LocalDateTime generatedAt;
    private LocalDateTime windowStart;
    private int windowHours;
    private long totalRequests;
    private long draftCreated;
    private long confirmed;
    private long rejected;
    private long failed;
    private double failureRate;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
    private long quotaConsumedAmount;
    private long quotaRefundedAmount;
    private Map<String, Double> estimatedCostByCurrency;
    private List<ProviderModelMetric> providerModels;
    private List<RequestStatusMetric> requestStatuses;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderModelMetric {
        private AiProvider provider;
        private String model;
        private String promptVersion;
        private String costCurrency;
        private long requestCount;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private double estimatedCost;
        private long quotaConsumedAmount;
        private long quotaRefundedAmount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestStatusMetric {
        private AiRequestType requestType;
        private AiRequestStatus status;
        private long requestCount;
        private long totalTokens;
        private long quotaConsumedAmount;
        private long quotaRefundedAmount;
    }
}