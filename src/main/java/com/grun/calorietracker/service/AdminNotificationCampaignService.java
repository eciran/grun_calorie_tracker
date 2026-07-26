package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.enums.NotificationCampaignStatus;

import java.time.LocalDateTime;

public interface AdminNotificationCampaignService {
    AdminNotificationCampaignPageDto list(NotificationCampaignStatus status, int page, int size);
    AdminNotificationCampaignDto get(Long id);
    AdminNotificationCampaignDto create(AdminNotificationCampaignRequestDto request, String adminEmail, String correlationId);
    AdminNotificationCampaignDto update(Long id, AdminNotificationCampaignRequestDto request, String adminEmail, String correlationId);
    AdminNotificationCampaignPreviewDto preview(Long id);
    AdminNotificationCampaignDto schedule(Long id, LocalDateTime scheduledAt, String adminEmail, String correlationId);
    AdminNotificationCampaignDto cancel(Long id, String adminEmail, String correlationId);
}