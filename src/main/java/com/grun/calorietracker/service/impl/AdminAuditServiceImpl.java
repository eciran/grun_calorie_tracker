package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.AdminActionAuditDto;
import com.grun.calorietracker.dto.AdminActionAuditPageDto;
import com.grun.calorietracker.entity.AdminActionAuditEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.repository.AdminActionAuditRepository;
import com.grun.calorietracker.service.AdminAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class AdminAuditServiceImpl implements AdminAuditService {

    private final AdminActionAuditRepository adminActionAuditRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void record(String adminEmail,
                       AdminAuditActionType actionType,
                       AdminAuditTargetType targetType,
                       String targetKey,
                       Object oldValue,
                       Object newValue,
                       String correlationId) {
        AdminActionAuditEntity entity = new AdminActionAuditEntity();
        entity.setAdminEmail(adminEmail);
        entity.setActionType(actionType);
        entity.setTargetType(targetType);
        entity.setTargetKey(targetKey);
        entity.setOldValue(toAuditValue(oldValue));
        entity.setNewValue(toAuditValue(newValue));
        entity.setCorrelationId(correlationId);
        entity.setCreatedAt(LocalDateTime.now());
        adminActionAuditRepository.save(entity);
    }

    @Override
    public AdminActionAuditPageDto list(AdminAuditActionType actionType,
                                        AdminAuditTargetType targetType,
                                        int page,
                                        int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<AdminActionAuditEntity> audits;
        if (actionType != null && targetType != null) {
            audits = adminActionAuditRepository.findByActionTypeAndTargetType(actionType, targetType, pageable);
        } else if (actionType != null) {
            audits = adminActionAuditRepository.findByActionType(actionType, pageable);
        } else if (targetType != null) {
            audits = adminActionAuditRepository.findByTargetType(targetType, pageable);
        } else {
            audits = adminActionAuditRepository.findAll(pageable);
        }
        AdminActionAuditPageDto dto = new AdminActionAuditPageDto();
        dto.setContent(audits.getContent().stream().map(this::toDto).toList());
        dto.setPage(audits.getNumber());
        dto.setSize(audits.getSize());
        dto.setTotalElements(audits.getTotalElements());
        dto.setTotalPages(audits.getTotalPages());
        dto.setFirst(audits.isFirst());
        dto.setLast(audits.isLast());
        return dto;
    }

    @Override
    public byte[] exportCsv(AdminAuditActionType actionType, AdminAuditTargetType targetType) {
        StringBuilder csv = new StringBuilder(
                "id,createdAt,adminEmail,actionType,targetType,targetKey,correlationId,oldValue,newValue\n"
        );
        for (int page = 0; page < 100; page++) {
            AdminActionAuditPageDto result = list(actionType, targetType, page, 100);
            result.getContent().forEach(item -> csv.append(csv(item.getId())).append(',')
                    .append(csv(item.getCreatedAt())).append(',')
                    .append(csv(item.getAdminEmail())).append(',')
                    .append(csv(item.getActionType())).append(',')
                    .append(csv(item.getTargetType())).append(',')
                    .append(csv(item.getTargetKey())).append(',')
                    .append(csv(item.getCorrelationId())).append(',')
                    .append(csv(item.getOldValue())).append(',')
                    .append(csv(item.getNewValue())).append('\n'));
            if (result.isLast()) break;
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csv(Object value) {
        if (value == null) return "";
        return "\"" + value.toString().replace("\"", "\"\"") + "\"";
    }
    private String toAuditValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return value.toString();
        }
    }

    private AdminActionAuditDto toDto(AdminActionAuditEntity entity) {
        AdminActionAuditDto dto = new AdminActionAuditDto();
        dto.setId(entity.getId());
        dto.setAdminEmail(entity.getAdminEmail());
        dto.setActionType(entity.getActionType());
        dto.setTargetType(entity.getTargetType());
        dto.setTargetKey(entity.getTargetKey());
        dto.setOldValue(entity.getOldValue());
        dto.setNewValue(entity.getNewValue());
        dto.setCorrelationId(entity.getCorrelationId());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
