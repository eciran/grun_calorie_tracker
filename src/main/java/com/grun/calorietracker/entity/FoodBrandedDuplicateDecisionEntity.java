package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.BrandedDuplicateDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "food_branded_duplicate_decisions", uniqueConstraints =
        @UniqueConstraint(name = "uq_food_branded_duplicate_identity", columnNames = {"brand_key", "name_key"}))
public class FoodBrandedDuplicateDecisionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String brandKey;

    @Column(nullable = false, length = 500)
    private String nameKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BrandedDuplicateDecision decision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survivor_food_item_id")
    private FoodItemEntity survivorFoodItem;

    @Column(nullable = false, length = 64)
    private String candidateFingerprint;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false, length = 255)
    private String reviewedBy;

    @Column(nullable = false)
    private LocalDateTime reviewedAt;

    @Version
    private Long version;

    @PrePersist
    @PreUpdate
    void touch() {
        reviewedAt = LocalDateTime.now();
    }
}
