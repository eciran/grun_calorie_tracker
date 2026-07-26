package com.grun.calorietracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_operations_policy")
@Data
public class AiOperationsPolicyEntity {
    @Id
    private Long id;
    @Version
    private Long version;
    @Column(nullable = false)
    private Boolean circuitOpen;
    @Column(nullable = false)
    private Double failureRateThreshold;
    @Column(nullable = false)
    private Double rejectionRateThreshold;
    @Column(name = "max_tokens_per24_hours", nullable = false)
    private Long maxTokensPer24Hours;
    @Column(name = "max_cost_per24_hours", nullable = false)
    private Double maxCostPer24Hours;
    @Column(nullable = false, length = 12)
    private String costCurrency;
    @Column(length = 120)
    private String activeModel;
    @Column(length = 120)
    private String activePromptVersion;
    @Column(length = 120)
    private String previousModel;
    @Column(length = 120)
    private String previousPromptVersion;
    @Column(nullable = false, length = 500)
    private String changeReason;
    @Column(nullable = false, length = 255)
    private String updatedBy;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}