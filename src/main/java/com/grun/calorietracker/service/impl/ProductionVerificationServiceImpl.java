package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.ProductionVerificationRunEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.ProductionVerificationRunRepository;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.ProductionVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProductionVerificationServiceImpl implements ProductionVerificationService {
    private static final Pattern SECRET_PATTERN = Pattern.compile("(?i)(bearer\\s+|api[_-]?key|authorization|secret=|token=)");
    private final ProductionVerificationRunRepository repository;
    private final AdminAuditService auditService;

    @Override
    @Transactional
    public ProductionVerificationRunDto record(ProductionVerificationRunRequestDto request, String adminEmail,
                                                String correlationId) {
        rejectSecrets(request.evidenceReference());
        rejectSecrets(request.summary());
        ProductionVerificationRunEntity entity = new ProductionVerificationRunEntity();
        entity.setProvider(request.provider());
        entity.setEnvironment(request.environment());
        entity.setScenario(request.scenario());
        entity.setStatus(request.status());
        entity.setEvidenceReference(clean(request.evidenceReference()));
        entity.setSummary(clean(request.summary()));
        entity.setExecutedBy(adminEmail);
        entity.setExecutedAt(Instant.now());
        entity.setValidUntil(request.validUntil());
        ProductionVerificationRunEntity saved = repository.save(entity);
        auditService.record(adminEmail, AdminAuditActionType.PRODUCTION_VERIFICATION_RECORD,
                AdminAuditTargetType.PRODUCTION_VERIFICATION, saved.getId().toString(), null,
                Map.of("provider", saved.getProvider(), "scenario", saved.getScenario(), "status", saved.getStatus()),
                correlationId);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductionVerificationRunPageDto list(String provider, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50),
                Sort.by(Sort.Direction.DESC, "executedAt"));
        Page<ProductionVerificationRunEntity> result = provider == null || provider.isBlank()
                ? repository.findAll(pageable)
                : repository.findByProvider(provider, pageable);
        return new ProductionVerificationRunPageDto(result.getContent().stream().map(this::toDto).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
                result.isFirst(), result.isLast());
    }

    private void rejectSecrets(String value) {
        if (value != null && SECRET_PATTERN.matcher(value).find()) {
            throw new IllegalArgumentException("Verification evidence must not contain credentials or tokens.");
        }
    }

    private String clean(String value) {
        return value.trim().replaceAll("[\\r\\n\\t]+", " ");
    }

    private ProductionVerificationRunDto toDto(ProductionVerificationRunEntity entity) {
        return new ProductionVerificationRunDto(entity.getId(), entity.getProvider(), entity.getEnvironment(),
                entity.getScenario(), entity.getStatus(), entity.getEvidenceReference(), entity.getSummary(),
                entity.getExecutedBy(), entity.getExecutedAt(), entity.getValidUntil(),
                entity.getValidUntil() != null && entity.getValidUntil().isBefore(Instant.now()));
    }
}
