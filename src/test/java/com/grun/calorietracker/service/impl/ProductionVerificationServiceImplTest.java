package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.ProductionVerificationRunRequestDto;
import com.grun.calorietracker.entity.ProductionVerificationRunEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.ProductionVerificationRunRepository;
import com.grun.calorietracker.service.AdminAuditService;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductionVerificationServiceImplTest {
    @Mock ProductionVerificationRunRepository repository;
    @Mock AdminAuditService auditService;
    ProductionVerificationServiceImpl service;

    @BeforeEach void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ProductionVerificationServiceImpl(repository, auditService);
        when(repository.save(any())).thenAnswer(invocation -> {
            ProductionVerificationRunEntity entity = invocation.getArgument(0);
            entity.setId(9L);
            return entity;
        });
    }

    @Test void recordsSafeEvidenceAndAudit() {
        var request = new ProductionVerificationRunRequestDto("REVENUECAT", "SANDBOX", "PURCHASE",
                ProductionVerificationStatus.PASSED, "ci-run-208", "Sandbox purchase and entitlement verified.",
                Instant.now().plusSeconds(86400));
        var result = service.record(request, "owner@grun.app", "cid");
        assertThat(result.id()).isEqualTo(9L);
        verify(auditService).record(eq("owner@grun.app"),
                eq(AdminAuditActionType.PRODUCTION_VERIFICATION_RECORD),
                eq(AdminAuditTargetType.PRODUCTION_VERIFICATION), eq("9"), isNull(), any(), eq("cid"));
    }

    @Test void rejectsCredentialsInEvidence() {
        var request = new ProductionVerificationRunRequestDto("BREVO", "PRODUCTION", "MAIL_SMOKE",
                ProductionVerificationStatus.PASSED, "token=private", "mail delivered", null);
        assertThatThrownBy(() -> service.record(request, "owner@grun.app", "cid"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("credentials");
        verifyNoInteractions(repository);
    }
}
