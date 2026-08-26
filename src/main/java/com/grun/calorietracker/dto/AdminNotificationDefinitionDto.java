package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.NotificationCampaignChannel;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminNotificationDefinitionDto {
    private Long id;
    private Long version;
    private String key;
    private String displayName;
    private String description;
    private boolean enabled;
    private boolean protectedDefinition;
    private NotificationCampaignChannel channel;
    private String severity;
    private String targetRoute;
    private String titleEn;
    private String messageEn;
    private String titleTr;
    private String messageTr;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
