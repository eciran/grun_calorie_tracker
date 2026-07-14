package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodEvidenceBasis;
import com.grun.calorietracker.enums.FoodEvidenceField;
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
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "food_product_source_evidence")
@Data
@NoArgsConstructor
public class FoodProductSourceEvidenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_item_id", nullable = false)
    private FoodItemEntity foodItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FoodDataSource provider;

    @Column(nullable = false, length = 255)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private FoodEvidenceField fieldName;

    @Column(nullable = false)
    private Double numericValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FoodEvidenceBasis basis;

    @Column(nullable = false)
    private Integer confidenceScore;

    @Column(nullable = false)
    private LocalDateTime observedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, length = 64, unique = true, updatable = false)
    private String fingerprint;

    @Column(length = 120)
    private String sourceVersion;

    @Column(length = 255)
    private String reviewerIdentity;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (observedAt == null) {
            observedAt = createdAt;
        }
    }
}