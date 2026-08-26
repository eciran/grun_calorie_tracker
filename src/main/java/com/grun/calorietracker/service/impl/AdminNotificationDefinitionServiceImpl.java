package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminNotificationDefinitionDto;
import com.grun.calorietracker.dto.AdminNotificationDefinitionRequestDto;
import com.grun.calorietracker.entity.NotificationDefinitionEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.NotificationDefinitionRepository;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.AdminNotificationDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminNotificationDefinitionServiceImpl implements AdminNotificationDefinitionService {
    private final NotificationDefinitionRepository repository;
    private final AdminAuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<AdminNotificationDefinitionDto> list() {
        return repository.findAllByOrderByDisplayNameAsc().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public AdminNotificationDefinitionDto create(AdminNotificationDefinitionRequestDto request, String adminEmail, String correlationId) {
        String key = request.getKey().trim().toLowerCase(java.util.Locale.ROOT);
        if (repository.existsByKey(key)) throw new IllegalArgumentException("Notification definition key already exists.");
        NotificationDefinitionEntity entity = new NotificationDefinitionEntity();
        entity.setKey(key);
        entity.setProtectedDefinition(false);
        apply(entity, request);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedBy(adminEmail);
        entity.setUpdatedBy(adminEmail);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity = repository.save(entity);
        auditService.record(adminEmail, AdminAuditActionType.NOTIFICATION_DEFINITION_CREATE,
                AdminAuditTargetType.NOTIFICATION_DEFINITION, entity.getId().toString(), null, auditValue(entity), correlationId);
        return toDto(entity);
    }

    @Override
    @Transactional
    public AdminNotificationDefinitionDto update(Long id, AdminNotificationDefinitionRequestDto request, String adminEmail, String correlationId) {
        NotificationDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification definition not found"));
        if (!entity.getKey().equals(request.getKey().trim().toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Notification definition key cannot be changed.");
        }
        if (entity.isProtectedDefinition() && !request.isEnabled()) {
            throw new IllegalArgumentException("Protected security notifications cannot be disabled.");
        }
        Map<String, Object> before = auditValue(entity);
        apply(entity, request);
        entity.setUpdatedBy(adminEmail);
        entity.setUpdatedAt(LocalDateTime.now());
        entity = repository.save(entity);
        auditService.record(adminEmail, AdminAuditActionType.NOTIFICATION_DEFINITION_UPDATE,
                AdminAuditTargetType.NOTIFICATION_DEFINITION, entity.getId().toString(), before, auditValue(entity), correlationId);
        return toDto(entity);
    }

    private void apply(NotificationDefinitionEntity entity, AdminNotificationDefinitionRequestDto request) {
        entity.setDisplayName(request.getDisplayName().trim());
        entity.setDescription(trim(request.getDescription()));
        entity.setEnabled(request.isEnabled());
        entity.setChannel(request.getChannel());
        entity.setSeverity(trim(request.getSeverity()));
        entity.setTargetRoute(trim(request.getTargetRoute()));
        entity.setTitleEn(trim(request.getTitleEn()));
        entity.setMessageEn(trim(request.getMessageEn()));
        entity.setTitleTr(trim(request.getTitleTr()));
        entity.setMessageTr(trim(request.getMessageTr()));
    }

    private AdminNotificationDefinitionDto toDto(NotificationDefinitionEntity entity) {
        AdminNotificationDefinitionDto dto = new AdminNotificationDefinitionDto();
        dto.setId(entity.getId()); dto.setVersion(entity.getVersion()); dto.setKey(entity.getKey());
        dto.setDisplayName(entity.getDisplayName()); dto.setDescription(entity.getDescription());
        dto.setEnabled(entity.isEnabled()); dto.setProtectedDefinition(entity.isProtectedDefinition());
        dto.setChannel(entity.getChannel()); dto.setSeverity(entity.getSeverity()); dto.setTargetRoute(entity.getTargetRoute());
        dto.setTitleEn(entity.getTitleEn()); dto.setMessageEn(entity.getMessageEn());
        dto.setTitleTr(entity.getTitleTr()); dto.setMessageTr(entity.getMessageTr());
        dto.setUpdatedBy(entity.getUpdatedBy()); dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> auditValue(NotificationDefinitionEntity entity) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("key", entity.getKey()); value.put("enabled", entity.isEnabled());
        value.put("channel", entity.getChannel()); value.put("severity", entity.getSeverity());
        value.put("targetRoute", entity.getTargetRoute());
        return value;
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
