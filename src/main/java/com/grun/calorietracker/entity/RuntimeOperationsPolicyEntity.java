package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.RuntimeRolloutSegment;
import com.grun.calorietracker.enums.SubscriptionFeature;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "runtime_operations_policy")
public class RuntimeOperationsPolicyEntity {
    @Id
    private Long id;
    @Version
    private Long version;
    @Column(nullable = false)
    private Boolean maintenanceEnabled;
    @Column(nullable = false, length = 240)
    private String maintenanceMessage;
    @Column(nullable = false, length = 80)
    private String releaseVersion;
    @Column(nullable = false, length = 40)
    private String deploymentEnvironment;
    @Column(nullable = false, length = 40)
    private String minimumIosVersion;
    @Column(nullable = false, length = 40)
    private String minimumAndroidVersion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private SubscriptionFeature rolloutFeature;
    @Column(nullable = false)
    private Boolean rolloutEnabled;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private SubscriptionPlan rolloutPlan;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MarketRegion rolloutRegion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RuntimeRolloutSegment rolloutSegment;
    @Column(nullable = false)
    private Integer rolloutPercentage;
    @Column(nullable = false)
    private Long apiLatencyWarningMs;
    @Column(nullable = false)
    private Double apiErrorRateThreshold;
    @Column(length = 255)
    private String escalationTarget;
    @Column(columnDefinition = "TEXT")
    private String previousSnapshot;
    @Column(nullable = false, length = 500)
    private String changeReason;
    @Column(nullable = false, length = 255)
    private String updatedBy;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
