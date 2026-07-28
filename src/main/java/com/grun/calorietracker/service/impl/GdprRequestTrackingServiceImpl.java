package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.GdprAdminRequestEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.GdprAdminRequestRepository;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.GdprRequestTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GdprRequestTrackingServiceImpl implements GdprRequestTrackingService {
    private final GdprAdminRequestRepository repository;
    private final AdminAuditService auditService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long begin(String userEmail, GdprRequestType type) {
        Instant now = Instant.now();
        GdprAdminRequestEntity entity = new GdprAdminRequestEntity();
        entity.setRequestType(type);
        entity.setStatus(GdprRequestStatus.IN_PROGRESS);
        entity.setSubjectReference(subjectReference(userEmail));
        entity.setRequestedAt(now);
        entity.setDueAt(now.plus(Duration.ofDays(type == GdprRequestType.DELETE ? 30 : 7)));
        return repository.save(entity).getId();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long id, String resultCode) {
        GdprAdminRequestEntity entity = require(id);
        entity.setStatus(GdprRequestStatus.COMPLETED);
        entity.setCompletedAt(Instant.now());
        entity.setResultCode(trim(resultCode, 64));
        entity.setEvidenceReference("gdpr-request:" + id);
        repository.save(entity);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long id, RuntimeException exception) {
        GdprAdminRequestEntity entity = require(id);
        entity.setStatus(GdprRequestStatus.FAILED);
        entity.setCompletedAt(Instant.now());
        entity.setResultCode("OPERATION_FAILED");
        entity.setFailureSummary(trim(exception.getClass().getSimpleName(), 300));
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminGdprRequestPageDto list(GdprRequestStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50),
                Sort.by(Sort.Direction.DESC, "requestedAt"));
        Page<GdprAdminRequestEntity> result = status == null
                ? repository.findAll(pageable)
                : repository.findByStatus(status, pageable);
        return new AdminGdprRequestPageDto(result.getContent().stream().map(this::toDto).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
                result.isFirst(), result.isLast());
    }

    @Override
    @Transactional
    public AdminGdprRequestDto update(Long id, AdminGdprRequestUpdateDto request, String adminEmail,
                                      String correlationId) {
        GdprAdminRequestEntity entity = require(id);
        Map<String, Object> before = Map.of("status", entity.getStatus(),
                "assigned", entity.getAssignedTo() == null ? "" : entity.getAssignedTo());
        entity.setAssignedTo(trim(request.assignedTo(), 320));
        if (request.escalated() && entity.getStatus() != GdprRequestStatus.COMPLETED) {
            entity.setStatus(GdprRequestStatus.ESCALATED);
            entity.setEscalatedAt(Instant.now());
            entity.setFailureSummary(trim(request.reason(), 300));
        }
        GdprAdminRequestEntity saved = repository.save(entity);
        auditService.record(adminEmail, AdminAuditActionType.GDPR_REQUEST_UPDATE,
                AdminAuditTargetType.GDPR_REQUEST, id.toString(), before,
                Map.of("status", saved.getStatus(), "assigned", saved.getAssignedTo() == null ? "" : saved.getAssignedTo()),
                correlationId);
        return toDto(saved);
    }

    private GdprAdminRequestEntity require(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("GDPR request was not found."));
    }

    private String subjectReference(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(email.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create GDPR subject reference.");
        }
    }

    private String trim(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim().replaceAll("[\\r\\n\\t]+", " ");
        return clean.substring(0, Math.min(clean.length(), max));
    }

    private AdminGdprRequestDto toDto(GdprAdminRequestEntity entity) {
        return new AdminGdprRequestDto(entity.getId(), entity.getRequestType(), entity.getStatus(),
                entity.getSubjectReference(), entity.getRequestedAt(), entity.getDueAt(), entity.getCompletedAt(),
                entity.getAssignedTo(), entity.getResultCode(), entity.getEvidenceReference(),
                entity.getFailureSummary(), entity.getEscalatedAt());
    }
}
