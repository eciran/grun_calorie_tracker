package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "food_brand_aliases")
public class FoodBrandAliasEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)
    private FoodBrandEntity brand;
    @Column(nullable = false, length = 255)
    private String alias;
    @Column(nullable = false, length = 255)
    private String normalizedAlias;
    @Column(length = 40)
    private String source;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    private String createdBy;

    @PrePersist
    void createTimestamp() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
