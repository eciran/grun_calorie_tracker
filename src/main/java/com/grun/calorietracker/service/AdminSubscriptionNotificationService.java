package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;

public interface AdminSubscriptionNotificationService {
    AdminSubscriptionNotificationPolicyDto getPolicy();
    AdminSubscriptionNotificationPolicyDto publishApproved(AdminSubscriptionNotificationPolicyRequestDto request,
            String checker, String correlationId);
    AdminSubscriptionNotificationPolicyDto emergencyStop(String reason, String admin, String correlationId);
    AdminSubscriptionNotificationPreviewDto preview(AdminSubscriptionNotificationPreviewRequestDto request);
    AdminSubscriptionNotificationLedgerPageDto ledger(int page, int size);
    AdminSubscriptionNotificationMetricsDto metrics();
    void publishDefinitionApproved(Long definitionId, AdminNotificationDefinitionRequestDto request,
            String checker, String correlationId);
    boolean deliveryAllowed();
}
