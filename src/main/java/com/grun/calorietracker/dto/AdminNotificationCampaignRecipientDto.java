package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.NotificationCampaignRecipientStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class AdminNotificationCampaignRecipientDto {
    private Long id;
    private String userReference;
    private NotificationCampaignRecipientStatus status;
    private Integer pushSent;
    private Integer pushFailed;
    private String suppressionReason;
    private LocalDateTime processedAt;
    private LocalDateTime openedAt;
    private LocalDateTime clickedAt;
    private LocalDateTime dismissedAt;
    private LocalDateTime convertedAt;
}
