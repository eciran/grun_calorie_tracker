package com.grun.calorietracker.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class RevenueCatMonitoringChartsDto {
    private String environment;
    private String currency;
    private boolean providerReachable;
    private String statusMessage;
    private LocalDateTime checkedAt;
    private List<RevenueCatChartDto> charts = new ArrayList<>();
}
