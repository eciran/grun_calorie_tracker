package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FoodBrandStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "food_brands")
public class FoodBrandEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 255)
    private String canonicalName;
    @Column(nullable = false, length = 255)
    private String normalizedKey;
    private String manufacturerName;
    @Column(length = 2)
    private String countryCode;
    @Column(length = 1000)
    private String logoUrl;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FoodBrandStatus status = FoodBrandStatus.ACTIVE;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merged_into_id")
    private FoodBrandEntity mergedInto;
    @Column(length = 40)
    private String source;
    @Column(nullable = false)
    private boolean verified;
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
