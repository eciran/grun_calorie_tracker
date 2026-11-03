package com.grun.calorietracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "ai_quota_promotion_policy")
@Data
public class AiQuotaPromotionPolicyEntity {
    @Id private Long id;
    @Version private Long version;
    @Column(nullable = false) private Boolean enabled;
    @Column(nullable = false) private Integer minimumIntervalHours;
    @Column(name = "max_impressions_24h", nullable = false) private Integer maxImpressions24h;
    @Column(nullable = false) private Integer dismissCooldownHours;
    @Column(nullable = false) private Integer rolloutPercentage;
    @Column(nullable = false) private Long campaignVersion;
    @Column(nullable = false, length = 500) private String changeReason;
    @Column(nullable = false, length = 255) private String updatedBy;
    @Column(nullable = false) private Instant updatedAt;
}
