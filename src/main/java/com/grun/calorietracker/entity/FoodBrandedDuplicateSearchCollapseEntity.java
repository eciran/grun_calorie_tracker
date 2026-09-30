package com.grun.calorietracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "food_branded_duplicate_search_collapses", uniqueConstraints =
        @UniqueConstraint(name = "uq_food_branded_duplicate_search_collapse_decision", columnNames = "decision_id"))
public class FoodBrandedDuplicateSearchCollapseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false)
    private FoodBrandedDuplicateDecisionEntity decision;

    @Column(nullable = false, length = 300)
    private String brandKey;

    @Column(nullable = false, length = 500)
    private String nameKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survivor_food_item_id", nullable = false)
    private FoodItemEntity survivorFoodItem;

    @Column(nullable = false, length = 64)
    private String candidateFingerprint;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false, length = 255)
    private String appliedBy;

    @Column(nullable = false)
    private LocalDateTime appliedAt;

    @Column(nullable = false)
    private boolean active;

    @Column(length = 255)
    private String revertedBy;

    private LocalDateTime revertedAt;

    @Column(length = 1000)
    private String revertReason;

    @Version
    private Long version;

    @PrePersist
    void initialize() {
        if (appliedAt == null) appliedAt = LocalDateTime.now();
    }
}
