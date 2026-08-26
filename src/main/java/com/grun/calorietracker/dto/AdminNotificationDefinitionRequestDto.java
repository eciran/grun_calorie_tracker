package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.NotificationCampaignChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminNotificationDefinitionRequestDto {
    @NotBlank
    @Pattern(regexp = "[a-z0-9_]+")
    @Size(max = 80)
    private String key;

    @NotBlank
    @Size(max = 120)
    private String displayName;

    @Size(max = 500)
    private String description;

    private boolean enabled = true;

    @NotNull
    private NotificationCampaignChannel channel;

    @Pattern(regexp = "INFO|WARNING|CRITICAL")
    private String severity;

    @Size(max = 255)
    private String targetRoute;

    @Size(max = 120)
    private String titleEn;
    @Size(max = 1000)
    private String messageEn;
    @Size(max = 120)
    private String titleTr;
    @Size(max = 1000)
    private String messageTr;
}
