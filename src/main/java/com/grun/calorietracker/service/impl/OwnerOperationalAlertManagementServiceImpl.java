package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.OwnerOperationalAlertDto;
import com.grun.calorietracker.entity.OwnerOperationalAlertEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.OwnerOperationalAlertRepository;
import com.grun.calorietracker.security.JwtUtil;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.OwnerOperationalAlertManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OwnerOperationalAlertManagementServiceImpl implements OwnerOperationalAlertManagementService {
    private final OwnerOperationalAlertRepository repository;
    private final JwtUtil jwtUtil;
    private final AdminAuditService auditService;

    @Override @Transactional
    public OwnerOperationalAlertDto retry(long id, String ownerEmail, String token, String reason, String correlationId) {
        requireProof(ownerEmail, token); OwnerOperationalAlertEntity alert = require(id);
        if (!"FAILED".equals(alert.getStatus())) throw new IllegalArgumentException("Only failed owner alerts can be queued again.");
        String oldStatus = alert.getStatus(); alert.setStatus("RETRY"); alert.setAttemptCount(0);
        alert.setNextAttemptAt(Instant.now()); alert.setSentAt(null); alert.setLastErrorType(null); alert.setUpdatedAt(Instant.now());
        OwnerOperationalAlertEntity saved = repository.save(alert);
        auditService.record(ownerEmail, AdminAuditActionType.OWNER_ALERT_RETRY, AdminAuditTargetType.OWNER_OPERATIONAL_ALERT,
                Long.toString(id), Map.of("status", oldStatus), Map.of("status", saved.getStatus(), "reason", reason.trim()), correlationId);
        return OwnerOperationalAlertDto.from(saved);
    }

    @Override @Transactional
    public OwnerOperationalAlertDto acknowledge(long id, String ownerEmail, String token, String reason, String correlationId) {
        requireProof(ownerEmail, token); OwnerOperationalAlertEntity alert = require(id);
        if (!Set.of("SENT", "FAILED").contains(alert.getStatus())) throw new IllegalArgumentException("Only sent or failed owner alerts can be acknowledged.");
        String oldStatus = alert.getStatus(); alert.setStatus("ACKNOWLEDGED"); alert.setNextAttemptAt(null); alert.setUpdatedAt(Instant.now());
        OwnerOperationalAlertEntity saved = repository.save(alert);
        auditService.record(ownerEmail, AdminAuditActionType.OWNER_ALERT_ACKNOWLEDGE, AdminAuditTargetType.OWNER_OPERATIONAL_ALERT,
                Long.toString(id), Map.of("status", oldStatus), Map.of("status", saved.getStatus(), "reason", reason.trim()), correlationId);
        return OwnerOperationalAlertDto.from(saved);
    }

    private void requireProof(String email, String token) {
        if (email == null || token == null || !jwtUtil.isAdminReauthenticationTokenValid(token, email, AdminReauthenticationPurpose.OWNER_ALERT_ACTION))
            throw new IllegalArgumentException("Fresh owner MFA re-authentication is required.");
    }
    private OwnerOperationalAlertEntity require(long id) {
        if (id <= 0) throw new IllegalArgumentException("Owner alert id must be positive.");
        return repository.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Owner alert was not found."));
    }
}
