package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminCustomer360Dto;
import com.grun.calorietracker.dto.AdminUserSupportNoteDto;
import com.grun.calorietracker.dto.AdminUserSupportNoteRequestDto;
import com.grun.calorietracker.dto.AdminUserNotificationRequestDto;

public interface AdminCustomer360Service {
    AdminCustomer360Dto getCustomer(Long userId);

    AdminUserSupportNoteDto addSupportNote(Long userId,
                                           AdminUserSupportNoteRequestDto request,
                                           String adminEmail);

    AdminCustomer360Dto.NotificationItem sendNotification(Long userId,
                                                           AdminUserNotificationRequestDto request);

    int revokeActiveSessions(Long userId);
}
