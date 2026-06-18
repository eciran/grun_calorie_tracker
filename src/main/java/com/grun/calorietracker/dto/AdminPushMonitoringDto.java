package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.PushProvider;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class AdminPushMonitoringDto {
    private Boolean enabled;
    private PushProvider provider;
    private Long activeTokenCount;
    private Map<PushProvider, Long> activeTokensByProvider;
    private Long sentLast24h;
    private Long failedLast24h;
    private Boolean expoConfigured;
    private Boolean fcmConfigured;
    private Boolean oneSignalConfigured;
}
