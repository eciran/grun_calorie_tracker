package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminNotificationDefinitionDto;
import com.grun.calorietracker.dto.AdminNotificationDefinitionRequestDto;

import java.util.List;

public interface AdminNotificationDefinitionService {
    List<AdminNotificationDefinitionDto> list();
    AdminNotificationDefinitionDto create(AdminNotificationDefinitionRequestDto request, String adminEmail, String correlationId);
    AdminNotificationDefinitionDto update(Long id, AdminNotificationDefinitionRequestDto request, String adminEmail, String correlationId);
    AdminNotificationDefinitionDto publishProtected(Long id, AdminNotificationDefinitionRequestDto request,
            String adminEmail, String correlationId);
}
