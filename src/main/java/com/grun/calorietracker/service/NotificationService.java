package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.NotificationDto;
import com.grun.calorietracker.dto.NotificationPageDto;
import com.grun.calorietracker.dto.NotificationReadAllResponseDto;
import com.grun.calorietracker.enums.NotificationEngagementType;

public interface NotificationService {
    NotificationPageDto listNotifications(String email, Boolean unreadOnly, String type, String severity, int page, int size);
    NotificationDto markAsRead(String email, Long notificationId);
    NotificationReadAllResponseDto markAllAsRead(String email);
    NotificationDto recordEngagement(String email, Long notificationId, NotificationEngagementType engagementType);
}
