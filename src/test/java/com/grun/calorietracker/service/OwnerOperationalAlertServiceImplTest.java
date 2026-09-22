package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.OwnerOperationalAlertEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.AdminApprovalActionType;
import com.grun.calorietracker.dto.OwnerDailySummaryDto;
import java.time.LocalDate;
import com.grun.calorietracker.repository.OwnerOperationalAlertRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.OwnerOperationalAlertServiceImpl;
import com.grun.calorietracker.service.support.OwnerErrorEvent;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OwnerOperationalAlertServiceImplTest {
    private final OwnerOperationalAlertRepository alerts = mock(OwnerOperationalAlertRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MailDeliveryService mail = mock(MailDeliveryService.class);
    private final OwnerOperationalAlertServiceImpl service = new OwnerOperationalAlertServiceImpl(alerts, users, mail);

    @Test
    void recordCriticalBackendError_groupsBySanitizedRouteAndHour() {
        when(alerts.findByDedupeKey(anyString())).thenReturn(Optional.empty());
        service.recordCriticalBackendError(event(503, "cid value&next"));

        var captor = org.mockito.ArgumentCaptor.forClass(OwnerOperationalAlertEntity.class);
        verify(alerts).save(captor.capture());
        OwnerOperationalAlertEntity value = captor.getValue();
        assertEquals("BACKEND_ERROR", value.getCategory());
        assertEquals(1L, value.getOccurrenceCount());
        assertEquals("PENDING", value.getStatus());
        assertTrue(value.getDedupeKey().matches("BACKEND_5XX\\|[0-9a-f]{64}"));
        assertTrue(value.getTargetPath().endsWith("correlationId=cid+value%26next"));
        assertFalse(value.getMessageEn().contains("cid value"));
    }

    @Test
    void deliverDue_usesOwnerLanguageAndMarksSent() {
        OwnerOperationalAlertEntity alert = pending();
        UserEntity owner = new UserEntity(); owner.setEmail("owner@example.com"); owner.setAccountEnabled(true); owner.setPreferredLanguage(PreferredLanguage.TR);
        ReflectionTestUtils.setField(service, "deliveryEnabled", true);
        ReflectionTestUtils.setField(service, "primaryOwnerEmail", owner.getEmail());
        when(users.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(alerts.findByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(anyCollection(), any(), any(Pageable.class))).thenReturn(List.of(alert));

        assertEquals(1, service.deliverDue());

        verify(mail).sendTransactionalEmail(eq(owner.getEmail()), eq("Kritik hata"), contains("/admin/system/errors"));
        assertEquals("SENT", alert.getStatus());
        assertNotNull(alert.getSentAt());
    }

    @Test
    void recordApprovalRequired_createsOneAddressableFinancialAlert() {
        when(alerts.findByDedupeKey("APPROVAL_REQUIRED|91")).thenReturn(Optional.empty());

        service.recordApprovalRequired(91L, AdminApprovalActionType.SUBSCRIPTION_UPDATE,
                Instant.parse("2026-09-17T13:00:00Z"));

        var captor = org.mockito.ArgumentCaptor.forClass(OwnerOperationalAlertEntity.class);
        verify(alerts).save(captor.capture());
        OwnerOperationalAlertEntity value = captor.getValue();
        assertEquals("FINANCIAL_APPROVAL", value.getCategory());
        assertEquals("CRITICAL", value.getSeverity());
        assertEquals("/admin/approvals?approvalId=91", value.getTargetPath());
        assertEquals(1L, value.getOccurrenceCount());
        assertFalse(value.getMessageEn().contains("maker"));
    }

    @Test
    void recordApprovalRequired_isIdempotentForExistingApproval() {
        OwnerOperationalAlertEntity existing = pending();
        when(alerts.findByDedupeKey("APPROVAL_REQUIRED|91")).thenReturn(Optional.of(existing));

        service.recordApprovalRequired(91L, AdminApprovalActionType.RUNTIME_POLICY_UPDATE, Instant.now());

        verify(alerts, never()).save(any());
    }

    @Test
    void recordDailySummary_isIdempotentByReportDate() {
        when(alerts.findByDedupeKey("DAILY_SUMMARY|2026-09-17")).thenReturn(Optional.empty());
        service.recordDailySummary(new OwnerDailySummaryDto(LocalDate.of(2026,9,17), Instant.now(), 2, 7, 3, 1, 4, 5, 1, 8, 1, 2, 3, 4, 1));
        var captor = org.mockito.ArgumentCaptor.forClass(OwnerOperationalAlertEntity.class); verify(alerts).save(captor.capture());
        assertEquals("DAILY_SUMMARY", captor.getValue().getCategory()); assertEquals("PENDING", captor.getValue().getStatus());
        assertEquals("/admin/reports/owner-alerts?reportDate=2026-09-17", captor.getValue().getTargetPath());
        assertTrue(captor.getValue().getMessageEn().contains("new/investigating/resolved/reopened: 2/3/4/1"));
    }

    @Test
    void deliverDue_recordsRetryWithoutPersistingProviderMessage() {
        OwnerOperationalAlertEntity alert = pending();
        UserEntity owner = new UserEntity(); owner.setEmail("owner@example.com"); owner.setAccountEnabled(true);
        ReflectionTestUtils.setField(service, "deliveryEnabled", true);
        ReflectionTestUtils.setField(service, "primaryOwnerEmail", owner.getEmail());
        when(users.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(alerts.findByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(anyCollection(), any(), any(Pageable.class))).thenReturn(List.of(alert));
        doThrow(new IllegalStateException("provider secret detail")).when(mail).sendTransactionalEmail(anyString(), anyString(), anyString());

        assertEquals(0, service.deliverDue());

        assertEquals("RETRY", alert.getStatus());
        assertEquals("java.lang.IllegalStateException", alert.getLastErrorType());
        assertFalse(alert.getLastErrorType().contains("secret"));
    }

    private OwnerErrorEvent event(int status, String correlationId) {
        return new OwnerErrorEvent(null, UUID.randomUUID(), Instant.parse("2026-09-17T12:15:00Z"), status, "POST", "/api/v1/products/{id}", correlationId, "UNEXPECTED_ERROR", "IllegalStateException", "Fixture.run:1", 30,"BACKEND",null,null);
    }

    private OwnerOperationalAlertEntity pending() {
        OwnerOperationalAlertEntity value = new OwnerOperationalAlertEntity();
        value.setId(1L); value.setStatus("PENDING"); value.setTitleEn("Critical error"); value.setTitleTr("Kritik hata");
        value.setMessageEn("Open the record."); value.setMessageTr("Kaydı açın."); value.setTargetPath("/admin/system/errors");
        value.setOccurrenceCount(3L); value.setAttemptCount(0); value.setNextAttemptAt(Instant.now()); value.setUpdatedAt(Instant.now());
        return value;
    }
}
