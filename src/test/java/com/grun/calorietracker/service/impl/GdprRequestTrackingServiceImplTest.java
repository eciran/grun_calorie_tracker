package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminGdprRequestUpdateDto;
import com.grun.calorietracker.entity.GdprAdminRequestEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.GdprAdminRequestRepository;
import com.grun.calorietracker.service.AdminAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GdprRequestTrackingServiceImplTest {
    @Mock private GdprAdminRequestRepository repository;
    @Mock private AdminAuditService auditService;
    private GdprRequestTrackingServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GdprRequestTrackingServiceImpl(repository, auditService);
        when(repository.save(any())).thenAnswer(invocation -> {
            GdprAdminRequestEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) entity.setId(7L);
            return entity;
        });
    }

    @Test
    void beginStoresOnlyHashedSubjectReferenceAndMetadata() {
        Long id = service.begin("person@example.com", GdprRequestType.EXPORT);

        assertThat(id).isEqualTo(7L);
        verify(repository).save(argThat(entity -> entity.getSubjectReference().length() == 64
                && !entity.getSubjectReference().contains("person")
                && entity.getStatus() == GdprRequestStatus.IN_PROGRESS));
    }

    @Test
    void failureDoesNotPersistExceptionMessage() {
        GdprAdminRequestEntity entity = pending();
        when(repository.findById(7L)).thenReturn(Optional.of(entity));

        service.fail(7L, new IllegalArgumentException("secret user payload"));

        assertThat(entity.getFailureSummary()).isEqualTo("IllegalArgumentException");
        assertThat(entity.getFailureSummary()).doesNotContain("secret");
    }

    @Test
    void escalationIsAuditedWithoutSubjectData() {
        GdprAdminRequestEntity entity = pending();
        when(repository.findById(7L)).thenReturn(Optional.of(entity));

        service.update(7L, new AdminGdprRequestUpdateDto("legal@grun.app", true, "SLA review"),
                "owner@grun.app", "cid");

        assertThat(entity.getStatus()).isEqualTo(GdprRequestStatus.ESCALATED);
        verify(auditService).record(eq("owner@grun.app"), eq(AdminAuditActionType.GDPR_REQUEST_UPDATE),
                eq(AdminAuditTargetType.GDPR_REQUEST), eq("7"), any(), any(), eq("cid"));
    }

    private GdprAdminRequestEntity pending() {
        GdprAdminRequestEntity entity = new GdprAdminRequestEntity();
        entity.setId(7L);
        entity.setRequestType(GdprRequestType.DELETE);
        entity.setStatus(GdprRequestStatus.IN_PROGRESS);
        entity.setSubjectReference("a".repeat(64));
        entity.setRequestedAt(Instant.now());
        entity.setDueAt(Instant.now().plusSeconds(60));
        return entity;
    }
}
