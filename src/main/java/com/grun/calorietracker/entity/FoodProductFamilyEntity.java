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
import jakarta.persistence.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "food_product_families")
public class FoodProductFamilyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 180)
    private String familyKey;

    @Column(nullable = false, length = 160)
    private String brandKey;

    @Column(nullable = false, length = 240)
    private String variantKey;

    @Column(nullable = false, length = 300)
    private String displayName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "representative_food_item_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private FoodItemEntity representativeFoodItem;

    @Column(nullable = false, length = 1000)
    private String evidenceReason;

    @Column(nullable = false, length = 255)
    private String appliedBy;

    @Column(length = 64)
    private String previewFingerprint;

    @Column(length = 4000)
    private String acknowledgedWarnings;

    @Column(nullable = false)
    private LocalDateTime appliedAt;

    @Column(nullable = false)
    private boolean active = true;

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
