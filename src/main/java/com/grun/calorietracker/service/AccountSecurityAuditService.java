package com.grun.calorietracker.service;

import com.grun.calorietracker.enums.AccountSecurityEventType;
import com.grun.calorietracker.enums.AuthProvider;

public interface AccountSecurityAuditService {
    void record(Long userId, AccountSecurityEventType eventType, AuthProvider provider, String resultCode);
}
