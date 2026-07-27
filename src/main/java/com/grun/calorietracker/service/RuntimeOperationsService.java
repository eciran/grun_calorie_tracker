package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminRuntimeApiMetricsDto;
import com.grun.calorietracker.dto.AdminRuntimeOperationRecordDto;
import com.grun.calorietracker.dto.AdminRuntimeOperationRecordRequestDto;
import com.grun.calorietracker.dto.AdminRuntimeOperationsPolicyDto;
import com.grun.calorietracker.dto.AdminRuntimeOperationsPolicyUpdateRequestDto;
import com.grun.calorietracker.dto.AdminRuntimePolicyRollbackRequestDto;
import com.grun.calorietracker.dto.RuntimeClientConfigDto;
import com.grun.calorietracker.enums.RuntimeOperationRecordType;
import com.grun.calorietracker.enums.RuntimeOperationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RuntimeOperationsService {
    AdminRuntimeOperationsPolicyDto getPolicy();
    AdminRuntimeOperationsPolicyDto updatePolicy(String adminEmail, AdminRuntimeOperationsPolicyUpdateRequestDto request);
    AdminRuntimeOperationsPolicyDto rollbackPolicy(String adminEmail, AdminRuntimePolicyRollbackRequestDto request);
    AdminRuntimeApiMetricsDto getApiMetrics();
    Page<AdminRuntimeOperationRecordDto> getRecords(
            RuntimeOperationRecordType type, RuntimeOperationStatus status, Pageable pageable);
    AdminRuntimeOperationRecordDto createRecord(String adminEmail, AdminRuntimeOperationRecordRequestDto request);
    AdminRuntimeOperationRecordDto retryRecord(String adminEmail, Long id);
    RuntimeClientConfigDto getClientConfig(String email);
    boolean maintenanceEnabled();
    String maintenanceMessage();
}
