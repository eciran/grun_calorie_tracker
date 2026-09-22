package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.OwnerOperationalAlertEntity;
import com.grun.calorietracker.enums.AdminReauthenticationPurpose;
import com.grun.calorietracker.repository.OwnerOperationalAlertRepository;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.service.impl.OwnerOperationalAlertManagementServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OwnerOperationalAlertManagementServiceImplTest {
    private final OwnerOperationalAlertRepository repository = mock(OwnerOperationalAlertRepository.class);
    private final JwtUtil jwt = mock(JwtUtil.class);
    private final AdminAuditService audit = mock(AdminAuditService.class);
    private final OwnerOperationalAlertManagementServiceImpl service = new OwnerOperationalAlertManagementServiceImpl(repository, jwt, audit);

    @Test void failedAlertCanBeQueuedAgainWithFreshPurposeBoundProof() {
        OwnerOperationalAlertEntity alert = alert("FAILED"); proof(); when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(alert)); when(repository.save(alert)).thenReturn(alert);
        var result = service.retry(7L, "owner@example.com", "proof", "Provider recovered", "cid");
        assertEquals("RETRY", result.status()); assertEquals(0, result.attemptCount()); assertNull(result.lastErrorType()); assertNotNull(result.nextAttemptAt());
        verify(audit).record(eq("owner@example.com"), any(), any(), eq("7"), any(), argThat(value -> value.toString().contains("Provider recovered")), eq("cid"));
    }

    @Test void sentAlertCanBeAcknowledgedButPendingAlertCannotBeSuppressed() {
        proof(); OwnerOperationalAlertEntity sent = alert("SENT"); when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(sent)); when(repository.save(sent)).thenReturn(sent);
        assertEquals("ACKNOWLEDGED", service.acknowledge(7L, "owner@example.com", "proof", "Reviewed", "cid").status());
        OwnerOperationalAlertEntity pending = alert("PENDING"); when(repository.findByIdForUpdate(8L)).thenReturn(Optional.of(pending));
        assertThrows(IllegalArgumentException.class, () -> service.acknowledge(8L, "owner@example.com", "proof", "Skip", "cid"));
    }

    @Test void wrongPurposeOrStaleProofRejectsMutationBeforeLockingRow() {
        when(jwt.isAdminReauthenticationTokenValid("bad", "owner@example.com", AdminReauthenticationPurpose.OWNER_ALERT_ACTION)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.retry(7L, "owner@example.com", "bad", "reason", "cid"));
        verify(repository, never()).findByIdForUpdate(anyLong());
    }

    private void proof() { when(jwt.isAdminReauthenticationTokenValid("proof", "owner@example.com", AdminReauthenticationPurpose.OWNER_ALERT_ACTION)).thenReturn(true); }
    private OwnerOperationalAlertEntity alert(String status) { OwnerOperationalAlertEntity value = new OwnerOperationalAlertEntity(); value.setId(7L); value.setCategory("BACKEND_ERROR"); value.setSeverity("CRITICAL"); value.setStatus(status); value.setTitleEn("Title"); value.setTitleTr("Başlık"); value.setMessageEn("Message"); value.setMessageTr("Mesaj"); value.setTargetPath("/admin/system/errors"); value.setOccurrenceCount(1L); value.setFirstOccurredAt(Instant.now()); value.setLastOccurredAt(Instant.now()); value.setAttemptCount(5); value.setLastErrorType("ProviderException"); value.setUpdatedAt(Instant.now()); return value; }
}
