package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.BrandedDuplicateDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "food_branded_duplicate_decision_audits")
public class FoodBrandedDuplicateDecisionAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 300)
    private String brandKey;
    @Column(nullable = false, length = 500)
    private String nameKey;
    @Column(nullable = false, length = 20)
    private String action;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private BrandedDuplicateDecision previousDecision;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private BrandedDuplicateDecision newDecision;
    private Long previousSurvivorFoodItemId;
    private Long newSurvivorFoodItemId;
    @Column(length = 64)
    private String candidateFingerprint;
    @Column(nullable = false, length = 1000)
    private String reason;
    @Column(nullable = false, length = 255)
    private String reviewedBy;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void createTimestamp() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
