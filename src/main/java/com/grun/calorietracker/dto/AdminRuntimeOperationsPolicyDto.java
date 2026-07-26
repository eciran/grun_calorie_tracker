package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.RuntimeRolloutSegment;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.SubscriptionPlan;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminRuntimeOperationsPolicyDto {
    private Long version;
    private Boolean maintenanceEnabled;
    private String maintenanceMessage;
    private String releaseVersion;
    private String deploymentEnvironment;
    private String minimumIosVersion;
    private String minimumAndroidVersion;
    private SubscriptionFeature rolloutFeature;
    private Boolean rolloutEnabled;
    private SubscriptionPlan rolloutPlan;
    private MarketRegion rolloutRegion;
    private RuntimeRolloutSegment rolloutSegment;
    private Integer rolloutPercentage;
    private Long apiLatencyWarningMs;
    private Double apiErrorRateThreshold;
    private String escalationTarget;
    private Boolean rollbackAvailable;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
