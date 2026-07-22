package com.grun.calorietracker.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminNotificationCampaignScheduleRequestDto {
    private LocalDateTime scheduledAt;
}