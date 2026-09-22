package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.enums.AdminApprovalActionType;
import com.grun.calorietracker.service.OwnerOperationalAlertService;
import com.grun.calorietracker.service.support.OwnerApprovalRequestedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class OwnerApprovalAlertEventListenerTest {
    private final OwnerOperationalAlertService alerts = mock(OwnerOperationalAlertService.class);
    private final OwnerApprovalAlertEventListener listener = new OwnerApprovalAlertEventListener(alerts);

    @Test
    void delegatesCommittedApprovalWithoutSensitiveMakerOrPayloadData() {
        Instant createdAt = Instant.parse("2026-09-17T14:00:00Z");
        listener.onApprovalRequested(new OwnerApprovalRequestedEvent(41L, AdminApprovalActionType.AI_QUOTA_REFUND, createdAt));
        verify(alerts).recordApprovalRequired(41L, AdminApprovalActionType.AI_QUOTA_REFUND, createdAt);
    }

    @Test
    void alertPersistenceFailureDoesNotEscapeTheAfterCommitListener() {
        doThrow(new IllegalStateException("database unavailable")).when(alerts)
                .recordApprovalRequired(anyLong(), any(), any());
        assertDoesNotThrow(() -> listener.onApprovalRequested(new OwnerApprovalRequestedEvent(
                41L, AdminApprovalActionType.RUNTIME_POLICY_UPDATE, Instant.now())));
    }
}
