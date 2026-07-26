package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminAiOperationsPolicyDto;
import com.grun.calorietracker.dto.AdminAiOperationsPolicyUpdateRequestDto;
import com.grun.calorietracker.dto.AdminAiOperationsRollbackRequestDto;

public interface AiOperationsPolicyService {
    AdminAiOperationsPolicyDto getPolicy();
    AdminAiOperationsPolicyDto update(String adminEmail, AdminAiOperationsPolicyUpdateRequestDto request);
    AdminAiOperationsPolicyDto rollback(String adminEmail, AdminAiOperationsRollbackRequestDto request);
    void assertRequestAllowed();
}