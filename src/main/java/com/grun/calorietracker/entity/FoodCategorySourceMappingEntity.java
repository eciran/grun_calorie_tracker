package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FoodCategoryMappingStatus;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.MarketRegion;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "food_category_source_mappings")
public class FoodCategorySourceMappingEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FoodDataSource dataSource;
    @Column(nullable = false, length = 180)
    private String sourceTag;
    @Column(nullable = false, length = 180)
    private String normalizedSourceTag;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MarketRegion marketRegion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private FoodCategoryEntity category;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FoodCategoryMappingStatus status = FoodCategoryMappingStatus.REVIEW_REQUIRED;
    private Integer confidenceScore;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    @PrePersist
    void createTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}
