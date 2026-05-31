package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.RetentionPolicyUpdateRequestDto;
import com.grun.calorietracker.entity.RetentionPolicyEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.RetentionPolicyKey;
import com.grun.calorietracker.repository.RetentionPolicyRepository;
import com.grun.calorietracker.service.impl.RetentionPolicyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetentionPolicyServiceImplTest {

    @Mock
    private RetentionPolicyRepository retentionPolicyRepository;

    @Mock
    private AdminAuditService adminAuditService;

    private RetentionPolicyServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RetentionPolicyServiceImpl(retentionPolicyRepository, adminAuditService);
    }

    @Test
    void upsertPolicy_savesPolicyAndRecordsAdminAudit() {
        RetentionPolicyUpdateRequestDto request = new RetentionPolicyUpdateRequestDto(
                2555,
                "Payment audit",
                "Keep anonymized provider events for reconciliation.",
                true
        );
        when(retentionPolicyRepository.findByPolicyKey(RetentionPolicyKey.PAYMENT_AUDIT_EVENTS))
                .thenReturn(Optional.empty());
        when(retentionPolicyRepository.save(org.mockito.ArgumentMatchers.any(RetentionPolicyEntity.class)))
                .thenAnswer(invocation -> {
                    RetentionPolicyEntity entity = invocation.getArgument(0);
                    entity.setId(20L);
                    return entity;
                });

        var result = service.upsertPolicy("admin@grun.app", RetentionPolicyKey.PAYMENT_AUDIT_EVENTS, request);

        assertEquals(20L, result.getId());
        assertEquals(2555, result.getRetentionDays());
        verify(adminAuditService).record(
                org.mockito.ArgumentMatchers.eq("admin@grun.app"),
                org.mockito.ArgumentMatchers.eq(AdminAuditActionType.RETENTION_POLICY_UPDATE),
                org.mockito.ArgumentMatchers.eq(AdminAuditTargetType.RETENTION_POLICY),
                org.mockito.ArgumentMatchers.eq("PAYMENT_AUDIT_EVENTS"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull()
        );
    }
}
