package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FoodCategoryAssignmentSource;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@IdClass(FoodItemCategoryId.class)
@Table(name = "food_item_categories")
public class FoodItemCategoryEntity {
    @Id @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_item_id", nullable = false)
    private FoodItemEntity foodItem;
    @Id @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private FoodCategoryEntity category;
    @Column(nullable = false)
    private boolean primaryCategory;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FoodCategoryAssignmentSource assignmentSource;
    private Integer confidenceScore;
    @Column(nullable = false)
    private boolean reviewed;
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
