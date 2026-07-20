package com.grun.calorietracker.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "food_canonical_resolutions")
@Data
@NoArgsConstructor
public class FoodCanonicalResolutionEntity {

    @Id
    private String canonicalFoodKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "primary_food_item_id", nullable = false)
    private FoodItemEntity primaryFoodItem;

    private String resolvedBy;
    private LocalDateTime resolvedAt;

    @PrePersist
    void onCreate() {
        if (resolvedAt == null) {
            resolvedAt = LocalDateTime.now();
        }
    }
}