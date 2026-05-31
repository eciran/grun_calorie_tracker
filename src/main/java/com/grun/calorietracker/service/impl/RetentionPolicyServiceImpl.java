package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.RetentionPolicyDto;
import com.grun.calorietracker.dto.RetentionPolicyUpdateRequestDto;
import com.grun.calorietracker.entity.RetentionPolicyEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.RetentionPolicyKey;
import com.grun.calorietracker.repository.RetentionPolicyRepository;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.RetentionPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RetentionPolicyServiceImpl implements RetentionPolicyService {

    private final RetentionPolicyRepository retentionPolicyRepository;
    private final AdminAuditService adminAuditService;

    @Override
    @Transactional(readOnly = true)
    public List<RetentionPolicyDto> listPolicies() {
        return retentionPolicyRepository.findAll().stream()
                .sorted(Comparator.comparing(policy -> policy.getPolicyKey().name()))
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public RetentionPolicyDto upsertPolicy(String adminEmail, RetentionPolicyKey key, RetentionPolicyUpdateRequestDto request) {
        RetentionPolicyEntity entity = retentionPolicyRepository.findByPolicyKey(key)
                .orElseGet(() -> {
                    RetentionPolicyEntity created = new RetentionPolicyEntity();
                    created.setPolicyKey(key);
                    return created;
                });
        RetentionPolicyDto oldValue = entity.getId() == null ? null : toDto(entity);
        entity.setRetentionDays(request.getRetentionDays());
        entity.setLegalBasis(request.getLegalBasis().trim());
        entity.setDescription(request.getDescription().trim());
        entity.setActive(request.getActive());
        entity.setUpdatedBy(adminEmail);
        entity.setUpdatedAt(LocalDateTime.now());
        RetentionPolicyDto newValue = toDto(retentionPolicyRepository.save(entity));
        adminAuditService.record(
                adminEmail,
                AdminAuditActionType.RETENTION_POLICY_UPDATE,
                AdminAuditTargetType.RETENTION_POLICY,
                key.name(),
                oldValue,
                newValue,
                null
        );
        return newValue;
    }

    private RetentionPolicyDto toDto(RetentionPolicyEntity entity) {
        return new RetentionPolicyDto(
                entity.getId(),
                entity.getPolicyKey(),
                entity.getRetentionDays(),
                entity.getLegalBasis(),
                entity.getDescription(),
                entity.getActive(),
                entity.getUpdatedBy(),
                entity.getUpdatedAt()
        );
    }
}
