package com.grun.calorietracker.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RevenueCatChartDto {
    private String chartName;
    private String label;
    private String environment;
    private String currency;
    private boolean providerReachable;
    private String statusMessage;
    private List<RevenueCatChartPointDto> points = new ArrayList<>();
}
