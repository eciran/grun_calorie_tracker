package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "General admin action audit entry.")
public class AdminActionAuditDto {
    private Long id;
    private String adminEmail;
    private AdminAuditActionType actionType;
    private AdminAuditTargetType targetType;
    private String targetKey;
    private String oldValue;
    private String newValue;
    private String correlationId;
    private LocalDateTime createdAt;
}
