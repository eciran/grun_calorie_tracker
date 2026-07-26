package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.AdminRuntimeOperationRecordRequestDto;
import com.grun.calorietracker.dto.AdminRuntimeOperationsPolicyUpdateRequestDto;
import com.grun.calorietracker.dto.AdminRuntimePolicyRollbackRequestDto;
import com.grun.calorietracker.entity.RuntimeOperationRecordEntity;
import com.grun.calorietracker.entity.RuntimeOperationsPolicyEntity;
import com.grun.calorietracker.enums.RuntimeOperationRecordType;
import com.grun.calorietracker.enums.RuntimeOperationStatus;
import com.grun.calorietracker.enums.RuntimeRolloutSegment;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.repository.RuntimeOperationRecordRepository;
import com.grun.calorietracker.repository.RuntimeOperationsPolicyRepository;
import com.grun.calorietracker.repository.SubscriptionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.RuntimeApiMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeOperationsServiceImplTest {
    private RuntimeOperationsPolicyRepository policyRepository;
    private RuntimeOperationRecordRepository recordRepository;
    private RuntimeOperationsServiceImpl service;
    private RuntimeOperationsPolicyEntity policy;

    @BeforeEach
    void setUp() {
        policyRepository = mock(RuntimeOperationsPolicyRepository.class);
        recordRepository = mock(RuntimeOperationRecordRepository.class);
        service = new RuntimeOperationsServiceImpl(
                policyRepository, recordRepository, new RuntimeApiMetricsService(),
                new ObjectMapper().findAndRegisterModules(),
                mock(UserRepository.class), mock(SubscriptionRepository.class));
        policy = policy();
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(recordRepository.save(any())).thenAnswer(invocation -> {
            RuntimeOperationRecordEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) entity.setId(42L);
            return entity;
        });
        service.initializeCache();
    }

    @Test
    void updatesTypedPolicyAndRetainsRollbackSnapshot() {
        AdminRuntimeOperationsPolicyUpdateRequestDto request = updateRequest();
        request.setMaintenanceEnabled(true);

        var result = service.updatePolicy("admin@grun.local", request);

        assertTrue(result.getMaintenanceEnabled());
        assertTrue(policy.getPreviousSnapshot().contains("\"maintenanceEnabled\":false"));
        assertEquals("admin@grun.local", policy.getUpdatedBy());
    }

    @Test
    void rejectsStalePolicyVersion() {
        AdminRuntimeOperationsPolicyUpdateRequestDto request = updateRequest();
        request.setVersion(99L);

        assertThrows(IllegalArgumentException.class,
                () -> service.updatePolicy("admin@grun.local", request));
    }

    @Test
    void rollsBackPreviousSnapshot() {
        AdminRuntimeOperationsPolicyUpdateRequestDto update = updateRequest();
        update.setMaintenanceEnabled(true);
        service.updatePolicy("admin@grun.local", update);
        policy.setVersion(1L);
        AdminRuntimePolicyRollbackRequestDto rollback = new AdminRuntimePolicyRollbackRequestDto();
        rollback.setVersion(1L);
        rollback.setReason("Restore the prior production policy.");

        var result = service.rollbackPolicy("admin@grun.local", rollback);

        assertFalse(result.getMaintenanceEnabled());
    }

    @Test
    void rejectsAdminCreatedScheduledJobRegistryRows() {
        AdminRuntimeOperationRecordRequestDto request = recordRequest();
        request.setRecordType(RuntimeOperationRecordType.SCHEDULED_JOB);
        request.setStatus(RuntimeOperationStatus.SCHEDULED);

        assertThrows(IllegalArgumentException.class,
                () -> service.createRecord("admin@grun.local", request));
    }

    @Test
    void queuesOnlyFailedRetryableScheduledJobs() {
        RuntimeOperationRecordEntity source = new RuntimeOperationRecordEntity();
        source.setId(9L);
        source.setRecordType(RuntimeOperationRecordType.SCHEDULED_JOB);
        source.setStatus(RuntimeOperationStatus.FAILED);
        source.setRetryable(true);
        source.setRetryCount(1);
        source.setOperationKey("token-cleanup");
        source.setTitle("Token cleanup");
        when(recordRepository.findById(9L)).thenReturn(Optional.of(source));

        var result = service.retryRecord("admin@grun.local", 9L);

        assertEquals(RuntimeOperationStatus.SCHEDULED, result.status());
        assertEquals(2, result.retryCount());
        assertEquals(9L, result.parentRecordId());
    }

    private RuntimeOperationsPolicyEntity policy() {
        RuntimeOperationsPolicyEntity entity = new RuntimeOperationsPolicyEntity();
        entity.setId(1L);
        entity.setVersion(0L);
        entity.setMaintenanceEnabled(false);
        entity.setMaintenanceMessage("Maintenance");
        entity.setReleaseVersion("1.0.0");
        entity.setDeploymentEnvironment("local");
        entity.setMinimumIosVersion("1.0.0");
        entity.setMinimumAndroidVersion("1.0.0");
        entity.setRolloutFeature(SubscriptionFeature.AI_INSIGHTS);
        entity.setRolloutEnabled(false);
        entity.setRolloutSegment(RuntimeRolloutSegment.ALL);
        entity.setRolloutPercentage(0);
        entity.setApiLatencyWarningMs(2000L);
        entity.setApiErrorRateThreshold(0.05);
        entity.setChangeReason("Initial policy");
        entity.setUpdatedBy("SYSTEM");
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private AdminRuntimeOperationsPolicyUpdateRequestDto updateRequest() {
        AdminRuntimeOperationsPolicyUpdateRequestDto request = new AdminRuntimeOperationsPolicyUpdateRequestDto();
        request.setVersion(0L);
        request.setMaintenanceEnabled(false);
        request.setMaintenanceMessage("Scheduled maintenance");
        request.setReleaseVersion("1.1.0");
        request.setDeploymentEnvironment("production");
        request.setMinimumIosVersion("1.0.0");
        request.setMinimumAndroidVersion("1.0.0");
        request.setRolloutFeature(SubscriptionFeature.AI_INSIGHTS);
        request.setRolloutEnabled(true);
        request.setRolloutSegment(RuntimeRolloutSegment.NEW_USERS);
        request.setRolloutPercentage(25);
        request.setApiLatencyWarningMs(1500L);
        request.setApiErrorRateThreshold(0.03);
        request.setReason("Enable a controlled production rollout.");
        return request;
    }

    private AdminRuntimeOperationRecordRequestDto recordRequest() {
        AdminRuntimeOperationRecordRequestDto request = new AdminRuntimeOperationRecordRequestDto();
        request.setRecordType(RuntimeOperationRecordType.INCIDENT);
        request.setStatus(RuntimeOperationStatus.OPEN);
        request.setOperationKey("incident-1");
        request.setTitle("Provider latency");
        request.setSummary("Provider latency is above the configured threshold.");
        request.setRetryable(false);
        return request;
    }
}
