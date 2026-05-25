package com.grun.calorietracker.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RevenueCatConfigStatusDto {
    private boolean webhookAuthorizationConfigured;
    private boolean strictProductMapping;
    private List<String> plusEntitlements;
    private List<String> proEntitlements;
    private List<String> plusProductIds;
    private List<String> proProductIds;
    private Map<String, Integer> aiAddonQuotas;
    private Map<String, Integer> aiAddonValidityDays;
    private int defaultAiAddonValidityDays;
}
