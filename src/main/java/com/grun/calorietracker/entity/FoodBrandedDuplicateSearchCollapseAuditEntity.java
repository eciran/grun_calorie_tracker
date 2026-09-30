package com.grun.calorietracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "food_branded_duplicate_search_collapse_audits")
public class FoodBrandedDuplicateSearchCollapseAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long decisionId;

    @Column(nullable = false, length = 300)
    private String brandKey;

    @Column(nullable = false, length = 500)
    private String nameKey;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(nullable = false)
    private Long survivorFoodItemId;

    @Column(nullable = false, length = 64)
    private String candidateFingerprint;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String suppressedFoodItemIds;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false, length = 255)
    private String performedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void initialize() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
