package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.HealthConnectionStatus;
import com.grun.calorietracker.enums.HealthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Third-party health provider connection state for the authenticated user.")
public class HealthConnectionDto {

    @Schema(description = "Internal connection id.", example = "1")
    private Long id;

    @Schema(description = "Health data provider.", example = "APPLE_HEALTH")
    private HealthProvider provider;

    @Schema(description = "Current provider connection status.", example = "CONNECTED")
    private HealthConnectionStatus status;

    @Schema(description = "Provider-specific user id when available.", example = "apple-health-device")
    private String providerUserId;

    @Schema(description = "Device model used for client-side health data sync.", example = "iPhone 15")
    private String deviceModel;

    @Schema(description = "Mobile app version that registered the connection.", example = "1.0.0")
    private String appVersion;

    @Schema(description = "Connection timestamp.", example = "2026-05-26T21:55:00")
    private String connectedAt;

    @Schema(description = "Disconnection timestamp.", example = "2026-05-26T21:55:00")
    private String disconnectedAt;

    @Schema(description = "Last successful metric sync timestamp.", example = "2026-05-26T21:55:00")
    private String lastSyncAt;
}
