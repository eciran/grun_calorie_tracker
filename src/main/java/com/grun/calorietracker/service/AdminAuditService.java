package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminActionAuditPageDto;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;

public interface AdminAuditService {
    void record(String adminEmail,
                AdminAuditActionType actionType,
                AdminAuditTargetType targetType,
                String targetKey,
                Object oldValue,
                Object newValue,
                String correlationId);

    AdminActionAuditPageDto list(AdminAuditActionType actionType,
                                 AdminAuditTargetType targetType,
                                 int page,
                                 int size);
}
