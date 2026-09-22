package com.grun.calorietracker.dto;

import java.time.Instant;
import java.time.LocalDate;

public record OwnerDailySummaryDto(LocalDate date, Instant generatedAt, long backendErrorGroups,
        long backendErrorOccurrences, long financialApprovalRequests, long operationalApprovalRequests,
        long pendingApprovals, long approvedToday, long rejectedToday, long sentAlerts, long failedAlerts,
        long newErrorGroups, long investigatingErrorGroups, long resolvedErrorGroups, long reopenedErrorGroups) {}
