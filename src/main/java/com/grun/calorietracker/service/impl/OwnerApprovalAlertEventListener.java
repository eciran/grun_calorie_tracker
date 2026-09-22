package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.service.OwnerOperationalAlertService;
import com.grun.calorietracker.service.support.OwnerApprovalRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OwnerApprovalAlertEventListener {
    private static final Logger log = LoggerFactory.getLogger(OwnerApprovalAlertEventListener.class);
    private final OwnerOperationalAlertService alerts;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApprovalRequested(OwnerApprovalRequestedEvent event) {
        try {
            alerts.recordApprovalRequired(event.approvalId(), event.actionType(), event.createdAt());
        } catch (RuntimeException failure) {
            log.warn("owner_approval_alert_record_failed approvalId={} type={}", event.approvalId(), failure.getClass().getSimpleName());
        }
    }
}
