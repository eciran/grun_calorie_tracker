package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "food_categories")
public class FoodCategoryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private FoodCategoryEntity parent;
    @Column(nullable = false, length = 160)
    private String slug;
    @Column(nullable = false)
    private String nameEn;
    @Column(nullable = false)
    private String nameTr;
    @Column(length = 1000)
    private String descriptionEn;
    @Column(length = 1000)
    private String descriptionTr;
    @Column(length = 120)
    private String iconKey;
    @Column(length = 1000)
    private String imageUrl;
    @Column(nullable = false)
    private int sortOrder;
    @Column(nullable = false)
    private boolean active = true;
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
