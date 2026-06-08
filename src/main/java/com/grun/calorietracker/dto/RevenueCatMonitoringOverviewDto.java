package com.grun.calorietracker.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class RevenueCatMonitoringOverviewDto {
    private String environment;
    private String currency;
    private boolean apiEnabled;
    private boolean apiSecretConfigured;
    private boolean apiProjectConfigured;
    private boolean providerReachable;
    private String statusMessage;
    private LocalDateTime checkedAt;
    private List<RevenueCatMetricCardDto> metrics = new ArrayList<>();
}
