package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.DevicePlatform;
import com.grun.calorietracker.enums.PushProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Registered push token metadata. Raw token is never returned.")
public class PushTokenDto {
    private Long id;
    private PushProvider provider;
    private DevicePlatform platform;
    private String deviceId;
    private String tokenPreview;
    private Boolean enabled;
    private LocalDateTime lastSeenAt;
    private LocalDateTime revokedAt;
}
