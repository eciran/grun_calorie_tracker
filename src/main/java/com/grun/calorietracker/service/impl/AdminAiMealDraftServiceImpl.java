package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AdminAiRequestReviewDto;
import com.grun.calorietracker.dto.AdminAiMonitoringSummaryDto;
import com.grun.calorietracker.dto.AdminAiQuotaRefundRequestDto;
import com.grun.calorietracker.dto.AdminAiQuotaRefundResponseDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.entity.AiRequestHistoryEntity;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.repository.AiRequestHistoryRepository;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.service.AdminAiMealDraftService;
import com.grun.calorietracker.service.PushDeliveryService;
import com.grun.calorietracker.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAiMealDraftServiceImpl implements AdminAiMealDraftService {

    private static final String AI_QUOTA_REFUND_APPROVED_TYPE = "ai_quota_refund_approved";
    private static final String AI_REFUND_SOURCE = "AI_QUOTA_REFUND";

    private final AiRequestHistoryRepository aiRequestHistoryRepository;
    private final SubscriptionService subscriptionService;
    private final NotificationRepository notificationRepository;
    private final PushDeliveryService pushDeliveryService;
    private final AiProperties aiProperties;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminAiRequestReviewDto> listRequests(AiRequestType requestType, AiRequestStatus status, boolean refundableOnly, Pageable pageable) {
        Page<AiRequestHistoryEntity> requests;
        if (refundableOnly) {
            requests = aiRequestHistoryRepository.findRefundableRejectedDrafts(pageable);
        } else if (requestType != null && status != null) {
            requests = aiRequestHistoryRepository.findByRequestTypeAndStatusOrderByCreatedAtDesc(requestType, status, pageable);
        } else if (requestType != null) {
            requests = aiRequestHistoryRepository.findByRequestTypeOrderByCreatedAtDesc(requestType, pageable);
        } else if (status != null) {
            requests = aiRequestHistoryRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            requests = aiRequestHistoryRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return requests.map(this::toReviewDto);
    }
    @Override
    @Transactional(readOnly = true)
    public AdminAiMonitoringSummaryDto getMonitoringSummary(int windowHours) {
        int safeWindowHours = Math.min(Math.max(windowHours, 1), 24 * 31);
        LocalDateTime generatedAt = LocalDateTime.now();
        LocalDateTime windowStart = generatedAt.minusHours(safeWindowHours);

        List<AdminAiMonitoringSummaryDto.ProviderModelMetric> providerMetrics = new ArrayList<>();
        Map<String, Double> costByCurrency = new LinkedHashMap<>();
        long promptTokens = 0;
        long completionTokens = 0;
        long totalTokens = 0;
        long quotaConsumed = 0;
        long quotaRefunded = 0;

        for (Object[] row : aiRequestHistoryRepository.summarizeByProviderModelAfter(windowStart)) {
            if (row == null || row.length < 11) {
                continue;
            }
            AiProvider provider = row[0] instanceof AiProvider value ? value : null;
            String model = stringValue(row[1], "unknown");
            String promptVersion = stringValue(row[2], "legacy");
            String currency = stringValue(row[3], "UNSPECIFIED");
            long requestCount = longValue(row[4]);
            long rowPromptTokens = longValue(row[5]);
            long rowCompletionTokens = longValue(row[6]);
            long rowTotalTokens = longValue(row[7]);
            double estimatedCost = doubleValue(row[8]);
            long rowQuotaConsumed = longValue(row[9]);
            long rowQuotaRefunded = longValue(row[10]);

            providerMetrics.add(new AdminAiMonitoringSummaryDto.ProviderModelMetric(
                    provider,
                    model,
                    promptVersion,
                    currency,
                    requestCount,
                    rowPromptTokens,
                    rowCompletionTokens,
                    rowTotalTokens,
                    estimatedCost,
                    rowQuotaConsumed,
                    rowQuotaRefunded
            ));
            promptTokens += rowPromptTokens;
            completionTokens += rowCompletionTokens;
            totalTokens += rowTotalTokens;
            quotaConsumed += rowQuotaConsumed;
            quotaRefunded += rowQuotaRefunded;
            costByCurrency.merge(currency, estimatedCost, Double::sum);
        }

        List<AdminAiMonitoringSummaryDto.RequestStatusMetric> requestMetrics = new ArrayList<>();
        long totalRequests = 0;
        long draftCreated = 0;
        long confirmed = 0;
        long rejected = 0;
        long failed = 0;
        for (Object[] row : aiRequestHistoryRepository.summarizeByRequestTypeStatusAfter(windowStart)) {
            if (row == null || row.length < 6) {
                continue;
            }
            AiRequestType requestType = row[0] instanceof AiRequestType value ? value : null;
            AiRequestStatus status = row[1] instanceof AiRequestStatus value ? value : null;
            long requestCount = longValue(row[2]);
            requestMetrics.add(new AdminAiMonitoringSummaryDto.RequestStatusMetric(
                    requestType,
                    status,
                    requestCount,
                    longValue(row[3]),
                    longValue(row[4]),
                    longValue(row[5])
            ));
            totalRequests += requestCount;
            if (status == AiRequestStatus.DRAFT_CREATED) {
                draftCreated += requestCount;
            } else if (status == AiRequestStatus.CONFIRMED) {
                confirmed += requestCount;
            } else if (status == AiRequestStatus.REJECTED) {
                rejected += requestCount;
            } else if (status == AiRequestStatus.FAILED) {
                failed += requestCount;
            }
        }

        AdminAiMonitoringSummaryDto summary = new AdminAiMonitoringSummaryDto();
        summary.setGeneratedAt(generatedAt);
        summary.setWindowStart(windowStart);
        summary.setWindowHours(safeWindowHours);
        summary.setTotalRequests(totalRequests);
        summary.setDraftCreated(draftCreated);
        summary.setConfirmed(confirmed);
        summary.setRejected(rejected);
        summary.setFailed(failed);
        summary.setFailureRate(totalRequests == 0 ? 0 : (double) failed / totalRequests);
        summary.setPromptTokens(promptTokens);
        summary.setCompletionTokens(completionTokens);
        summary.setTotalTokens(totalTokens);
        summary.setQuotaConsumedAmount(quotaConsumed);
        summary.setQuotaRefundedAmount(quotaRefunded);
        summary.setEstimatedCostByCurrency(costByCurrency);
        summary.setProviderModels(providerMetrics);
        summary.setRequestStatuses(requestMetrics);
        List<AdminAiMonitoringSummaryDto.OperationalAlert> alerts = operationalAlerts(
                totalRequests, failed, rejected, totalTokens, costByCurrency, requestMetrics);
        summary.setAttentionRequired(!alerts.isEmpty());
        summary.setAlerts(alerts);
        return summary;
    }
    @Override
    @Transactional
    public AdminAiQuotaRefundResponseDto refundQuota(String adminEmail, Long requestId, AdminAiQuotaRefundRequestDto request) {
        AiRequestHistoryEntity history = aiRequestHistoryRepository.findByIdForQuotaRefund(requestId)
                .orElseThrow(() -> new IllegalArgumentException("AI request was not found."));
        if (!Boolean.TRUE.equals(history.getQuotaConsumed())) {
            throw new IllegalArgumentException("AI request did not consume quota.");
        }
        if (history.getStatus() != AiRequestStatus.REJECTED) {
            throw new IllegalArgumentException("AI quota can be refunded only for rejected drafts.");
        }
        int consumed = safeInt(history.getQuotaConsumedAmount());
        int refunded = safeInt(history.getQuotaRefundedAmount());
        int refundable = consumed - refunded;
        int amount = request.getAmount() == null ? 0 : request.getAmount();
        if (amount <= 0) {
            throw new IllegalArgumentException("AI quota refund amount must be greater than zero.");
        }
        if (amount > refundable) {
            throw new IllegalArgumentException("AI quota refund amount exceeds refundable quota for this request.");
        }
        if (history.getUser() == null || history.getUser().getId() == null) {
            throw new IllegalArgumentException("AI request user is missing.");
        }

        SubscriptionDto subscription = subscriptionService.refundConsumedAiQuota(history.getUser().getId(), amount);
        LocalDateTime now = LocalDateTime.now();
        history.setQuotaRefundedAmount(refunded + amount);
        history.setQuotaRefundReason(request.getReason().trim());
        history.setQuotaRefundedBy(adminEmail);
        history.setQuotaRefundedAt(now);
        AiRequestHistoryEntity saved = aiRequestHistoryRepository.save(history);
        notifyUserAboutQuotaRefund(saved, amount);
        return toDto(saved, amount, subscription);
    }

    private void notifyUserAboutQuotaRefund(AiRequestHistoryEntity history, int amount) {
        if (history.getUser() == null) {
            return;
        }
        NotificationEntity notification = new NotificationEntity();
        notification.setUser(history.getUser());
        notification.setType(AI_QUOTA_REFUND_APPROVED_TYPE);
        notification.setSeverity("INFO");
        notification.setSource(AI_REFUND_SOURCE);
        notification.setTargetType("AI_REQUEST");
        notification.setTargetId(String.valueOf(history.getId()));
        notification.setTargetRoute("ai");
        notification.setMessage("%d AI credit%s refunded to your account.".formatted(amount, amount == 1 ? "" : "s"));
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        NotificationEntity saved = notificationRepository.save(notification);
        try {
            pushDeliveryService.deliver(saved);
        } catch (RuntimeException ex) {
            log.warn("ai_quota_refund_push_delivery_failed requestId={} notificationId={} reason={}", history.getId(), saved.getId(), ex.getMessage());
        }
    }
    private AdminAiRequestReviewDto toReviewDto(AiRequestHistoryEntity entity) {
        int consumed = safeInt(entity.getQuotaConsumedAmount());
        int refunded = safeInt(entity.getQuotaRefundedAmount());
        AdminAiRequestReviewDto dto = new AdminAiRequestReviewDto();
        dto.setRequestId(entity.getId());
        dto.setUserId(entity.getUser() == null ? null : entity.getUser().getId());
        dto.setUserEmail(entity.getUser() == null ? null : entity.getUser().getEmail());
        dto.setRequestType(entity.getRequestType());
        dto.setProvider(entity.getProvider());
        dto.setModel(entity.getModel());
        dto.setPromptVersion(entity.getPromptVersion());
        dto.setStatus(entity.getStatus());
        dto.setQuotaConsumed(entity.getQuotaConsumed());
        dto.setQuotaConsumedAmount(consumed);
        dto.setQuotaRefundedAmount(refunded);
        dto.setRefundableAmount(Math.max(consumed - refunded, 0));
        dto.setRejectionReason(entity.getRejectionReason());
        dto.setRejectionFeedback(entity.getRejectionFeedback());
        dto.setLatencyMs(entity.getLatencyMs());
        dto.setTotalTokens(entity.getTotalTokens());
        dto.setEstimatedCost(entity.getEstimatedCost());
        dto.setCostCurrency(entity.getCostCurrency());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setRejectedAt(entity.getRejectedAt());
        dto.setQuotaRefundReason(entity.getQuotaRefundReason());
        dto.setQuotaRefundedBy(entity.getQuotaRefundedBy());
        dto.setQuotaRefundedAt(entity.getQuotaRefundedAt());
        return dto;
    }

    private AdminAiQuotaRefundResponseDto toDto(AiRequestHistoryEntity entity, int refundedNow, SubscriptionDto subscription) {
        AdminAiQuotaRefundResponseDto dto = new AdminAiQuotaRefundResponseDto();
        dto.setRequestId(entity.getId());
        dto.setUserId(entity.getUser() == null ? null : entity.getUser().getId());
        dto.setStatus(entity.getStatus());
        dto.setQuotaConsumedAmount(safeInt(entity.getQuotaConsumedAmount()));
        dto.setQuotaRefundedAmount(safeInt(entity.getQuotaRefundedAmount()));
        dto.setRefundedNow(refundedNow);
        dto.setQuotaRefundReason(entity.getQuotaRefundReason());
        dto.setQuotaRefundedBy(entity.getQuotaRefundedBy());
        dto.setQuotaRefundedAt(entity.getQuotaRefundedAt());
        dto.setSubscription(subscription);
        return dto;
    }

    private List<AdminAiMonitoringSummaryDto.OperationalAlert> operationalAlerts(
            long totalRequests,
            long failed,
            long rejected,
            long totalTokens,
            Map<String, Double> costByCurrency,
            List<AdminAiMonitoringSummaryDto.RequestStatusMetric> requestMetrics) {
        AiProperties.Monitoring config = aiProperties.getMonitoring();
        int minRequests = Math.max(config.getMinRequestsForAlert(), 1);
        double failureThreshold = rateThreshold(config.getFailureRateThreshold());
        double rejectionThreshold = rateThreshold(config.getRejectionRateThreshold());
        List<AdminAiMonitoringSummaryDto.OperationalAlert> alerts = new ArrayList<>();

        if (totalRequests >= minRequests && (double) failed / totalRequests >= failureThreshold) {
            alerts.add(alert("FAILURE_RATE_HIGH", "CRITICAL",
                    "AI request failure rate reached the configured operational threshold.", null, null));
        }
        if (totalRequests >= minRequests && (double) rejected / totalRequests >= rejectionThreshold) {
            alerts.add(alert("REJECTION_RATE_HIGH", "WARNING",
                    "AI result rejection rate reached the configured quality threshold.", null, null));
        }
        if (config.getMaxTokensPerWindow() > 0
                && totalTokens >= config.getMaxTokensPerWindow()) {
            alerts.add(alert("TOKEN_VOLUME_HIGH", "WARNING",
                    "AI token usage reached the configured window threshold.", null, null));
        }
        if (config.getMaxEstimatedCostPerCurrency() > 0) {
            costByCurrency.forEach((currency, cost) -> {
                if (cost != null && cost >= config.getMaxEstimatedCostPerCurrency()) {
                    alerts.add(alert("ESTIMATED_COST_HIGH", "WARNING",
                            "Estimated AI cost reached the configured currency threshold.",
                            null, currency));
                }
            });
        }

        Map<AiRequestType, long[]> typeCounts = new LinkedHashMap<>();
        for (AdminAiMonitoringSummaryDto.RequestStatusMetric metric : requestMetrics) {
            if (metric.getRequestType() == null) {
                continue;
            }
            long[] counts = typeCounts.computeIfAbsent(metric.getRequestType(), ignored -> new long[2]);
            counts[0] += metric.getRequestCount();
            if (metric.getStatus() == AiRequestStatus.FAILED) {
                counts[1] += metric.getRequestCount();
            }
        }
        typeCounts.forEach((requestType, counts) -> {
            if (counts[0] >= minRequests && (double) counts[1] / counts[0] >= failureThreshold) {
                alerts.add(alert("REQUEST_TYPE_FAILURE_RATE_HIGH", "CRITICAL",
                        "An AI request type reached the configured failure-rate threshold.",
                        requestType, null));
            }
        });
        return alerts;
    }

    private double rateThreshold(double value) {
        return Math.min(Math.max(value, 0.0), 1.0);
    }

    private AdminAiMonitoringSummaryDto.OperationalAlert alert(
            String code, String severity, String message,
            AiRequestType requestType, String currency) {
        return new AdminAiMonitoringSummaryDto.OperationalAlert(
                code, severity, message, requestType, currency);
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0d;
    }

    private String stringValue(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }
    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
