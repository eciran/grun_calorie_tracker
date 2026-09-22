package com.grun.calorietracker.service;

import com.grun.calorietracker.service.support.OwnerErrorEvent;
import com.grun.calorietracker.enums.AdminApprovalActionType;
import java.time.Instant;
import com.grun.calorietracker.dto.OwnerDailySummaryDto;

public interface OwnerOperationalAlertService {
    void recordCriticalBackendError(OwnerErrorEvent event);
    void recordApprovalRequired(long approvalId, AdminApprovalActionType actionType, Instant createdAt);
    void recordDailySummary(OwnerDailySummaryDto summary);
    int deliverDue();
}
