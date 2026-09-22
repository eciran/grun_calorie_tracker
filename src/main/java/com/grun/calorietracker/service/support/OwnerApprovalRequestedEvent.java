package com.grun.calorietracker.service.support;

import com.grun.calorietracker.enums.AdminApprovalActionType;
import java.time.Instant;

public record OwnerApprovalRequestedEvent(long approvalId, AdminApprovalActionType actionType, Instant createdAt) {}
